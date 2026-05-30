package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * A labelled M3 [Slider] for a live fake-backend knob (latency, failure rate, interval). Shows
 * the [label] (Body Medium) with an inset [valueLabel] (the formatted current value, Label-style)
 * to its right, over the slider track. When [isError] is set the active track and handle use the
 * error color (the spec's "failure %" variant).
 *
 * Pure presentational (Rule C): the value is hoisted ([value] + [onValueChange]); the component
 * writes nothing to a store. The screen turns [onValueChange] into a dispatch.
 *
 * @param label the knob's name (e.g. "Latency"); shown in Body Medium.
 * @param value the current slider value.
 * @param valueRange the inclusive range the slider spans.
 * @param onValueChange invoked with the new value as the handle is dragged.
 * @param valueLabel the formatted current value (e.g. "450 ms", "10%"); shown inset.
 * @param modifier the [Modifier] for this control.
 * @param steps the number of discrete steps between range ends (0 = continuous).
 * @param isError `true` renders the error-track failure variant.
 */
@Composable
public fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    isError: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = if (isError) {
        SliderDefaults.colors(
            thumbColor = scheme.error,
            activeTrackColor = scheme.error,
            activeTickColor = scheme.onError,
        )
    } else {
        SliderDefaults.colors()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.space1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (isError) scheme.error else scheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$label: $valueLabel" },
        )
    }
}
