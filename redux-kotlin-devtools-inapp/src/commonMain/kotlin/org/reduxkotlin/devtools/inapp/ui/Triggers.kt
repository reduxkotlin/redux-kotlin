package org.reduxkotlin.devtools.inapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/** Leftward drag distance on the edge tab that counts as an open-the-drawer swipe. */
private val EdgeSwipeThreshold = 24.dp

/**
 * Floating, draggable bubble; tap (without drag) opens the drawer. Position state is hoisted
 * ([offset]/[onDrag]) so it survives the bubble leaving composition while the drawer is open.
 */
@Composable
internal fun DevToolsBubble(badge: Int, offset: IntOffset, onDrag: (Offset) -> Unit, onOpen: () -> Unit) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnOpen by rememberUpdatedState(onOpen)
    Box(
        Modifier
            .offset { offset }
            .size(56.dp)
            .clip(CircleShape)
            .background(RkTokens.InkSurfaceHigh)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnOpen() }) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    currentOnDrag(drag)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (badge > 0) badge.toString() else "RK", color = RkTokens.InkOn)
    }
}

/** Right-edge tab; tap or leftward swipe (past [EdgeSwipeThreshold]) opens the drawer. */
@Composable
internal fun EdgeTab(onOpen: () -> Unit) {
    val currentOnOpen by rememberUpdatedState(onOpen)
    Box(
        Modifier
            .size(width = 22.dp, height = 96.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .background(RkTokens.gradient)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnOpen() }) }
            .pointerInput(Unit) {
                val threshold = EdgeSwipeThreshold.toPx()
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = { if (dragX <= -threshold) currentOnOpen() },
                ) { change, dragAmount ->
                    change.consume()
                    dragX += dragAmount
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = RkTokens.InkOn)
    }
}
