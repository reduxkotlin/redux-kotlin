package org.reduxkotlin.devtools.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.ui.theme.RkTokens

/** The State tab: a recursive, expandable tree of the selected action's serialized state. */
@Composable
public fun StateTab(state: JsonElement?) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (state == null) {
            Text("No state yet.", color = RkTokens.InkDim)
        } else {
            JsonNode(key = "state", value = state, depth = 0)
        }
    }
}

private fun primitiveColor(value: JsonPrimitive): Color = when {
    value.isString -> RkTokens.Green
    value.content == "true" || value.content == "false" -> RkTokens.Magenta
    value.content.toDoubleOrNull() != null -> RkTokens.Orange
    else -> RkTokens.InkDim
}

private fun containerEntries(value: JsonElement): List<Pair<String, JsonElement>> = when (value) {
    is JsonArray -> value.mapIndexed { i, v -> i.toString() to v }
    is JsonObject -> value.entries.map { it.key to it.value }
    else -> emptyList()
}

@Composable
private fun JsonNode(key: String?, value: JsonElement, depth: Int) {
    val pad = (depth * 14).dp
    when (value) {
        is JsonObject, is JsonArray -> {
            var open by remember { mutableStateOf(depth < 2) }
            val count = if (value is JsonArray) value.size else (value as JsonObject).size
            val summary = if (value is JsonArray) "Array($count)" else "{$count}"
            Text(
                text = (key?.let { "$it: " } ?: "") + (if (open) "▾ " else "▸ ") + summary,
                color = RkTokens.BlueLight,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = pad).clickable { open = !open },
            )
            if (open) {
                containerEntries(value).forEach { (k, v) -> JsonNode(k, v, depth + 1) }
            }
        }

        is JsonPrimitive -> {
            val color = primitiveColor(value)
            val text = if (value.isString) "\"${value.content}\"" else value.content
            Text(
                "${key?.let { "$it: " } ?: ""}$text",
                color = color,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = pad),
            )
        }
    }
}
