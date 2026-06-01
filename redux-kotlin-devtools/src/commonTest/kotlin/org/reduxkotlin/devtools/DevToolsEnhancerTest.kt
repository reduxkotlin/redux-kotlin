package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class CountState(val count: Int = 0)
private data class Increment(val by: Int)
private data class Noisy(val tick: Int)

class DevToolsEnhancerTest {
    private val reducer = { state: CountState, action: Any ->
        when (action) {
            is Increment -> state.copy(count = state.count + action.by)
            else -> state
        }
    }

    @Test
    fun enhancerDoesNotAlterReducerBehaviorOrState() {
        // port 65535 → no real connection forms; dispatch must still work.
        val store = createStore(
            reducer,
            CountState(),
            devTools(DevToolsConfig(name = "T", port = 65535)),
        )
        store.dispatch(Increment(5))
        store.dispatch(Increment(2))
        assertEquals(7, store.state.count)
    }

    @Test
    fun shouldSendRespectsDenylist() {
        assertFalse(shouldSend(Noisy(1), denylist = listOf(Regex("Noisy")), allowlist = emptyList()))
        assertTrue(shouldSend(Increment(1), denylist = listOf(Regex("Noisy")), allowlist = emptyList()))
    }

    @Test
    fun shouldSendRespectsAllowlist() {
        assertTrue(shouldSend(Increment(1), denylist = emptyList(), allowlist = listOf(Regex("Increment"))))
        assertFalse(shouldSend(Noisy(1), denylist = emptyList(), allowlist = listOf(Regex("Increment"))))
    }

    @Test
    fun recorderSnapshotReflectsDispatchedActions() {
        val r = LiftedStateRecorder(maxAge = 50, clock = { 1L })
        r.init(JsonPrimitive(0))
        r.record(JsonPrimitive("INC"), JsonPrimitive(1))
        r.record(JsonPrimitive("INC"), JsonPrimitive(2))
        val lifted = r.liftedState()
        assertEquals(3, (lifted["nextActionId"] as JsonPrimitive).content.toInt())
    }
}
