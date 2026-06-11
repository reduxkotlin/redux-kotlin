package org.reduxkotlin.granular

import org.reduxkotlin.Dispatcher
import org.reduxkotlin.GetState
import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscriber
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the activate-time re-diff (hardening plan C2(b)): a state change landing
 * during registration — after `on()` sampled `entry.last`, before (or while)
 * `activate()` installs the underlying `store.subscribe` — must fire the
 * listener at activation instead of being silently missed.
 */
class ActivationReDiffTest {

    private data class S(val count: Int = 0, val label: String = "init")
    private object Inc

    private val reducer: Reducer<S> = { s, a -> if (a is Inc) s.copy(count = s.count + 1) else s }

    /** Delegates to a real store but dispatches [Inc] inside `subscribe`, BEFORE registering. */
    private class MutateOnSubscribeStore(private val inner: Store<S>) : Store<S> {
        override val store: Store<S> = this
        override val getState: GetState<S> = inner.getState
        override var dispatch: Dispatcher = { a -> inner.dispatch(a) }
        override val subscribe: (StoreSubscriber) -> StoreSubscription = { l ->
            inner.dispatch(Inc) // lands exactly in the registration window
            inner.subscribe(l)
        }
        override val replaceReducer: (Reducer<S>) -> Unit = inner.replaceReducer
    }

    @Test
    fun changeLandingDuringRegistrationFiresAtActivate() {
        val store = MutateOnSubscribeStore(createStore(reducer, S()))
        val fired = mutableListOf<Pair<Int, Int>>()

        store.subscribeTo({ it.count }, triggerOnSubscribe = false) { old, new ->
            fired += old to new
        }

        // The change landed inside subscribe() with no notification for it;
        // only an activate-time re-diff can observe it.
        assertEquals(listOf(0 to 1), fired, "registration-window change must fire at activate")

        // The diff state is consistent afterwards: the next real change fires once.
        store.dispatch(Inc)
        assertEquals(listOf(0 to 1, 1 to 2), fired)
    }

    @Test
    fun triggerOnSubscribeFiresTheRealDiffWhenTheWindowChangedTheValue() {
        val store = MutateOnSubscribeStore(createStore(reducer, S()))
        val fired = mutableListOf<Pair<Int, Int>>()

        store.subscribeTo({ it.count }, triggerOnSubscribe = true) { old, new ->
            fired += old to new
        }

        // Exactly one activation callback: the real (0, 1) diff subsumes the
        // (current, current) trigger — no double-fire.
        assertEquals(listOf(0 to 1), fired)
    }

    @Test
    fun triggerOnSubscribeStillFiresCurrentCurrentWhenNothingChanged() {
        val store = createStore(reducer, S())
        val fired = mutableListOf<Pair<Int, Int>>()

        store.subscribeTo({ it.count }, triggerOnSubscribe = true) { old, new ->
            fired += old to new
        }

        assertEquals(listOf(0 to 0), fired, "unchanged value keeps the (current, current) trigger")
    }

    @Test
    fun untouchedEntriesDoNotFireAtActivate() {
        val store = MutateOnSubscribeStore(createStore(reducer, S()))
        val labelFired = mutableListOf<Pair<String, String>>()

        store.subscribeTo({ it.label }, triggerOnSubscribe = false) { old, new ->
            labelFired += old to new
        }

        // The window change touched `count`, not `label` — no spurious callback.
        assertEquals(emptyList(), labelFired)
    }
}
