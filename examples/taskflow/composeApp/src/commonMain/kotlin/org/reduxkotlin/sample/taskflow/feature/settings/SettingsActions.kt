package org.reduxkotlin.sample.taskflow.feature.settings

import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.Theme

// --- settings actions (root) ---

/** Sets the app-wide [Theme] (System / Light / Dark). */
public data class SetTheme(val theme: Theme) : Action

/**
 * Updates the fake-service latency band.
 *
 * @param minMs the lower bound of the simulated latency range in milliseconds.
 * @param maxMs the upper bound of the simulated latency range in milliseconds.
 */
public data class SetLatency(val minMs: Int, val maxMs: Int) : Action

/**
 * Updates the fake-service failure rate.
 *
 * @param rate the probability of a simulated failure in the range `0f..1f`.
 */
public data class SetFailureRate(val rate: Float) : Action

/**
 * Enables or disables the bot collaborator on the fake service.
 *
 * @param enabled `true` to activate the bot; `false` to stop it.
 */
public data class SetBotEnabled(val enabled: Boolean) : Action

/**
 * Writes [AppSettingsModel.fakeService.online][org.reduxkotlin.sample.taskflow.core.AppSettingsModel]
 * (root) — toggling fake connectivity to trigger offline-first sync paths.
 *
 * @param online `true` = online; `false` = offline.
 */
public data class SetOnline(val online: Boolean) : Action
