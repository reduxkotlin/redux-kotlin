package org.reduxkotlin.sample.taskflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Shape scale from the hi-fi spec (spec-data.js -> `shapes`). The five standard
// M3 corner sizes populate [TaskFlowShapes]; the two "Increased" Expressive
// sizes used by components live as named vals since M3 Shapes has no slot for
// them.

/** Standard M3 shape scale for TaskFlow (4 / 8 / 12 / 16 / 28 dp corners). */
val TaskFlowShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Expressive "Large Increased" (20 dp) — compact KanbanCard, nav-rail item. */
val ShapeLargeIncreased = RoundedCornerShape(20.dp)

/** Expressive "Extra Large Increased" (32 dp) — account switcher sheet. */
val ShapeExtraLargeIncreased = RoundedCornerShape(32.dp)
