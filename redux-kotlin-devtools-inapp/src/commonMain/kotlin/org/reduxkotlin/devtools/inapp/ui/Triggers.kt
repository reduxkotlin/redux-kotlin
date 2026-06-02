package org.reduxkotlin.devtools.inapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.inapp.theme.RkTokens
import kotlin.math.roundToInt

/** Floating, draggable bubble; tap (without drag) opens the drawer. */
@Composable
internal fun DevToolsBubble(badge: Int, onOpen: () -> Unit) {
    var offset by remember { mutableStateOf(IntOffset(40, 240)) }
    Box(
        Modifier
            .offset { offset }
            .size(56.dp)
            .clip(CircleShape)
            .background(RkTokens.InkSurfaceHigh)
            .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset = IntOffset((offset.x + drag.x).roundToInt(), (offset.y + drag.y).roundToInt())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (badge > 0) badge.toString() else "RK", color = RkTokens.InkOn)
    }
}

/** Right-edge tab; tap opens the drawer. */
@Composable
internal fun EdgeTab(onOpen: () -> Unit) {
    Box(
        Modifier
            .size(width = 22.dp, height = 96.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .background(RkTokens.gradient)
            .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = RkTokens.InkOn)
    }
}
