package org.reduxkotlin.sample.taskflow.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 4dp spacing scale from the hi-fi spec (spec-data.js -> `spacing`) plus the
 * M3 window-size-class breakpoints. The scale skips a few steps (no 9/11/13...)
 * to match the spec's named stops exactly.
 */
object Dimens {
    /** 0 dp. */
    val space0: Dp = 0.dp

    /** 4 dp — chip gap, inter-column gutter base. */
    val space1: Dp = 4.dp

    /** 8 dp — inter-column gutter, chip gap. */
    val space2: Dp = 8.dp

    /** 12 dp — card inner gap, list-row vertical padding. */
    val space3: Dp = 12.dp

    /** 16 dp — card padding, compact screen margin. */
    val space4: Dp = 16.dp

    /** 20 dp. */
    val space5: Dp = 20.dp

    /** 24 dp — expanded screen margin, section gap. */
    val space6: Dp = 24.dp

    /** 28 dp. */
    val space7: Dp = 28.dp

    /** 32 dp. */
    val space8: Dp = 32.dp

    /** 40 dp. */
    val space10: Dp = 40.dp

    /** 48 dp — min touch target, FAB size. */
    val space12: Dp = 48.dp

    /** 64 dp. */
    val space16: Dp = 64.dp

    /** Compact/medium window boundary: width < 600 dp is Compact. */
    val breakpointMedium: Dp = 600.dp

    /** Medium/expanded window boundary: width >= 840 dp is Expanded. */
    val breakpointExpanded: Dp = 840.dp
}
