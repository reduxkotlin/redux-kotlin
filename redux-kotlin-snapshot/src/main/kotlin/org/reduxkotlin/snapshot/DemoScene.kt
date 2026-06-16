package org.reduxkotlin.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.reduxkotlin.Reducer
import org.reduxkotlin.createStore

/** A trivial counter state for the redux-backed demo scene (internal fixture, not consumer API). */
internal data class CounterState(val count: Int = 0)

/** Increments [CounterState.count] on the `"inc"` action; ignores everything else. */
internal val counterReducer: Reducer<CounterState> = { state, action ->
    if (action == "inc") state.copy(count = state.count + 1) else state
}

/**
 * A redux-free, deterministic demo registry used to self-test the whole pipe in CI.
 *
 * - `counter` proves `f(redux-state) -> UI`: it builds a real redux-kotlin [createStore], dispatches
 *   N `"inc"` actions from the input, and renders `count` solid bars (no text -> font-independent ->
 *   golden is stable across OS). This is the committed-golden scene.
 * - `demo` renders a single text label (used for render/determinism smoke only, not a cross-host golden).
 */
public val demoSnapshots: SnapshotApp = snapshotApp {
    defaults {
        width = 200
        height = 200
        density = 2f
        theme = "dark"
    }

    scene("counter") {
        presets("n0", "n3")
        render { args ->
            val n = when (val i = args.input) {
                is SnapshotInput.Preset -> i.name.removePrefix("n").toIntOrNull() ?: 0

                is SnapshotInput.Json -> {
                    val c = (i.json as? JsonObject)?.get("count")?.jsonPrimitive?.content
                    c?.toIntOrNull() ?: 0
                }
            }
            val store = createStore(counterReducer, CounterState())
            repeat(n) { store.dispatch("inc") }
            val count = store.state.count
            val bg = if (args.theme == "light") Color.White else Color(0xFF101418)
            val bar = if (args.theme == "light") Color(0xFF3355DD) else Color(0xFF66AAFF)
            val content: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(count) {
                            Box(Modifier.fillMaxWidth().height(24.dp).background(bar))
                        }
                    }
                }
            }
            content
        }
    }

    scene("demo") {
        presets("default", "light")
        render { args ->
            val bg = if (args.theme == "light") Color.White else Color(0xFF101418)
            val fg = if (args.theme == "light") Color.Black else Color.White
            val content: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                    Text("snapshot ok", color = fg, fontSize = 20.sp, modifier = Modifier.padding(8.dp))
                }
            }
            content
        }
    }
}
