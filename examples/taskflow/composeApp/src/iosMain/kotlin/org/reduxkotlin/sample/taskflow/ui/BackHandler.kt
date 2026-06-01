package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.runtime.Composable

@Composable
public actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS uses native edge-swipe / nav-bar back. Hook later if/when the sample wires a
    // UINavigationController-style back interaction.
}

@Composable
public actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    // No-op on iOS; the system handles its own edge-swipe back animation at the navigation level.
}
