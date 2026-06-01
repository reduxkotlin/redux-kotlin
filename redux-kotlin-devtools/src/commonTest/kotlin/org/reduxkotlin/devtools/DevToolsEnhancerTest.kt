package org.reduxkotlin.devtools

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
        val config = DevToolsConfig(denylist = listOf("Noisy"))
        assertFalse(shouldSend(Noisy(1), config))
        assertTrue(shouldSend(Increment(1), config))
    }

    @Test
    fun shouldSendRespectsAllowlist() {
        val config = DevToolsConfig(allowlist = listOf("Increment"))
        assertTrue(shouldSend(Increment(1), config))
        assertFalse(shouldSend(Noisy(1), config))
    }
}
