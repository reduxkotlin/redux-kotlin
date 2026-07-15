package org.reduxkotlin.granular

import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscriber
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private data class SelectorState(val values: List<Int> = List(4) { 0 }, val unrelated: Int = 0)

private object AdvanceUnrelated
private object AdvanceFirst

private val selectorReducer: (SelectorState, Any) -> SelectorState = { state, action ->
    when (action) {
        AdvanceUnrelated -> state.copy(unrelated = state.unrelated + 1)

        AdvanceFirst -> state.copy(
            values = state.values.mapIndexed { index, value ->
                if (index == 0) value + 1 else value
            },
        )

        else -> state
    }
}

private class CallbackCountingStore<S>(private val delegate: Store<S>) : Store<S> by delegate {
    var callbackCount: Int = 0
        private set

    var activeSubscriptions: Int = 0
        private set

    override val subscribe: (StoreSubscriber) -> StoreSubscription = { subscriber ->
        activeSubscriptions++
        val unsubscribe = delegate.subscribe {
            callbackCount++
            subscriber()
        }
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

class SelectorSubscriptionsTest {

    @Test
    fun sharedScope_reduces_store_callbacks_but_not_selector_evaluations() {
        val independentStore = CallbackCountingStore(createStore(selectorReducer, SelectorState()))
        var independentEvaluations = 0
        repeat(4) { index ->
            independentStore.subscribeTo(
                selector = { state ->
                    independentEvaluations++
                    state.values[index]
                },
                triggerOnSubscribe = false,
            ) { _, _ -> }
        }
        independentEvaluations = 0
        independentStore.dispatch(AdvanceUnrelated)

        val sharedStore = CallbackCountingStore(createStore(selectorReducer, SelectorState()))
        val subscriptions = sharedStore.selectorSubscriptions()
        var sharedEvaluations = 0
        repeat(4) { index ->
            subscriptions.subscribeTo(
                selector = { state ->
                    sharedEvaluations++
                    state.values[index]
                },
                triggerOnSubscribe = false,
            ) { _, _ -> }
        }
        sharedEvaluations = 0
        sharedStore.dispatch(AdvanceUnrelated)

        assertEquals(4, independentStore.callbackCount)
        assertEquals(1, sharedStore.callbackCount)
        assertEquals(4, independentEvaluations)
        assertEquals(4, sharedEvaluations)
    }

    @Test
    fun memoizedSelector_skips_transform_when_declared_input_is_unchanged() {
        val store = createStore(selectorReducer, SelectorState(values = listOf(1, 2, 3, 4)))
        var transformCalls = 0
        val total = memoizedSelector({ state: SelectorState -> state.values }) { values ->
            transformCalls++
            values.sum()
        }
        store.subscribeTo(total, triggerOnSubscribe = false) { _, _ -> }

        repeat(3) { store.dispatch(AdvanceUnrelated) }
        assertEquals(1, transformCalls, "unrelated state must not rerun the transform")

        store.dispatch(AdvanceFirst)
        assertEquals(2, transformCalls)
        assertEquals(11, total(store.state))
        assertEquals(2, transformCalls, "a read with unchanged input must use the cached result")
    }

    @Test
    fun scope_removes_source_subscription_when_last_entry_is_removed_or_closed() {
        val store = CallbackCountingStore(createStore(selectorReducer, SelectorState()))
        val subscriptions = store.selectorSubscriptions()
        val first = subscriptions.subscribeTo({ it.values[0] }, triggerOnSubscribe = false) { _, _ -> }
        val second = subscriptions.subscribeTo({ it.values[1] }, triggerOnSubscribe = false) { _, _ -> }

        assertEquals(1, store.activeSubscriptions)
        first()
        assertEquals(1, store.activeSubscriptions)
        second()
        assertEquals(0, store.activeSubscriptions)

        subscriptions.subscribeTo({ it.values[2] }, triggerOnSubscribe = false) { _, _ -> }
        assertEquals(1, store.activeSubscriptions)
        subscriptions.close()
        assertEquals(0, store.activeSubscriptions)
        assertFailsWith<IllegalStateException> {
            subscriptions.subscribeTo({ it.values[3] }, triggerOnSubscribe = false) { _, _ -> }
        }
    }

    @Test
    fun scope_resamples_a_change_landing_during_source_subscription_install() {
        val inner = createStore(selectorReducer, SelectorState())
        val store = object : Store<SelectorState> by inner {
            override val subscribe: (StoreSubscriber) -> StoreSubscription = { listener ->
                inner.dispatch(AdvanceFirst)
                inner.subscribe(listener)
            }
        }
        val seen = mutableListOf<Pair<Int, Int>>()

        store.selectorSubscriptions().subscribeTo({ it.values[0] }, triggerOnSubscribe = false) { old, new ->
            seen += old to new
        }

        assertEquals(listOf(0 to 1), seen)
    }
}
