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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.compose.fieldState
import kotlin.test.Test
import kotlin.test.assertEquals

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
        // Also assert the store itself rehydrated — proves dispatch happened, not just render.
        assertEquals(TestState(tab = 7, query = ""), freshStore!!.state)
    }

    /**
     * Proves the restore is applied SYNCHRONOUSLY during composition: a child
     * reading `store.state` during its first composition already sees the
     * rehydrated value. This only holds if restore runs in a `remember` block;
     * with a post-composition [androidx.compose.runtime.DisposableEffect] the
     * first read would still observe the default state (a stale first frame).
     */
    @Test
    fun restoreIsAppliedSynchronouslyBeforeFirstChildRead() = runComposeUiTest {
        // Phase 1: produce a saved snapshot from a source store.
        val source = newTestStore(TestState(tab = 7, query = "z"))
        val reg1 = SaveableStateRegistry(restoredValues = null) { true }
        wireSaveable(source, reg1, "k", testSaver)
        val saved = reg1.performSave()

        // Phase 2: fresh store + a registry pre-seeded with the saved values (simulates restore).
        val fresh = newTestStore(TestState())
        val reg2 = SaveableStateRegistry(restoredValues = saved) { true }

        var firstChildObservedTab: Int? = null
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides reg2) {
                fresh.rememberSaveableState(testSaver, key = "k")
                // Child reads store state during ITS first composition. With a synchronous restore the
                // value is already rehydrated; with a post-composition effect it would still be default.
                if (firstChildObservedTab == null) firstChildObservedTab = fresh.state.tab
                BasicText("tab=${fresh.state.tab}")
            }
        }
        waitForIdle()
        assertEquals(7, firstChildObservedTab) // restore happened during composition, not after
        onAllNodesWithText("tab=7").assertCountEquals(1)
    }
}
