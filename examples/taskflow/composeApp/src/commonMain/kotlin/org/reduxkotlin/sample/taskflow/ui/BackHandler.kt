package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.runtime.Composable

/**
 * Multiplatform system-back hook for the nav stack.
 *
 * Wired to the activity back-press dispatcher on Android (via `androidx.activity.compose`);
 * no-op on JVM (desktop), iOS, and Wasm targets — those hosts have native back gestures
 * (Esc key, edge-swipe, browser back) that the taskflow sample does not yet intercept. The
 * Redux-side `Back` action is the canonical pop primitive and remains available from any
 * platform via in-app UI (e.g. the CardDetail "Close" button).
 *
 * @param enabled when `false` the handler does not intercept system back — the host's
 *   default behaviour (e.g. exiting or backgrounding the app) takes over.
 * @param onBack invoked on each back event while [enabled] is `true`.
 */
@Composable
public expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Predictive-back handler that exposes gesture progress as it happens.
 *
 * On Android 14+ (and Android 13 with the dev opt-in) and with
 * `android:enableOnBackInvokedCallback="true"` on the host activity, swiping from the screen edge
 * drives [onProgress] with values in `0f..1f` while the gesture is in flight; releasing past the
 * commit threshold ends the flow and triggers [onBack]; releasing short of it throws cancellation
 * and triggers [onCancel] (the caller typically animates progress back to `0f` then).
 *
 * On JVM, iOS, and Wasm there is no system predictive back to hook; the actual is a no-op so the
 * caller can wire this handler unconditionally and pair it with a [BackHandler] for terminal back.
 */
@Composable
public expect fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
)
