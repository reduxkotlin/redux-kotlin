package org.reduxkotlin.sample.taskflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.SetBotEnabled
import org.reduxkotlin.sample.taskflow.action.SetFailureRate
import org.reduxkotlin.sample.taskflow.action.SetLatency
import org.reduxkotlin.sample.taskflow.action.SetOnline
import org.reduxkotlin.sample.taskflow.action.SetTheme
import org.reduxkotlin.sample.taskflow.model.AppSettingsModel
import org.reduxkotlin.sample.taskflow.model.FakeServiceConfig
import org.reduxkotlin.sample.taskflow.model.Theme
import org.reduxkotlin.sample.taskflow.ui.components.SettingsSlider
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The settings screen (Screen 7): the live demo console for the app. It has two sections —
 * APPEARANCE (the app-wide [Theme] choice) and FAKE BACKEND · DEMO KNOBS (the dials that the fake
 * service reads live: request latency, failure rate, the bot collaborator, and the connectivity
 * toggle). Cranking latency or failure surfaces the loading / error / undo paths on demand; flipping
 * the offline switch, editing the board, then flipping back online drains the sync queue — the
 * offline-first showcase.
 *
 * Binding discipline (Rule C): the two state slices are read through the smallest possible store
 * slices — [AppSettingsModel.theme] and [AppSettingsModel.fakeService] — each via its own
 * [fieldStateOf] over the stable [rootStore], so the theme row and the knob rows recompose
 * independently. Every control is fully hoisted: it renders the bound value and turns each
 * interaction straight into a dispatch on [rootStore]. There is no local editor state and no
 * business logic in the screen — the reducers own the writes, and the fake-service middleware reads
 * the config live.
 *
 * The screen body is a single centered column capped at [CONTENT_MAX_WIDTH] so the Expanded / Web
 * layout reads as one centered settings column (per the redline) while Compact fills the width.
 *
 * @param rootStore the root app store holding [AppSettingsModel] (theme + fake-service config).
 * @param modifier the [Modifier] for the screen root.
 */
@Composable
public fun SettingsScreen(rootStore: Store<ModelState>, modifier: Modifier = Modifier) {
    val r = rememberStableStore(rootStore).value
    val theme by r.fieldStateOf(AppSettingsModel::class) { it.theme }
    val cfg by r.fieldStateOf(AppSettingsModel::class) { it.fakeService }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = CONTENT_MAX_WIDTH)
                .padding(horizontal = Dimens.space4, vertical = Dimens.space6),
            verticalArrangement = Arrangement.spacedBy(Dimens.space4),
        ) {
            SectionHeader("APPEARANCE")
            ThemeRow(theme = theme, onTheme = { r.dispatch(SetTheme(it)) })

            SectionHeader("FAKE BACKEND · DEMO KNOBS")
            FakeBackendSection(
                cfg = cfg,
                onLatency = { max -> r.dispatch(SetLatency(cfg.latencyMinMs, max)) },
                onFailureRate = { rate -> r.dispatch(SetFailureRate(rate)) },
                onBotEnabled = { enabled -> r.dispatch(SetBotEnabled(enabled)) },
                onOnline = { online -> r.dispatch(SetOnline(online)) },
            )
        }
    }
}

/** A Label-Small section heading (e.g. "APPEARANCE") in `onSurfaceVariant`, per the redline's section labels. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.space2),
    )
}

/**
 * The theme picker row: a "Theme" label over a [SingleChoiceSegmentedButtonRow] of System / Light /
 * Dark. Each segment reports the chosen [Theme] up via [onTheme]; the screen turns it into a
 * [SetTheme] dispatch that drives the app-wide color scheme.
 */
@Composable
private fun ThemeRow(theme: Theme, onTheme: (Theme) -> Unit) {
    SettingRow(label = "Theme") {
        val options = listOf(Theme.System, Theme.Light, Theme.Dark)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = theme == option,
                    onClick = { onTheme(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(option.name)
                }
            }
        }
    }
}

/**
 * The fake-backend knob block: the latency [SettingsSlider] (max bound, min held at the configured
 * floor), the failure-rate [SettingsSlider] (error-track styling), the bot-collaborator [Switch],
 * and the online / offline [Switch]. Each is fully hoisted; the screen turns each callback into a
 * dispatch.
 */
@Composable
private fun FakeBackendSection(
    cfg: FakeServiceConfig,
    onLatency: (Int) -> Unit,
    onFailureRate: (Float) -> Unit,
    onBotEnabled: (Boolean) -> Unit,
    onOnline: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space4)) {
        SettingsSlider(
            label = "Latency",
            value = cfg.latencyMaxMs.toFloat(),
            valueRange = LATENCY_RANGE,
            onValueChange = { onLatency(it.toInt()) },
            valueLabel = "${cfg.latencyMinMs}–${cfg.latencyMaxMs} ms",
        )
        SettingsSlider(
            label = "Failure rate",
            value = cfg.failureRate,
            valueRange = 0f..1f,
            onValueChange = onFailureRate,
            valueLabel = "${(cfg.failureRate * PERCENT).toInt()}%",
            isError = true,
        )
        SwitchRow(
            label = "Bot collaborator",
            checked = cfg.botEnabled,
            onCheckedChange = onBotEnabled,
        )
        SwitchRow(
            label = if (cfg.online) "Online" else "Offline",
            checked = cfg.online,
            onCheckedChange = onOnline,
        )
    }
}

/**
 * A labelled control row: the [label] (Body Large) on a line of its own, with the hoisted [content]
 * (a segmented row, slider, etc.) below it. Used for controls that need their own line.
 */
@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

/**
 * A labelled [Switch] row: the [label] (Body Large) on the leading edge and the [Switch] trailing,
 * wrapped in a `surfaceContainerLowest` card so the toggle reads as a settled control. The toggle is
 * fully hoisted via [checked] / [onCheckedChange].
 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = Dimens.space1,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.space4, vertical = Dimens.space2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** The latency slider range in ms: 0 up to a 2-second ceiling so the loading path is easy to provoke. */
private val LATENCY_RANGE = 0f..2000f

/** Multiplier for rendering a 0f..1f failure rate as a whole percentage. */
private const val PERCENT = 100

/** The Expanded / Web settings column cap so the content reads as one centered column per the redline. */
private val CONTENT_MAX_WIDTH = 640.dp
