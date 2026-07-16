package org.reduxkotlin.granular

import org.reduxkotlin.concurrent.createConcurrentStore
import kotlin.test.Test
import kotlin.test.assertEquals

private data class ConcurrentSelectorState(val count: Int = 0)

private object IncrementConcurrentSelector

class SelectorSubscriptionsConcurrentStoreTest {

    @Test
    fun sharedScope_observes_the_final_concurrent_store() {
        val store = createConcurrentStore<ConcurrentSelectorState>(
            reducer = { state, action ->
                if (action is IncrementConcurrentSelector) state.copy(count = state.count + 1) else state
            },
            preloadedState = ConcurrentSelectorState(),
        )
        val seen = mutableListOf<Pair<Int, Int>>()
        val subscriptions = store.selectorSubscriptions()
        subscriptions.subscribeTo({ it.count }, triggerOnSubscribe = false) { old, new -> seen += old to new }

        store.dispatch(IncrementConcurrentSelector)
        store.dispatch(IncrementConcurrentSelector)

        assertEquals(listOf(0 to 1, 1 to 2), seen)
    }
}
