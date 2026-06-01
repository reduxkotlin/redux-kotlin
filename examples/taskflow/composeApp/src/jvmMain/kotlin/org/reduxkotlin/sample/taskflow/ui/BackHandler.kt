package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.runtime.Composable

@Composable
public actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back on Desktop. Esc-as-back could be wired here via `Modifier.onPreviewKeyEvent`
    // on the window root if/when the sample wants it.
}

@Composable
public actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    // No predictive gesture on Desktop; the caller pairs this with [BackHandler] for terminal back.
}
