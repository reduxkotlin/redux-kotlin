package org.reduxkotlin.devtools.inapp

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsHub
import kotlin.test.AfterTest
import kotlin.test.Test

class MultiStoreDrawerTest {

    @AfterTest fun cleanup() {
        DevToolsHub.reset()
        ReduxDevTools.close()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun two_stores_are_reachable_in_the_drawer() = runComposeUiTest {
        DevToolsHub.createSession(DevToolsConfig(name = "StoreA"))
        DevToolsHub.createSession(DevToolsConfig(name = "StoreB"))
        setContent { ReduxDevToolsHost(InAppConfig()) { Text("app") } }
        ReduxDevTools.open()
        waitForIdle()
        // Open the store picker dropdown (click the picker label showing active store or "All stores").
        onNodeWithText("StoreA", substring = true).performClick()
        waitForIdle()
        onNodeWithText("All stores").assertIsDisplayed()
    }
}
