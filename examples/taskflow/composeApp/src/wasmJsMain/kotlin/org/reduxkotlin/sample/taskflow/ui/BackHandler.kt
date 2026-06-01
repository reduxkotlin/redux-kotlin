package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.runtime.Composable

@Composable
public actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Browser back is delivered as a `popstate` window event; the sample doesn't wire it yet.
}

@Composable
public actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    // No predictive gesture on Wasm/web; the browser handles back at the document level.
}
