package org.reduxkotlin.devtools.inapp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.ui.DevToolsTab
import kotlin.test.AfterTest
import kotlin.test.Test

class ReduxDevToolsPanelTest {

    @AfterTest fun cleanup() {
        DevToolsHub.reset()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun panel_renders_inspector_tabs_without_drawer_chrome() = runComposeUiTest {
        DevToolsHub.createSession(DevToolsConfig(name = "panel-store"))
        setContent { ReduxDevToolsPanel(instanceId = "panel-store") }
        waitForIdle()
        // The inspector tab strip is shown...
        onNodeWithText("ACTIONS").assertIsDisplayed()
        onNodeWithText("STATE").assertIsDisplayed()
        // ...but NOT the host drawer's header (the panel omits chrome).
        onNodeWithText("Redux DevTools").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun panel_honors_startTab() = runComposeUiTest {
        DevToolsHub.createSession(DevToolsConfig(name = "panel-store"))
        setContent { ReduxDevToolsPanel(instanceId = "panel-store", startTab = DevToolsTab.OUTPUTS) }
        waitForIdle()
        // Outputs tab content (the always-present in-app sink row), not just the tab label.
        onNodeWithText("In-app drawer").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun multi_session_panel_renders_the_inspector() = runComposeUiTest {
        DevToolsHub.createSession(DevToolsConfig(name = "a"))
        DevToolsHub.createSession(DevToolsConfig(name = "b"))
        setContent { ReduxDevToolsPanel() } // instanceId = null → multi-session + store picker
        waitForIdle()
        onNodeWithText("ACTIONS").assertIsDisplayed()
    }
}
