package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.runtime.Composable

/**
 * Hook the host platform's status- and navigation-bar icon (foreground) appearance to whatever
 * Compose just decided about light vs dark theme. Edge-to-edge mode makes the system bars
 * transparent, so the system clock / battery / gesture pill render *on top of* whatever app
 * content sits behind them — and their contrast has to be picked from that content's colour,
 * not from `enableEdgeToEdge()`'s one-shot read at activity creation.
 *
 * Semantics intentionally match Android's `WindowInsetsControllerCompat`:
 *
 * - `lightStatusBarBackground = true`  → the area behind the status bar is a *light* colour,
 *   so the system should render **dark** foreground icons (i.e. `isAppearanceLightStatusBars = true`).
 * - `lightStatusBarBackground = false` → dark background → light icons.
 *
 * Android is the only target with a system-bar appearance API; on JVM (desktop), iOS, and Wasm
 * the actual is a no-op (those hosts either don't have system bars in the app's control, or
 * handle their contrast natively).
 */
@Composable
public expect fun ApplySystemBarAppearance(lightStatusBarBackground: Boolean, lightNavigationBarBackground: Boolean)
