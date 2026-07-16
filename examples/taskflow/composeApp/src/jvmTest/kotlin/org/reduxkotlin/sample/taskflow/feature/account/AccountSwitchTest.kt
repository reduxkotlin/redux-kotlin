package org.reduxkotlin.sample.taskflow.feature.account

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import coil3.compose.setSingletonImageLoaderFactory
import org.junit.Test
import org.reduxkotlin.compose.SelectorStore
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberSelectorStore
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.app.AccountRegistry
import org.reduxkotlin.sample.taskflow.app.createAppStore
import org.reduxkotlin.sample.taskflow.app.nav.Navigate
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.infra.SeedData
import org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.infra.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.infra.db.taskFlowDb
import org.reduxkotlin.sample.taskflow.ui.image.fakeNoNetworkImageLoader

/**
 * Account-switch restore proof (design §10, plan Task 35): switching the *active account* swaps
 * which per-account store the UI binds to, and each account's screen comes back EXACTLY as it was
 * last left — Nav state is isolated per account and is *remembered*, never reset on switch.
 *
 * The harness binds `store = rememberSelectorStore(registry.store(activeId))` and renders a tag
 * of `store.fieldStateOf(NavModel::class){ it.current::class.simpleName }`. A test-controlled
 * `mutableStateOf` for the active account drives the switch. Each account's store was independently
 * navigated (A → Settings, B → Profile) before the first frame; the test then drives A → B → A and
 * asserts the rendered route follows the per-account store and that returning to A restores its
 * Settings screen (proving the per-account Nav was remembered, not reset).
 *
 * Stores use `NotificationContext.Inline` (deterministic, no main-thread marshalling) over an
 * in-memory SQLite [LocalStore], and a network-free [fakeNoNetworkImageLoader] so any AsyncImage in
 * a screen surfaces its fallback instead of hitting the network.
 */
@OptIn(ExperimentalTestApi::class)
class AccountSwitchTest {

    private val annId = AccountId("ann")
    private val rajId = AccountId("raj")

    private fun newLocal(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return SqlDelightLocalStore(taskFlowDb(driver))
    }

    private fun newRegistry(): AccountRegistry {
        // Inline notification context: deterministic dispatch, no off-main marshalling under runComposeUiTest.
        val rootStore = createAppStore(NotificationContext.Inline)
        return AccountRegistry(rootStore, newLocal(), NotificationContext.Inline)
    }

    @Test
    fun switchingActiveAccountRestoresEachAccountsRememberedScreen() = runComposeUiTest {
        val registry = newRegistry()
        // Two live, fully isolated per-account stores.
        registry.getOrCreate(annId, SeedData.accountDetail(annId))
        registry.getOrCreate(rajId, SeedData.accountDetail(rajId))

        // Navigate each account independently: A → Settings, B → Profile.
        registry.store(annId)!!.dispatch(Navigate(Route.Settings))
        registry.store(rajId)!!.dispatch(Navigate(Route.Profile))

        // Test-controlled active account: flipping this re-binds the harness to a different store.
        val active = mutableStateOf(annId)

        setContent {
            // No-network ImageLoader so any AsyncImage in a screen falls back instead of hitting the net.
            setSingletonImageLoaderFactory { ctx -> fakeNoNetworkImageLoader(ctx) }
            val activeId = remember { active }.value
            val store = rememberSelectorStore(registry.store(activeId)!!)
            RouteTag(store)
        }

        val settingsTag = "route:" + Route.Settings::class.simpleName
        val profileTag = "route:" + Route.Profile::class.simpleName

        // Active = A → A's remembered screen is Settings.
        active.value = annId
        waitForIdle()
        onAllNodesWithText(settingsTag).assertCountEquals(1)

        // Switch active = B → B's remembered screen is Profile (isolated from A).
        active.value = rajId
        waitForIdle()
        onAllNodesWithText(profileTag).assertCountEquals(1)
        // A's Settings tag is gone — the bound store really swapped to B.
        onAllNodesWithText(settingsTag).assertCountEquals(0)

        // Switch back to A → A's screen is STILL Settings (remembered across the switch, NOT reset).
        active.value = annId
        waitForIdle()
        onAllNodesWithText(settingsTag).assertCountEquals(1)
        onAllNodesWithText(profileTag).assertCountEquals(0)
    }
}

/**
 * Renders the active account store's current route name as a single text node the test inspects.
 * Binds ONLY the [NavModel.route]'s leaf class name via `fieldStateOf`, so the rendered tag reflects
 * exactly which screen the currently-bound account store is on.
 *
 * @param store the active account's store the harness re-binds on every account switch.
 */
@Composable
private fun RouteTag(store: SelectorStore<ModelState>) {
    val routeName: String by store.fieldStateOf(NavModel::class) { it.current::class.simpleName ?: "?" }
    Text(text = "route:$routeName")
}
