package org.reduxkotlin.compose

import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.reduxkotlin.concurrent.ConcurrentStore
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.createConcurrentStore

private data class BState(val count: Int = 0, val label: String = "init")
private data class Add(val amount: Int = 1)

private fun bReducer(state: BState, action: Any): BState = when (action) {
    is Add -> state.copy(count = state.count + action.amount)
    else -> state
}

// Local re-declaration of the queueing context (test fixtures don't cross modules):
// posts are captured and run only on drain(), making async notification deterministic.
private class QueueingContext : NotificationContext {
    private val queue = mutableListOf<() -> Unit>()
    override fun post(block: () -> Unit) {
        queue += block
    }
    fun drain() {
        while (queue.isNotEmpty()) {
            val batch = queue.toList()
            queue.clear()
            batch.forEach { it() }
        }
    }
}

private fun queuedConcurrentStore(): Pair<ConcurrentStore<BState>, QueueingContext> {
    val queue = QueueingContext()
    return createConcurrentStore(::bReducer, BState(), notificationContext = queue) to queue
}

/**
 * Binding behavior against the real `ConcurrentStore` (closes test gap T6 —
 * the async-notify guarantees were previously proven only against a
 * hand-rolled fake store).
 */
@OptIn(ExperimentalTestApi::class)
class ConcurrentStoreBindingTest {

    @Test
    fun bindingUpdatesAfterNotificationDrain() = runComposeUiTest {
        val (store, queue) = queuedConcurrentStore()
        setContent {
            val count by store.fieldState(BState::count)
            Text("count=$count")
        }
        waitForIdle()
        onAllNodesWithText("count=0").assertCountEquals(1)

        store.dispatch(Add())
        queue.drain()
        waitForIdle()
        onAllNodesWithText("count=1").assertCountEquals(1)
    }

    @Test
    fun bindingReadsFreshStateWhenRecomposedBeforeDrain() = runComposeUiTest {
        val (store, queue) = queuedConcurrentStore()
        val external = mutableStateOf(0)
        setContent {
            val tick = external.value
            val count by store.fieldState(BState::count)
            Text("count=$count t=$tick")
        }
        waitForIdle()
        onAllNodesWithText("count=0 t=0").assertCountEquals(1)

        store.dispatch(Add()) // mirror published synchronously; notification queued
        external.value = 1 // recompose via unrelated state, notification NOT drained
        waitForIdle()
        // Live getter against the real store: reads the published mirror, not a cache.
        onAllNodesWithText("count=1 t=1").assertCountEquals(1)
        queue.drain()
        waitForIdle()
        onAllNodesWithText("count=1 t=1").assertCountEquals(1)
    }

    @Test
    fun bindingCatchesChangeThatLandedBeforeItSubscribedOnTheRealStore() = runComposeUiTest {
        val (store, queue) = queuedConcurrentStore()
        setContent {
            // Commits before the binding's DisposableEffect (composition order):
            // the dispatch's notification is queued and never drained, so only the
            // post-install re-sample can catch the change.
            DisposableEffect(Unit) {
                store.dispatch(Add(amount = 7))
                onDispose {}
            }
            val count by store.fieldState(BState::count)
            Text("count=$count")
        }
        waitForIdle()
        onAllNodesWithText("count=7").assertCountEquals(1)
        check(store.state.count == 7)
        // `queue` deliberately not drained — the re-sample alone must suffice.
        queue.drain() // cleanliness; must not change the rendered value
        waitForIdle()
        onAllNodesWithText("count=7").assertCountEquals(1)
    }
}
