package org.reduxkotlin.compose.saveable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.compose.fieldState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SaveableIntegrationTest {

    /**
     * Verifies that [rememberSaveableState] wires into the enclosing
     * [SaveableStateRegistry]: after a click mutates the store and a manual
     * save+restore cycle runs, a fresh store rehydrated from the snapshot
     * reflects the post-click value.
     *
     * [StateRestorationTester.emulateSaveAndRestore] is not implemented on
     * the desktop/JVM Compose target (it throws [NotImplementedError]) so
     * this test exercises the same registry round-trip directly via
     * [SaveableStateRegistry] and [LocalSaveableStateRegistry], which is the
     * exact mechanism the composable delegates to.
     */
    @Test
    fun child_binding_shows_restored_value_after_state_restoration() = runComposeUiTest {
        // Phase 1: set up a registry, mount the composable, click to mutate state.
        val registry1 = SaveableStateRegistry(restoredValues = null) { true }

        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry1) {
                val store = remember { newTestStore(TestState()) }
                store.rememberSaveableState(testSaver, key = "root")
                Column {
                    BasicText(
                        text = store.fieldState(TestState::tab).value.toString(),
                        modifier = Modifier.testTag("tab"),
                    )
                    BasicText(
                        text = "set",
                        modifier = Modifier
                            .testTag("set")
                            .clickable { store.dispatch(SetTab(7)) },
                    )
                }
            }
        }

        onNodeWithTag("tab").assertTextEquals("0")
        onNodeWithTag("set").performClick()
        onNodeWithTag("tab").assertTextEquals("7")

        // Phase 2: capture the saved values from the registry.
        val saved = registry1.performSave()

        // Phase 3: new registry seeded with the saved values + new store (process death).
        // A fresh store starts at tab=0; only the anchor can produce tab=7.
        val registry2 = SaveableStateRegistry(restoredValues = saved) { true }
        var freshStore: org.reduxkotlin.Store<TestState>? = null
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry2) {
                val store = remember { newTestStore(TestState()).also { freshStore = it } }
                store.rememberSaveableState(testSaver, key = "root")
                Column {
                    BasicText(
                        text = store.fieldState(TestState::tab).value.toString(),
                        modifier = Modifier.testTag("tab2"),
                    )
                }
            }
        }

        // The button was not clicked again; only rehydration can produce "7".
        onNodeWithTag("tab2").assertTextEquals("7")
    }
}
