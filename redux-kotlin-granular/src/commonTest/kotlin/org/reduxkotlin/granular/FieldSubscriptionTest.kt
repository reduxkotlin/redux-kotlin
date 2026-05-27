package org.reduxkotlin.granular

import org.reduxkotlin.Reducer
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

private data class TestState(val counter: Int = 0, val label: String = "", val items: List<String> = emptyList())

private sealed class TestAction {
    object Increment : TestAction()

    data class SetLabel(val label: String) : TestAction()

    data class AppendItem(val item: String) : TestAction()

    object Noop : TestAction()
}

private val reducer: Reducer<TestState> = { state, action ->
    when (action) {
        is TestAction.Increment -> state.copy(counter = state.counter + 1)
        is TestAction.SetLabel -> state.copy(label = action.label)
        is TestAction.AppendItem -> state.copy(items = state.items + action.item)
        else -> state
    }
}

class FieldSubscriptionTest {

    @Test
    fun subscribeTo_fires_when_field_changes() {
        val store = createStore(reducer, TestState())
        val seen = mutableListOf<Pair<Int, Int>>()
        store.subscribeTo(TestState::counter, triggerOnSubscribe = false) { old, new ->
            seen += old to new
        }
        store.dispatch(TestAction.Increment)
        store.dispatch(TestAction.Increment)
        assertEquals(listOf(0 to 1, 1 to 2), seen)
    }

    @Test
    fun subscribeTo_does_not_fire_when_field_unchanged() {
        val store = createStore(reducer, TestState(label = "hello"))
        val seen = mutableListOf<Pair<String, String>>()
        store.subscribeTo(TestState::label, triggerOnSubscribe = false) { old, new ->
            seen += old to new
        }
        store.dispatch(TestAction.Increment) // counter changes, label doesn't
        store.dispatch(TestAction.Increment)
        assertTrue(seen.isEmpty(), "label listener fired despite no label change: $seen")
    }

    @Test
    fun triggerOnSubscribe_true_fires_once_with_current_value() {
        val store = createStore(reducer, TestState(counter = 7))
        val seen = mutableListOf<Pair<Int, Int>>()
        store.subscribeTo(TestState::counter, triggerOnSubscribe = true) { old, new ->
            seen += old to new
        }
        assertEquals(listOf(7 to 7), seen)
    }

    @Test
    fun triggerOnSubscribe_false_waits_for_first_change() {
        val store = createStore(reducer, TestState(counter = 7))
        val seen = mutableListOf<Pair<Int, Int>>()
        store.subscribeTo(TestState::counter, triggerOnSubscribe = false) { old, new ->
            seen += old to new
        }
        assertTrue(seen.isEmpty())
        store.dispatch(TestAction.Increment)
        assertEquals(listOf(7 to 8), seen)
    }

    @Test
    fun referential_fast_path_skips_listener_when_state_reference_unchanged() {
        // Reducer returns the same instance for unknown action → reference
        // equality kicks in and no listener should fire.
        val store = createStore(reducer, TestState(counter = 5))
        var fired = false
        store.subscribeTo(TestState::counter, triggerOnSubscribe = false) { _, _ -> fired = true }
        store.dispatch(TestAction.Noop)
        assertFalse(fired, "listener fired despite identical state reference")
    }

    @Test
    fun subscribeFields_batches_multiple_entries_under_one_underlying_subscriber() {
        val store = createStore(reducer, TestState())
        val counterEvents = mutableListOf<Pair<Int, Int>>()
        val labelEvents = mutableListOf<Pair<String, String>>()
        store.subscribeFields {
            on(TestState::counter, triggerOnSubscribe = false) { o, n -> counterEvents += o to n }
            on(TestState::label, triggerOnSubscribe = false) { o, n -> labelEvents += o to n }
        }
        store.dispatch(TestAction.Increment)
        store.dispatch(TestAction.SetLabel("hi"))
        store.dispatch(TestAction.Increment)
        assertEquals(listOf(0 to 1, 1 to 2), counterEvents)
        assertEquals(listOf("" to "hi"), labelEvents)
    }

    @Test
    fun subscribeFields_returns_combined_unsubscribe() {
        val store = createStore(reducer, TestState())
        val counterEvents = mutableListOf<Int>()
        val labelEvents = mutableListOf<String>()
        val unsubscribe = store.subscribeFields {
            on(TestState::counter, triggerOnSubscribe = false) { _, n -> counterEvents += n }
            on(TestState::label, triggerOnSubscribe = false) { _, n -> labelEvents += n }
        }
        store.dispatch(TestAction.Increment)
        unsubscribe()
        store.dispatch(TestAction.Increment)
        store.dispatch(TestAction.SetLabel("after"))
        assertEquals(listOf(1), counterEvents)
        assertTrue(labelEvents.isEmpty(), "label listener fired after unsubscribe")
    }

    @Test
    fun selector_exception_does_not_break_other_subscribers() {
        val store = createStore(reducer, TestState())
        var goodCount = 0
        var capturedError: Throwable? = null
        store.subscribeFields(onSelectorError = { capturedError = it }) { scope ->
            scope.on<String>(
                selector = { error("boom") },
                triggerOnSubscribe = false,
            ) { _, _ -> fail("listener should never fire because selector threw") }
            scope.on(TestState::counter, triggerOnSubscribe = false) { _, _ -> goodCount += 1 }
        }
        store.dispatch(TestAction.Increment)
        store.dispatch(TestAction.Increment)
        assertEquals(2, goodCount, "the well-behaved listener must still see both ticks")
        assertEquals("boom", capturedError?.message)
    }

    @Test
    fun on_after_block_completes_throws() {
        val store = createStore(reducer, TestState())
        val capturedScope = arrayOfNulls<FieldSubscriptionScope<TestState>>(1)
        store.subscribeFields { scope -> capturedScope[0] = scope }
        val outOfBandError = runCatching {
            capturedScope[0]!!.on(TestState::counter, triggerOnSubscribe = false) { _, _ -> }
        }.exceptionOrNull()
        assertNull(
            outOfBandError?.let { it as? AssertionError },
            "registering after activate() must throw, not silently no-op (was: $outOfBandError)",
        )
        assertTrue(outOfBandError is IllegalStateException)
    }

    @Test
    fun lambda_selector_overload_matches_property_ref_overload() {
        val store = createStore(reducer, TestState(counter = 3))
        val a = mutableListOf<Int>()
        val b = mutableListOf<Int>()
        store.subscribeTo(TestState::counter, triggerOnSubscribe = false) { _, n -> a += n }
        store.subscribeTo({ it.counter }, triggerOnSubscribe = false) { _, n -> b += n }
        store.dispatch(TestAction.Increment)
        store.dispatch(TestAction.Increment)
        assertEquals(a, b)
        assertEquals(listOf(4, 5), a)
    }

    @Test
    fun derived_selector_with_computed_value() {
        val store = createStore(reducer, TestState())
        val sizes = mutableListOf<Int>()
        store.subscribeTo({ it.items.size }, triggerOnSubscribe = true) { _, n -> sizes += n }
        store.dispatch(TestAction.AppendItem("a"))
        store.dispatch(TestAction.AppendItem("b"))
        store.dispatch(TestAction.Increment) // size unchanged
        assertEquals(listOf(0, 1, 2), sizes)
    }

    @Test
    fun granular_enhancer_is_a_noop() {
        // Verifies the marker enhancer doesn't break the store's identity.
        val baseStore = createStore(reducer, TestState())
        val sub = baseStore.subscribeTo(TestState::counter, triggerOnSubscribe = false) { _, _ -> }
        baseStore.dispatch(TestAction.Increment)
        sub()
        // marker exists; just smoke-call to confirm it compiles + returns a creator
        val enhancer = granularSubscriptionsEnhancer<TestState>()

        @Suppress("UNCHECKED_CAST")
        val wrapped = enhancer({ r, init, _ -> baseStore as org.reduxkotlin.Store<TestState> })
        assertSame(baseStore, wrapped(reducer, TestState(), null))
    }
}
