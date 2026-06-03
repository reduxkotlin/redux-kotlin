package org.reduxkotlin.sample.taskflow.app

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.isActive
import org.reduxkotlin.Store
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.app.nav.Navigate
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.infra.SeedData
import org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.infra.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.infra.db.taskFlowDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountRegistryTest {

    private val annId = AccountId("ann")
    private val rajId = AccountId("raj")

    private fun newLocal(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return SqlDelightLocalStore(taskFlowDb(driver))
    }

    private fun newRegistry(local: LocalStore): AccountRegistry {
        // Inline notification: deterministic, no main-thread marshalling.
        val rootStore = createAppStore(NotificationContext.Inline)
        return AccountRegistry(rootStore, local, NotificationContext.Inline)
    }

    private fun Store<ModelState>.route(): Route = state.getModel<NavModel>().current

    @Test
    fun accounts_are_isolated_and_removable() {
        val registry = newRegistry(newLocal())

        val annHandle = registry.getOrCreate(annId, SeedData.accountDetail(annId))
        registry.getOrCreate(rajId, SeedData.accountDetail(rajId))

        // Default nav route is BoardList for a fresh account store.
        assertEquals(Route.BoardList, registry.get(annId)!!.route())
        assertEquals(Route.BoardList, registry.get(rajId)!!.route())

        // Dispatch Navigate(Settings) on A only.
        registry.get(annId)!!.dispatch(Navigate(Route.Settings))

        // A moved to Settings; B is untouched (state isolation across per-account stores).
        assertEquals(Route.Settings, registry.get(annId)!!.route(), "A's nav should advance to Settings")
        assertEquals(Route.BoardList, registry.get(rajId)!!.route(), "B's nav must stay BoardList (isolated)")

        // Remove A: forgotten from the registry, B still present.
        registry.remove(annId)
        assertNull(registry.get(annId), "removed account A must no longer resolve")
        assertNotNull(registry.get(rajId), "account B must remain registered")

        // A's coroutine scope was cancelled by remove() (tears down its effect/sync/bot coroutines).
        assertFalse(annHandle.scope.isActive, "removed account A's scope must be cancelled")
    }
}
