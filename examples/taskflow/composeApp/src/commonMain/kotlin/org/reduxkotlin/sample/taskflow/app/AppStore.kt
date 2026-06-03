package org.reduxkotlin.sample.taskflow.app

import org.reduxkotlin.Store
import org.reduxkotlin.bundle.createConcurrentModelStore
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.bridge.BridgeConfig
import org.reduxkotlin.devtools.bridge.BridgeOutput
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel
import org.reduxkotlin.sample.taskflow.feature.account.AccountLoggedIn
import org.reduxkotlin.sample.taskflow.feature.account.AccountsModel
import org.reduxkotlin.sample.taskflow.feature.account.AuthFlowModel
import org.reduxkotlin.sample.taskflow.feature.account.EditProfile
import org.reduxkotlin.sample.taskflow.feature.account.LoadAccountsSucceeded
import org.reduxkotlin.sample.taskflow.feature.account.LoginFailed
import org.reduxkotlin.sample.taskflow.feature.account.LoginRequested
import org.reduxkotlin.sample.taskflow.feature.account.LogoutAccount
import org.reduxkotlin.sample.taskflow.feature.account.StartLogin
import org.reduxkotlin.sample.taskflow.feature.account.SwitchAccount
import org.reduxkotlin.sample.taskflow.feature.account.accountsReducer
import org.reduxkotlin.sample.taskflow.feature.account.authFlowReducer
import org.reduxkotlin.sample.taskflow.feature.settings.SetBotEnabled
import org.reduxkotlin.sample.taskflow.feature.settings.SetFailureRate
import org.reduxkotlin.sample.taskflow.feature.settings.SetLatency
import org.reduxkotlin.sample.taskflow.feature.settings.SetOnline
import org.reduxkotlin.sample.taskflow.feature.settings.SetTheme
import org.reduxkotlin.sample.taskflow.feature.settings.appSettingsReducer
import org.reduxkotlin.sample.taskflow.infra.platform.mainNotificationContext

/**
 * Builds the root application store: a concurrent [ModelState] store holding the account directory
 * ([AccountsModel]), app settings ([AppSettingsModel]) and login/add-account flow ([AuthFlowModel]).
 *
 * Each model slot routes its handled actions to the matching pure reducer; actions a slot does not
 * handle are not registered on it (and unhandled handlers return the model unchanged). The
 * [notificationContext] decides where subscriber callbacks run — defaulting to the platform main
 * thread so Compose state reads stay on-main even when dispatches originate from background effects.
 *
 * @param notificationContext where subscriber callbacks are invoked (default: platform main thread).
 * @return the root [Store] over [ModelState].
 */
public fun createAppStore(notificationContext: NotificationContext = mainNotificationContext()): Store<ModelState> {
    val rootCfg = DevToolsConfig(name = "TaskFlow-root")
    val store = createConcurrentModelStore(
        notificationContext = notificationContext,
        enhancer = devTools(rootCfg),
    ) {
        model(AccountsModel()) {
            on<AccountLoggedIn> { s, a -> accountsReducer(s, a) }
            on<SwitchAccount> { s, a -> accountsReducer(s, a) }
            on<LogoutAccount> { s, a -> accountsReducer(s, a) }
            on<LoadAccountsSucceeded> { s, a -> accountsReducer(s, a) }
            on<EditProfile> { s, a -> accountsReducer(s, a) }
        }
        model(AppSettingsModel()) {
            on<SetTheme> { s, a -> appSettingsReducer(s, a) }
            on<SetLatency> { s, a -> appSettingsReducer(s, a) }
            on<SetFailureRate> { s, a -> appSettingsReducer(s, a) }
            on<SetBotEnabled> { s, a -> appSettingsReducer(s, a) }
            on<SetOnline> { s, a -> appSettingsReducer(s, a) }
        }
        model(AuthFlowModel()) {
            on<StartLogin> { s, a -> authFlowReducer(s, a) }
            on<LoginRequested> { s, _ -> authFlowReducer(s, LoginRequested) }
            on<AccountLoggedIn> { s, a -> authFlowReducer(s, a) }
            on<LoginFailed> { s, a -> authFlowReducer(s, a) }
        }
    }
    DevToolsHub.session(rootCfg.instanceId ?: rootCfg.name)?.let { session ->
        BridgeOutput(BridgeConfig(clientId = "taskflow", clientLabel = "TaskFlow")).start(session)
    }
    return store
}
