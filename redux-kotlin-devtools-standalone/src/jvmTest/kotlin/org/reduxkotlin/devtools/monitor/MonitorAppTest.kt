package org.reduxkotlin.devtools.monitor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.monitor.ui.MonitorApp
import kotlin.test.Test

class MonitorAppTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renders_the_dock_with_a_seeded_store() = runComposeUiTest {
        val ingest = MonitorIngest()
        ingest.openConnection().apply {
            accept(
                BridgeMessage.Hello(
                    PROTOCOL_VERSION,
                    "tf",
                    "TaskFlow",
                    "TaskFlow-root",
                    "TaskFlow-root",
                    "toString",
                    null,
                ),
            )
            accept(
                BridgeMessage.Action(
                    1,
                    buildJsonObject { put("type", "AddCard") },
                    buildJsonObject { put("n", 1) },
                    emptyList(),
                    10L,
                    false,
                ),
            )
        }
        setContent {
            val state = rememberMonitorState(ingest)
            MonitorApp(ingest, state)
        }
        waitForIdle()
        onNodeWithText("AddCard", substring = true).assertIsDisplayed()
        // "TaskFlow-root" appears in multiple nodes (WinBar title, TopBar picker, StoreRail row);
        // assert that at least one of them is displayed.
        onAllNodesWithText("TaskFlow-root", substring = true)[0].assertIsDisplayed()
    }
}
