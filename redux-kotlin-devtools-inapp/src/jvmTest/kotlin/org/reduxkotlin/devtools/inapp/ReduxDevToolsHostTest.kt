package org.reduxkotlin.devtools.inapp

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsHub
import kotlin.test.AfterTest
import kotlin.test.Test

class ReduxDevToolsHostTest {

    @AfterTest fun cleanup() {
        DevToolsHub.reset()
        ReduxDevTools.close()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun host_renders_app_content_and_opens_drawer_programmatically() = runComposeUiTest {
        // A session must exist for the overlay to appear.
        DevToolsHub.createSession(DevToolsConfig(name = "ui"))

        setContent {
            ReduxDevToolsHost(InAppConfig()) { Text("app content") }
        }
        onNodeWithText("app content").assertIsDisplayed()

        ReduxDevTools.open()
        waitForIdle()
        onNodeWithText("Redux DevTools").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun startTab_config_selects_the_initial_tab() = runComposeUiTest {
        DevToolsHub.createSession(DevToolsConfig(name = "ui"))

        setContent {
            ReduxDevToolsHost(InAppConfig(startTab = DevToolsTab.OUTPUTS)) { Text("app") }
        }
        ReduxDevTools.open()
        waitForIdle()
        // The Outputs tab content (not just the tab strip label) is showing.
        onNodeWithText("In-app drawer").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun single_session_path_resolves_a_session_registered_after_first_composition() = runComposeUiTest {
        setContent {
            ReduxDevToolsHost(InAppConfig(instanceId = "late-store")) { Text("app content") }
        }
        ReduxDevTools.open()
        waitForIdle()
        // No session yet: only the app content, no drawer.
        onNodeWithText("Redux DevTools").assertDoesNotExist()

        DevToolsHub.createSession(DevToolsConfig(name = "late-store"))
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Redux DevTools").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Redux DevTools").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun drawer_honors_the_sessions_maxAge_not_the_model_default() = runComposeUiTest {
        val session = DevToolsHub.createSession(DevToolsConfig(name = "ring", maxAge = 2))
        setContent { ReduxDevToolsHost(InAppConfig()) { Text("app") } }
        ReduxDevTools.open()
        waitForIdle()

        session.record(mapOf("type" to "First"), mapOf("n" to 1))
        session.record(mapOf("type" to "Second"), mapOf("n" to 2))
        session.record(mapOf("type" to "Third"), mapOf("n" to 3))

        // Once the newest action is rendered, the log must hold only maxAge (2) actions.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Third", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("2 actions", substring = true).assertIsDisplayed()
        onNodeWithText("First", substring = true).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun controller_follows_a_recreated_session_with_the_same_id() = runComposeUiTest {
        DevToolsHub.createSession(DevToolsConfig(name = "phoenix"))
        setContent { ReduxDevToolsHost(InAppConfig()) { Text("app") } }
        ReduxDevTools.open()
        waitForIdle()

        // Hub reset + recreate under the same id (no frame in between, so the composition sees the
        // id survive): the controller must rebind to the NEW session instance, not the dead one.
        DevToolsHub.reset()
        val fresh = DevToolsHub.createSession(DevToolsConfig(name = "phoenix"))
        fresh.record(mapOf("type" to "FreshAction"), mapOf("n" to 1))

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("FreshAction", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("FreshAction", substring = true).assertIsDisplayed()
    }
}
