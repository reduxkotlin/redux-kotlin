package org.reduxkotlin.sample.taskflow.store

import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.sample.taskflow.action.AccountLoggedIn
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel
import org.reduxkotlin.sample.taskflow.core.Theme
import org.reduxkotlin.sample.taskflow.feature.settings.SetTheme
import org.reduxkotlin.sample.taskflow.model.AccountsModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStoreTest {
    private val summary = AccountSummary(
        id = AccountId("acct-1"),
        displayName = "Ada",
        email = "ada@example.com",
        avatarUrl = "https://example.com/ada.png",
    )

    @Test
    fun account_logged_in_routes_to_accounts_model() {
        val store = createAppStore(NotificationContext.Inline)

        store.dispatch(AccountLoggedIn(summary))

        val accounts = store.state.getModel<AccountsModel>()
        assertTrue(accounts.accounts.containsKey(summary.id), "AccountLoggedIn should add the summary")
        assertEquals(summary, accounts.accounts[summary.id])
        assertEquals(summary.id, accounts.activeAccountId, "AccountLoggedIn should set the active account id")
    }

    @Test
    fun set_theme_routes_to_app_settings_model() {
        val store = createAppStore(NotificationContext.Inline)

        store.dispatch(SetTheme(Theme.Dark))

        assertEquals(Theme.Dark, store.state.getModel<AppSettingsModel>().theme)
    }
}
