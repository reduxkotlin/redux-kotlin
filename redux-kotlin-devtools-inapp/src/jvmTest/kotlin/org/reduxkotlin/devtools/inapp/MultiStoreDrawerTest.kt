package org.reduxkotlin.devtools.inapp

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun merged_all_stores_shows_actions_from_more_than_one_store() = runComposeUiTest {
        val a = DevToolsHub.createSession(DevToolsConfig(name = "StoreA"))
        val b = DevToolsHub.createSession(DevToolsConfig(name = "StoreB"))
        // Record one distinct action per store; the merged log must list BOTH (the bug: only one showed).
        a.record(mapOf("type" to "AlphaAction"), mapOf("n" to 1))
        b.record(mapOf("type" to "BetaAction"), mapOf("n" to 1))

        setContent { ReduxDevToolsHost(InAppConfig()) { Text("app") } }
        ReduxDevTools.open()
        waitForIdle()
        // Switch to the merged "All stores" view.
        onNodeWithText("StoreA", substring = true).performClick()
        waitForIdle()
        onNodeWithText("All stores").performClick()

        // Both stores' actions are rendered in the merged log (poll: actions arrive via a bg coroutine).
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("AlphaAction", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodesWithText("BetaAction", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("AlphaAction", substring = true).assertIsDisplayed()
        onNodeWithText("BetaAction", substring = true).assertIsDisplayed()
    }
}
