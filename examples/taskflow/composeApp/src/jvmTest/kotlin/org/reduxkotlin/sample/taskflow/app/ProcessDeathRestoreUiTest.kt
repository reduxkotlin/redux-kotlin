package org.reduxkotlin.sample.taskflow.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import coil3.compose.setSingletonImageLoaderFactory
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.compose.saveable.rememberSaveableState
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.app.persistence.RouteDto
import org.reduxkotlin.sample.taskflow.app.persistence.UiSnapshot
import org.reduxkotlin.sample.taskflow.app.persistence.accountUiSaver
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.feature.account.ProfileScreen
import org.reduxkotlin.sample.taskflow.feature.board.BoardClosed
import org.reduxkotlin.sample.taskflow.feature.board.BoardScreen
import org.reduxkotlin.sample.taskflow.feature.board.CardDetailScreen
import org.reduxkotlin.sample.taskflow.feature.board.LoadBoardRequested
import org.reduxkotlin.sample.taskflow.feature.boardlist.BoardListScreen
import org.reduxkotlin.sample.taskflow.feature.settings.SettingsScreen
import org.reduxkotlin.sample.taskflow.infra.SeedData
import org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.infra.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.infra.db.taskFlowDb
import org.reduxkotlin.sample.taskflow.ui.image.fakeNoNetworkImageLoader
import org.reduxkotlin.sample.taskflow.ui.theme.TaskFlowTheme
import kotlin.test.assertEquals

/**
 * Compose-level regression proof for the app-death restore flow: the platform restores the saveable snapshot
 * (nav stack `[BoardList, Board, ComposeCard]`), the ActiveAccount-shaped harness applies it via
 * [rememberSaveableState], the board-lifecycle effect dispatches [LoadBoardRequested], and the
 * board layer (beneath the add-card overlay) must eventually render the persisted cards.
 *
 * The harness mirrors `ActiveAccount` + `BoardLifecycleEffect` + `RouteScreen` from App.kt
 * (those are private), minus AdaptiveNav chrome and the predictive-back animation.
 */
@OptIn(ExperimentalTestApi::class)
class ProcessDeathRestoreUiTest {

    private val annId = AccountId("ann")
    private val annBoardId = BoardId("ann-board")

    private fun newLocal(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return SqlDelightLocalStore(taskFlowDb(driver))
    }

    /** The persisted snapshot a previous session saved while the user was on the add-card screen. */
    private fun savedSnapshotJson(): String = accountUiSaver.json.encodeToString(
        accountUiSaver.serializer,
        UiSnapshot(
            stack = listOf(RouteDto.BoardList, RouteDto.Board("ann-board"), RouteDto.ComposeCard("ann-todo")),
            filterQuery = "",
            filterAssignee = null,
            filterLabelIds = emptyList(),
        ),
    )

    @Test
    fun restoredAddCardStack_boardLayerShowsCards() = runComposeUiTest {
        val local = newLocal()
        runBlocking { local.ensureSeeded() }
        val rootStore = createAppStore(NotificationContext.Inline)
        val registry = AccountRegistry(rootStore, local, NotificationContext.Inline)

        // A SaveableStateRegistry primed with the previous session's snapshot — what the Android
        // host provides after process death.
        val restoredRegistry = SaveableStateRegistry(
            restoredValues = mapOf("account-ui-ann" to listOf(savedSnapshotJson())),
            canBeSaved = { true },
        )

        setContent {
            setSingletonImageLoaderFactory { ctx -> fakeNoNetworkImageLoader(ctx) }
            TaskFlowTheme {
                CompositionLocalProvider(LocalSaveableStateRegistry provides restoredRegistry) {
                    ActiveAccountReplica(registry = registry, activeId = annId)
                }
            }
        }

        // The full stack restored: the nav stack is the saved one...
        waitForIdle()
        val store = registry.store(annId)!!
        assertEquals(
            persistentListOf(
                Route.BoardList,
                Route.Board(annBoardId),
                Route.ComposeCard(ColumnId("ann-todo")),
            ),
            store.state.get<NavModel>().stack,
            "nav stack must restore [BoardList, Board, ComposeCard]",
        )

        // ...and the board layer (beneath the add-card overlay) shows the persisted cards.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Design the API surface").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Mirror of App.kt's private `ActiveAccount` (minus switcher/back-handler chrome). */
    @Composable
    private fun ActiveAccountReplica(registry: AccountRegistry, activeId: AccountId) {
        val handle = remember(activeId) { registry.getOrCreate(activeId, SeedData.accountDetail(activeId)) }
        val accountStore = handle.store

        accountStore.rememberSaveableState(accountUiSaver, key = "account-ui-${activeId.v}")

        val nav by rememberStableStore(accountStore).value.fieldStateOf(NavModel::class) { it }

        BoardLifecycleEffectReplica(
            accountStore = accountStore,
            registry = registry,
            activeId = activeId,
            activeBoardId = nav.activeBoardId,
        )

        RoutingReplica(accountStore = accountStore, nav = nav)
    }

    /** Mirror of App.kt's private `BoardLifecycleEffect` (bot kept on for fidelity). */
    @Composable
    private fun BoardLifecycleEffectReplica(
        accountStore: Store<ModelState>,
        registry: AccountRegistry,
        activeId: AccountId,
        activeBoardId: BoardId?,
    ) {
        DisposableEffect(activeId, activeBoardId) {
            if (activeBoardId != null) {
                accountStore.dispatch(LoadBoardRequested(activeBoardId))
                registry.startBot(activeId, rngSeed = 0L)
            }
            onDispose {
                if (activeBoardId != null) {
                    accountStore.dispatch(BoardClosed)
                    registry.stopBot(activeId)
                }
            }
        }
    }

    /** Mirror of App.kt's private routing: render every stack layer bottom-to-top. */
    @Composable
    private fun RoutingReplica(accountStore: Store<ModelState>, nav: NavModel) {
        Box(modifier = Modifier.fillMaxSize()) {
            nav.stack.forEach { route ->
                key(route) {
                    when (route) {
                        is Route.BoardList -> Surface(modifier = Modifier.fillMaxSize()) {
                            BoardListScreen(accountStore)
                        }

                        is Route.Board -> Surface(modifier = Modifier.fillMaxSize()) {
                            BoardScreen(accountStore)
                        }

                        Route.Profile -> Surface(modifier = Modifier.fillMaxSize()) {
                            ProfileScreen(accountStore, accountStore)
                        }

                        Route.Settings -> Surface(modifier = Modifier.fillMaxSize()) {
                            SettingsScreen(accountStore)
                        }

                        is Route.CardDetail, is Route.ComposeCard -> CardDetailScreen(accountStore)
                    }
                }
            }
        }
    }
}
