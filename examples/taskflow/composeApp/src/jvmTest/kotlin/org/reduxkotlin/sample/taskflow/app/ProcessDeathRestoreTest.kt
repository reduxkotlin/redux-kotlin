package org.reduxkotlin.sample.taskflow.app

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.sample.taskflow.app.persistence.accountUiSaver
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.feature.board.BoardModel
import org.reduxkotlin.sample.taskflow.feature.board.LoadBoardRequested
import org.reduxkotlin.sample.taskflow.infra.SeedData
import org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.infra.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.infra.db.taskFlowDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Store-level regression proof for the app-death restore flow: the [accountUiSaver] snapshot
 * (saved while the user was on the add-card screen) restores into a FRESH account store
 * (simulating process death), the BoardLifecycleEffect-equivalent [LoadBoardRequested] runs, and
 * the board model comes back populated with the persisted cards.
 *
 * Harness note: the effects scope must be a FOREGROUND test scope —
 * `TestScope.backgroundScope` tasks are skipped by [advanceUntilIdle] (it only drains until no
 * foreground tasks remain), so effects launched there would never run under virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProcessDeathRestoreTest {

    private val annId = AccountId("ann")
    private val annBoardId = BoardId("ann-board")
    private val todo = ColumnId("ann-todo")

    private fun newLocal(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return SqlDelightLocalStore(taskFlowDb(driver))
    }

    @Test
    fun control_freshStore_loadBoardRequested_populatesBoard() = runTest {
        val local = newLocal()
        local.ensureSeeded()
        val rootStore = createAppStore(NotificationContext.Inline)
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val session = createAccountStore(
            detail = SeedData.accountDetail(annId),
            rootStore = rootStore,
            localStore = local,
            notificationContext = NotificationContext.Inline,
            scope = scope,
        )
        session.store.dispatch(
            org.reduxkotlin.sample.taskflow.app.nav.Navigate(Route.Board(annBoardId)),
        )
        session.store.dispatch(LoadBoardRequested(annBoardId))
        advanceUntilIdle()
        assertNotNull(session.store.state.get<BoardModel>().board, "control: fresh store must load the board")
        scope.cancel()
    }

    @Test
    fun restoreIntoAddCardStack_thenLifecycleLoad_populatesBoard() = runTest {
        val local = newLocal()
        local.ensureSeeded()
        val rootStore = createAppStore(NotificationContext.Inline)

        // --- session 1: user is on the add-card screen; platform saves the UI snapshot ---
        val scope1 = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val session1 = createAccountStore(
            detail = SeedData.accountDetail(annId),
            rootStore = rootStore,
            localStore = local,
            notificationContext = NotificationContext.Inline,
            scope = scope1,
        )
        session1.store.dispatch(
            org.reduxkotlin.sample.taskflow.app.nav.Navigate(Route.Board(annBoardId)),
        )
        session1.store.dispatch(
            org.reduxkotlin.sample.taskflow.app.nav.Navigate(Route.ComposeCard(todo)),
        )
        advanceUntilIdle()
        val encoded = accountUiSaver.json.encodeToString(
            accountUiSaver.serializer,
            accountUiSaver.save(session1.store.state),
        )

        // --- process death: session 1 fully torn down (like a killed process), same durable DB ---
        session1.bridgeOutput?.stop()
        session1.devtoolsId?.let { DevToolsHub.removeSession(it) }
        scope1.cancel()
        advanceUntilIdle()

        val scope2 = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val session2 = createAccountStore(
            detail = SeedData.accountDetail(annId),
            rootStore = rootStore,
            localStore = local,
            notificationContext = NotificationContext.Inline,
            scope = scope2,
        )
        val store = session2.store

        // rememberSaveableState's applyRestore: decode + dispatch the restore action.
        val snapshot = accountUiSaver.json.decodeFromString(accountUiSaver.serializer, encoded)
        store.dispatch(accountUiSaver.restore(snapshot))

        val nav = store.state.get<NavModel>()
        assertEquals(
            persistentListOf(Route.BoardList, Route.Board(annBoardId), Route.ComposeCard(todo)),
            nav.stack,
            "nav stack must restore [BoardList, Board, ComposeCard]",
        )

        // BoardLifecycleEffect equivalent: activeBoardId is non-null -> load the board.
        store.dispatch(LoadBoardRequested(annBoardId))
        advanceUntilIdle()

        val board = store.state.get<BoardModel>().board
        assertNotNull(board, "board must be loaded after restore + LoadBoardRequested")
        assertTrue(board.cards.isNotEmpty(), "restored board must contain the persisted cards")
        scope2.cancel()
    }
}
