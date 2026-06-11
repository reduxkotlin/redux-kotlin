package org.reduxkotlin.compose.saveable

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.serialization.Serializable
import org.reduxkotlin.Store
import org.reduxkotlin.compose.fieldState
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the restore-safe effect pattern: a restore dispatches exactly ONE action (the saver's
 * restore action) and replays none of the events that originally produced the saved state. A data
 * load keyed on a navigation EVENT therefore never runs on the restore path; a load keyed on the
 * restored STATE fires for a real navigation and for a restore alike (and for any other
 * state-only entry point — deep links, DevTools time-travel, replay).
 */
@OptIn(ExperimentalTestApi::class)
class RestoreRetriggersEffectsTest {

    private data class NavState(val route: String = "list", val detail: String? = null)

    private data class Navigate(val route: String)

    private data class RestoreNav(val route: String)

    private data class LoadDetailSucceeded(val detail: String)

    @Serializable
    private data class NavSnapshot(val route: String)

    private val navReducer: (NavState, Any) -> NavState = { state, action ->
        when (action) {
            is Navigate -> state.copy(route = action.route)
            is RestoreNav -> state.copy(route = action.route)
            is LoadDetailSucceeded -> state.copy(detail = action.detail)
            else -> state
        }
    }

    private val navSaver: StateSaver<NavState, NavSnapshot> = StateSaver(
        serializer = NavSnapshot.serializer(),
        save = { state -> NavSnapshot(state.route) },
        restore = { snapshot -> RestoreNav(snapshot.route) },
    )

    @Test
    fun restoredRoute_firesStateKeyedLoadEffect() = runComposeUiTest {
        // Session 1: the user navigated to the detail route; the platform saves the snapshot.
        val store1: Store<NavState> = createStore(navReducer, NavState())
        val registry1 = SaveableStateRegistry(restoredValues = null) { true }
        wireSaveable(store1, registry1, "nav", navSaver)
        store1.dispatch(Navigate("detail"))
        val saved = registry1.performSave()

        // Session 2 (process death): a FRESH store + a registry primed with the saved snapshot.
        // The only action that will run is the saver's RestoreNav — Navigate is never replayed.
        val store2: Store<NavState> = createStore(navReducer, NavState())
        val registry2 = SaveableStateRegistry(restoredValues = saved) { true }
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry2) {
                store2.rememberSaveableState(navSaver, key = "nav")
                val route by store2.fieldState(NavState::route)
                // The load keys on STATE (the restored route), not on the Navigate event. Restore
                // is applied synchronously during composition, so this effect's first key
                // evaluation already sees "detail" and the load fires.
                DisposableEffect(route) {
                    if (route == "detail") store2.dispatch(LoadDetailSucceeded("detail-data"))
                    onDispose { }
                }
                BasicText("detail=${store2.fieldState(NavState::detail).value}")
            }
        }

        waitForIdle()
        onAllNodesWithText("detail=detail-data").assertCountEquals(1)
        assertEquals(NavState(route = "detail", detail = "detail-data"), store2.state)
    }
}
