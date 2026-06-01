package org.reduxkotlin.sample.taskflow.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.compose.BackHandler as ActivityBackHandler

@Composable
public actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    ActivityBackHandler(enabled, onBack)
}

@Composable
public actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    PredictiveBackHandler(enabled) { progress ->
        try {
            progress.collect { event -> onProgress(event.progress) }
            onBack()
        } catch (cancellation: CancellationException) {
            onCancel()
            throw cancellation
        }
    }
}
