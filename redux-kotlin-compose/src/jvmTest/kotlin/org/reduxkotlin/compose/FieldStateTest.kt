package org.reduxkotlin.compose

import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.reduxkotlin.Store
import org.reduxkotlin.createStore

private data class CounterState(val counter: Int = 0, val label: String = "init")
private data class Increment(val amount: Int = 1)
private data class SetLabel(val text: String)

private fun reducer(state: CounterState, action: Any): CounterState = when (action) {
    is Increment -> state.copy(counter = state.counter + action.amount)
    is SetLabel -> state.copy(label = action.text)
    else -> state
}

private fun newStore(): Store<CounterState> = createStore(::reducer, CounterState())

// Models a concurrent store: state writes apply synchronously, but
// subscriber notifications are deferred (posted, drained later).
private class DeferredNotifyStore<S>(initial: S) : org.reduxkotlin.Store<S> {
    private var current: S = initial
    private val listeners = mutableListOf<org.reduxkotlin.StoreSubscriber>()
    private val pending = mutableListOf<() -> Unit>()

    fun drain() {
        val copy = pending.toList()
        pending.clear()
        copy.forEach { it() }
    }

    override val store: org.reduxkotlin.Store<S> = this
    override val getState: org.reduxkotlin.GetState<S> = { current }
    override var dispatch: org.reduxkotlin.Dispatcher = { action ->
        @Suppress("UNCHECKED_CAST")
        run { current = action as S }
        val snapshot = listeners.toList()
        snapshot.forEach { l -> pending.add { l() } }
        action
    }
    override val subscribe: (org.reduxkotlin.StoreSubscriber) -> org.reduxkotlin.StoreSubscription = { l ->
        listeners.add(l)
        val unsubscribe: org.reduxkotlin.StoreSubscription = {
            listeners.remove(l)
            Unit
        }
        unsubscribe
    }
    override val replaceReducer: (org.reduxkotlin.Reducer<S>) -> Unit = { }
}

@OptIn(ExperimentalTestApi::class)
class FieldStateTest {

    @Test
    fun fieldState_renders_initial_value_on_first_frame() = runComposeUiTest {
        val store = newStore()
        setContent {
            val counter by store.fieldState(CounterState::counter)
            Text(text = "counter=$counter")
        }
        onAllNodesWithText("counter=0").assertCountEquals(1)
    }

    @Test
    fun fieldState_updates_state_value_after_dispatch() = runComposeUiTest {
        val store = newStore()
        setContent {
            val counter by store.fieldState(CounterState::counter)
            Text(text = "counter=$counter")
        }
        onAllNodesWithText("counter=0").assertCountEquals(1)

        store.dispatch(Increment())
        waitForIdle()
        onAllNodesWithText("counter=1").assertCountEquals(1)

        store.dispatch(Increment(amount = 4))
        waitForIdle()
        onAllNodesWithText("counter=5").assertCountEquals(1)
    }

    @Test
    fun fieldState_two_fields_track_independently() = runComposeUiTest {
        val store = newStore()
        setContent {
            val counter by store.fieldState(CounterState::counter)
            val label by store.fieldState(CounterState::label)
            Text(text = "counter=$counter")
            Text(text = "label=$label")
        }
        onAllNodesWithText("counter=0").assertCountEquals(1)
        onAllNodesWithText("label=init").assertCountEquals(1)

        // Only counter moves — label binding stays at "init".
        store.dispatch(Increment(amount = 3))
        waitForIdle()
        onAllNodesWithText("counter=3").assertCountEquals(1)
        onAllNodesWithText("label=init").assertCountEquals(1)

        // Only label moves — counter binding stays at "3".
        store.dispatch(SetLabel("hello"))
        waitForIdle()
        onAllNodesWithText("counter=3").assertCountEquals(1)
        onAllNodesWithText("label=hello").assertCountEquals(1)
    }

    @Test
    fun selectorState_recomputes_only_when_derived_value_changes() = runComposeUiTest {
        val store = newStore()
        var derivedReads = 0
        setContent {
            val parity by store.selectorState { state -> state.counter % 2 }
            derivedReads++
            Text(text = "parity=$parity")
        }
        val initial = derivedReads

        // Same parity → no recomposition triggered by State<F>.
        store.dispatch(Increment(amount = 2))
        waitForIdle()
        check(derivedReads == initial) { "selectorState fired when derived value unchanged" }

        // Flipping parity → recomposition.
        store.dispatch(Increment(amount = 1))
        waitForIdle()
        check(derivedReads > initial) { "selectorState did not fire on actual change" }
        onAllNodesWithText("parity=1").assertCountEquals(1)
    }

    @Test
    fun fieldState_b3_race_window_picks_up_dispatch_between_remember_and_effect() = runComposeUiTest {
        // The granular subscription is installed inside DisposableEffect,
        // which fires at commit. If the store dispatches between the
        // `remember { mutableStateOf(...) }` (composition time) and the
        // effect (commit time), and if we DIDN'T re-sample under the
        // effect, the first observed frame would be stale until the
        // next dispatch fired the subscriber.
        //
        // We simulate this by dispatching DURING setContent's body, then
        // assert the first observable frame reflects the post-dispatch
        // value. The runComposeUiTest harness drives composition and
        // effects synchronously enough for this to be deterministic.
        val store = newStore()
        setContent {
            // Dispatch happens during composition body — i.e. AFTER
            // remember { mutableStateOf(property.get(store.state)) } has
            // already captured counter=0.
            store.dispatch(Increment(amount = 7))
            val counter by store.fieldState(CounterState::counter)
            Text(text = "race=$counter")
        }
        waitForIdle()
        // Without the B3 re-sample, this would be "race=0" until another
        // dispatch arrived. With the re-sample inside DisposableEffect,
        // we pick up the in-flight increment immediately.
        onAllNodesWithText("race=7").assertCountEquals(1)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun selectorStateReadsFreshStateWhenRecomposedBeforeAsyncNotifyDrains() = runComposeUiTest {
        val store = DeferredNotifyStore("a")
        val external = mutableStateOf(0)
        setContent {
            val tick = external.value
            val value by store.selectorState { it }
            Text("v=$value t=$tick")
        }
        waitForIdle()
        onAllNodesWithText("v=a t=0").assertCountEquals(1)
        store.dispatch("b") // state updated synchronously; notification NOT drained
        external.value = 1 // force recomposition via external state
        waitForIdle()
        onAllNodesWithText("v=b t=1").assertCountEquals(1) // must be fresh "b", not stale "a"
    }

    @Test
    fun selectorState_renders_initial_value() = runComposeUiTest {
        val store = newStore()
        setContent {
            val doubled by store.selectorState { state -> state.counter * 2 }
            Text(text = "doubled=$doubled")
        }
        onAllNodesWithText("doubled=0").assertCountEquals(1)

        store.dispatch(Increment(amount = 5))
        waitForIdle()
        onAllNodesWithText("doubled=10").assertCountEquals(1)
    }
}
