package org.reduxkotlin.sample.taskflow.app.persistence

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.sample.taskflow.app.createAccountStore
import org.reduxkotlin.sample.taskflow.app.createAppStore
import org.reduxkotlin.sample.taskflow.app.getModel
import org.reduxkotlin.sample.taskflow.app.nav.Navigate
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel
import org.reduxkotlin.sample.taskflow.feature.board.SetFilterQuery
import org.reduxkotlin.sample.taskflow.infra.SeedData
import org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.infra.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.infra.db.taskFlowDb
import kotlin.test.Test
import kotlin.test.assertEquals

class UiSnapshotStoreTest {

    private val annId = AccountId("ann")

    private fun newLocal(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return SqlDelightLocalStore(taskFlowDb(driver))
    }

    // Mirrors AccountRegistryTest's construction (test rootStore + in-memory LocalStore + inline
    // notification), but builds the per-account store directly so the test can drive it and snapshot
    // its live NavModel/FilterModel under virtual time.
    private fun newAccountStore(scope: CoroutineScope) = createAccountStore(
        detail = SeedData.accountDetail(annId),
        rootStore = createAppStore(NotificationContext.Inline),
        localStore = newLocal(),
        notificationContext = NotificationContext.Inline,
        scope = scope,
    ).store

    @Test
    fun snapshot_round_trips_nav_and_filter_through_a_live_store() = runTest {
        // Account stores launch long-lived sync/effect collectors; run them on backgroundScope so
        // runTest auto-cancels them at body end (they never complete on their own).
        val store = newAccountStore(backgroundScope)

        store.dispatch(Navigate(Route.Board(BoardId("b1"))))
        store.dispatch(SetFilterQuery("hello"))

        val json = encodeUiSnapshot(store)

        val fresh = newAccountStore(backgroundScope)
        fresh.dispatch(decodeUiSnapshot(json))

        assertEquals(BoardId("b1"), fresh.getModel<NavModel>().activeBoardId)
        assertEquals("hello", fresh.getModel<FilterModel>().query)
    }
}
