package org.reduxkotlin.granular.concurrency

import org.reduxkotlin.concurrent.coalescingNotificationContext
import org.reduxkotlin.concurrent.createConcurrentStore
import org.reduxkotlin.granular.subscribeTo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies granular diffs do not run ahead of an older queued notification. */
class CoalescingContextGranularTest {

    private data class State(val count: Int = 0)
    private object Increment

    @Test
    fun targetDispatchBehindWorkerSignalWaitsForTheFifoDrain() {
        val scheduled = ArrayDeque<() -> Unit>()
        var onTarget = false
        val store = createConcurrentStore(
            reducer = { state: State, action ->
                if (action is Increment) state.copy(count = state.count + 1) else state
            },
            preloadedState = State(),
            notificationContext = coalescingNotificationContext(
                isOnTargetThread = { onTarget },
                post = { scheduled.addLast(it) },
            ),
        )
        val changes = mutableListOf<Pair<Int, Int>>()
        store.subscribeTo(State::count, triggerOnSubscribe = false) { old, new -> changes += old to new }

        store.dispatch(Increment)
        onTarget = true
        store.dispatch(Increment)

        assertEquals(emptyList(), changes, "target delivery must wait behind the older worker signal")
        scheduled.removeFirst()()
        assertEquals(listOf(0 to 2), changes)
    }
}
