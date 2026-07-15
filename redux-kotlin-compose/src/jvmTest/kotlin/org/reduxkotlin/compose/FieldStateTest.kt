package org.reduxkotlin.compose

import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscriber
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.createStore
import kotlin.test.assertEquals

private data class CounterState(val counter: Int = 0, val label: String = "init")
private data class Increment(val amount: Int = 1)
private data class SetLabel(val text: String)

private fun reducer(state: CounterState, action: Any): CounterState = when (action) {
    is Increment -> state.copy(counter = state.counter + action.amount)
    is SetLabel -> state.copy(label = action.text)
    else -> state
}

private fun newStore(): Store<CounterState> = createStore(::reducer, CounterState())

private class SubscriptionCountingStore<S>(private val delegate: Store<S>) : Store<S> by delegate {
    var activeSubscriptions: Int = 0
        private set

    override val subscribe: (StoreSubscriber) -> StoreSubscription = { subscriber ->
        activeSubscriptions++
        val unsubscribe = delegate.subscribe(subscriber)
        var active = true
        {
            if (active) {
                active = false
                activeSubscriptions--
                unsubscribe()
            }
        }
    }
}

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

// Lands a silent state change exactly inside the binding's effect-time window: `subscribe()`
// first mutates state WITHOUT notifying, then registers the listener. Whatever the binding does
// before its subscription is installed cannot see this change; only work done AFTER install can.
private class MutateOnSubscribeStore(initial: String, private val valueAtSubscribe: String) :
    org.reduxkotlin.Store<String> {
    private var current: String = initial
    private val listeners = mutableListOf<org.reduxkotlin.StoreSubscriber>()

    override val store: org.reduxkotlin.Store<String> = this
    override val getState: org.reduxkotlin.GetState<String> = { current }
    override var dispatch: org.reduxkotlin.Dispatcher = { action ->
        @Suppress("UNCHECKED_CAST")
        run { current = action as String }
        action
    }
    override val subscribe: (org.reduxkotlin.StoreSubscriber) -> org.reduxkotlin.StoreSubscription = { l ->
        current = valueAtSubscribe // silent change DURING registration; no notification follows
        listeners.add(l)
        val unsubscribe: org.reduxkotlin.StoreSubscription = {
            listeners.remove(l)
            Unit
        }
        unsubscribe
    }
    override val replaceReducer: (org.reduxkotlin.Reducer<String>) -> Unit = { }
}

@OptIn(ExperimentalTestApi::class)
class FieldStateTest {

    @Test
    fun bindingCatchesChangeThatLandedDuringSubscriptionInstall() = runComposeUiTest {
        val store = MutateOnSubscribeStore(initial = "a", valueAtSubscribe = "b")
        setContent {
            val value by store.selectorState { it }
            Text("v=$value")
        }
        waitForIdle()
        // The change landed inside subscribe() itself, with no notification ever delivered.
        // Only a re-sample that runs AFTER the subscription is installed can catch it; the
        // pre-install re-sample order left the binding stale on "a" forever.
        onAllNodesWithText("v=b").assertCountEquals(1)
    }

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
        // Mid-composition dispatch: the store state changes between the binding's creation and
        // the first read. Because the returned State reads `store.state` live in its getter
        // (not a cached snapshot), the first composed value already reflects the dispatch —
        // no stale initial value, no need for a re-sample.
        //
        // We simulate this by dispatching DURING setContent's body, then assert the first
        // observable frame reflects the post-dispatch value. The runComposeUiTest harness
        // drives composition and effects synchronously enough for this to be deterministic.
        val store = newStore()
        setContent {
            // Dispatch ONCE during the first composition (in a remember, not every recomposition).
            // The binding now re-samples at subscribe (triggerOnSubscribe), so an unconditional in-body
            // dispatch would recompose → dispatch → loop. store.state is counter=7 by the first read.
            remember { store.dispatch(Increment(amount = 7)) }
            val counter by store.fieldState(CounterState::counter)
            Text(text = "race=$counter")
        }
        waitForIdle()
        // The getter reads store.state live, so "race=7" is visible immediately —
        // no stale initial value, no extra dispatch needed.
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun bindingCatchesChangeThatLandedBeforeItSubscribed() = runComposeUiTest {
        val store = DeferredNotifyStore("a")
        setContent {
            // Runs at commit, in composition order BEFORE the binding's DisposableEffect below →
            // changes state after the binding's first composition but before it subscribes. The store
            // defers notifications (never drained here), so only the subscribe-time re-sample can catch it.
            DisposableEffect(Unit) {
                store.dispatch("b")
                onDispose {}
            }
            val value by store.selectorState { it }
            Text("v=$value")
        }
        waitForIdle()
        onAllNodesWithText("v=b").assertCountEquals(1) // must reflect "b", not stale "a"
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

    @Test
    fun sharedSelectorSubscriptions_use_one_store_subscription_and_dispose_with_the_subtree() = runComposeUiTest {
        val store = SubscriptionCountingStore(newStore())
        val visible = mutableStateOf(true)
        setContent {
            if (visible.value) {
                val subscriptions = store.rememberSelectorSubscriptions()
                val counter by store.fieldState(subscriptions, CounterState::counter)
                val label by store.fieldState(subscriptions, CounterState::label)
                Text("counter=$counter label=$label")
            }
        }
        waitForIdle()
        assertEquals(1, store.activeSubscriptions)

        store.dispatch(Increment())
        waitForIdle()
        onAllNodesWithText("counter=1 label=init").assertCountEquals(1)

        visible.value = false
        waitForIdle()
        assertEquals(0, store.activeSubscriptions)
    }

    @Test
    fun keyedSelectorState_replaces_a_selector_that_captures_a_changing_parameter() = runComposeUiTest {
        val store = newStore()
        val selectCounter = mutableStateOf(true)
        setContent {
            val value by store.selectorState(selectCounter.value) { state ->
                if (selectCounter.value) state.counter.toString() else state.label
            }
            Text("selected=$value")
        }
        onAllNodesWithText("selected=0").assertCountEquals(1)

        selectCounter.value = false
        waitForIdle()
        onAllNodesWithText("selected=init").assertCountEquals(1)

        store.dispatch(SetLabel("updated"))
        waitForIdle()
        onAllNodesWithText("selected=updated").assertCountEquals(1)
    }
}
