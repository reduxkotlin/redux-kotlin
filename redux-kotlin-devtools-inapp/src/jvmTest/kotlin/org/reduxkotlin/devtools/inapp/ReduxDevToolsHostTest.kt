package org.reduxkotlin.devtools.inapp

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
}
