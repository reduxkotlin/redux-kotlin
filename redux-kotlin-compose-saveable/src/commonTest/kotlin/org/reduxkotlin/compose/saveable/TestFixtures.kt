package org.reduxkotlin.compose.saveable

import kotlinx.serialization.Serializable
import org.reduxkotlin.Store
import org.reduxkotlin.createStore

internal data class TestState(val tab: Int = 0, val query: String = "")

internal data class SetTab(val tab: Int)

internal data class RehydrateUi(val tab: Int, val query: String)

@Serializable
internal data class UiSnapshot(val tab: Int, val query: String)

internal val testReducer: (TestState, Any) -> TestState = { state, action ->
    when (action) {
        is SetTab -> state.copy(tab = action.tab)
        is RehydrateUi -> state.copy(tab = action.tab, query = action.query)
        else -> state
    }
}

internal fun newTestStore(initial: TestState = TestState()): Store<TestState> =
    createStore(testReducer, initial)

internal val testSaver: StateSaver<TestState, UiSnapshot> = StateSaver(
    serializer = UiSnapshot.serializer(),
    save = { state -> UiSnapshot(state.tab, state.query) },
    restore = { snapshot -> RehydrateUi(snapshot.tab, snapshot.query) },
)
