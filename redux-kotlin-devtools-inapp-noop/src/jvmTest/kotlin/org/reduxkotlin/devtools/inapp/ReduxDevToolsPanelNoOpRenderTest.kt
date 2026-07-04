package org.reduxkotlin.devtools.inapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Guards the release no-op panel: it must render NOTHING (no inspector tabs) and never crash, so a
 * release build embedding `ReduxDevToolsPanel` shows only the surrounding app UI.
 */
class ReduxDevToolsPanelNoOpRenderTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noop_panel_is_inert() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize()) {
                BasicText("app content")
                ReduxDevToolsPanel(instanceId = "x")
            }
        }
        onNodeWithText("app content").assertIsDisplayed()
        // No inspector chrome in release.
        onNodeWithText("ACTIONS").assertDoesNotExist()
    }
}
