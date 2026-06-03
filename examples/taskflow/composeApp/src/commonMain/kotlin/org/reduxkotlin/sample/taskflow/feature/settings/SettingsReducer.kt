package org.reduxkotlin.sample.taskflow.feature.settings

import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel

/**
 * Pure root-store reducer for the [AppSettingsModel] slice (theme + fake-service config).
 *
 * Returns the same [model] instance unchanged for actions it does not handle.
 *
 * @param model the current app-settings slice.
 * @param action the dispatched action.
 * @return the next app-settings slice, or [model] unchanged when [action] is not handled.
 */
public fun appSettingsReducer(model: AppSettingsModel, action: Action): AppSettingsModel = when (action) {
    is SetTheme -> model.copy(theme = action.theme)

    is SetLatency -> model.copy(
        fakeService = model.fakeService.copy(latencyMinMs = action.minMs, latencyMaxMs = action.maxMs),
    )

    is SetFailureRate -> model.copy(fakeService = model.fakeService.copy(failureRate = action.rate))

    is SetBotEnabled -> model.copy(fakeService = model.fakeService.copy(botEnabled = action.enabled))

    is SetOnline -> model.copy(fakeService = model.fakeService.copy(online = action.online))

    else -> model
}
