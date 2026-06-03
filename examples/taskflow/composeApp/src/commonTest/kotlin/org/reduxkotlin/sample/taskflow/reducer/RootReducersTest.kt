package org.reduxkotlin.sample.taskflow.reducer

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.action.AccountLoggedIn
import org.reduxkotlin.sample.taskflow.action.EditProfile
import org.reduxkotlin.sample.taskflow.action.LoadAccountsSucceeded
import org.reduxkotlin.sample.taskflow.action.LoginFailed
import org.reduxkotlin.sample.taskflow.action.LoginRequested
import org.reduxkotlin.sample.taskflow.action.LogoutAccount
import org.reduxkotlin.sample.taskflow.action.Refresh
import org.reduxkotlin.sample.taskflow.action.StartLogin
import org.reduxkotlin.sample.taskflow.action.SwitchAccount
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel
import org.reduxkotlin.sample.taskflow.core.Theme
import org.reduxkotlin.sample.taskflow.feature.settings.SetBotEnabled
import org.reduxkotlin.sample.taskflow.feature.settings.SetFailureRate
import org.reduxkotlin.sample.taskflow.feature.settings.SetLatency
import org.reduxkotlin.sample.taskflow.feature.settings.SetOnline
import org.reduxkotlin.sample.taskflow.feature.settings.SetTheme
import org.reduxkotlin.sample.taskflow.feature.settings.appSettingsReducer
import org.reduxkotlin.sample.taskflow.model.AccountsModel
import org.reduxkotlin.sample.taskflow.model.AuthFlowModel
import org.reduxkotlin.sample.taskflow.model.AuthMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RootReducersTest {
    private fun summary(id: String, name: String = "n", email: String = "e", avatar: String = "a") =
        AccountSummary(AccountId(id), name, email, avatar)

    // --- accountsReducer ---

    @Test
    fun accountLoggedInPutsSummaryAndSetsActive() {
        val s = summary("ann")
        val next = accountsReducer(AccountsModel(), AccountLoggedIn(s))
        assertEquals(s, next.accounts[AccountId("ann")])
        assertEquals(AccountId("ann"), next.activeAccountId)
    }

    @Test
    fun switchAccountSetsActiveOnly() {
        val a = summary("ann")
        val b = summary("bob")
        val start = AccountsModel(persistentMapOf(a.id to a, b.id to b), a.id)
        val next = accountsReducer(start, SwitchAccount(b.id))
        assertEquals(b.id, next.activeAccountId)
        assertEquals(start.accounts, next.accounts)
    }

    @Test
    fun logoutRemovesAccountAndKeepsActiveWhenNotActive() {
        val a = summary("ann")
        val b = summary("bob")
        val start = AccountsModel(persistentMapOf(a.id to a, b.id to b), a.id)
        val next = accountsReducer(start, LogoutAccount(b.id))
        assertNull(next.accounts[b.id])
        assertEquals(a.id, next.activeAccountId)
    }

    @Test
    fun logoutActiveAccountReassignsActiveToRemaining() {
        val a = summary("ann")
        val b = summary("bob")
        val start = AccountsModel(persistentMapOf(a.id to a, b.id to b), a.id)
        val next = accountsReducer(start, LogoutAccount(a.id))
        assertNull(next.accounts[a.id])
        assertEquals(b.id, next.activeAccountId)
    }

    @Test
    fun logoutLastAccountSetsActiveNull() {
        val a = summary("ann")
        val start = AccountsModel(persistentMapOf(a.id to a), a.id)
        val next = accountsReducer(start, LogoutAccount(a.id))
        assertTrue(next.accounts.isEmpty())
        assertNull(next.activeAccountId)
    }

    @Test
    fun loadAccountsSucceededSetsAccountsAndActive() {
        val a = summary("ann")
        val b = summary("bob")
        val next = accountsReducer(
            AccountsModel(),
            LoadAccountsSucceeded(persistentListOf(a, b), b.id),
        )
        assertEquals(a, next.accounts[a.id])
        assertEquals(b, next.accounts[b.id])
        assertEquals(b.id, next.activeAccountId)
    }

    @Test
    fun editProfileUpdatesActiveAccountSummary() {
        val a = summary("ann", "Ann", "ann@x", "av")
        val start = AccountsModel(persistentMapOf(a.id to a), a.id)
        val next = accountsReducer(start, EditProfile("Annie", "annie@x", "av2", "bio"))
        val updated = next.accounts[a.id]!!
        assertEquals("Annie", updated.displayName)
        assertEquals("annie@x", updated.email)
        assertEquals("av2", updated.avatarUrl)
    }

    @Test
    fun editProfileNoopWhenActiveNull() {
        val start = AccountsModel()
        val next = accountsReducer(start, EditProfile("x", "y", "z", null))
        assertSame(start, next)
    }

    @Test
    fun editProfileNoopWhenActiveAbsent() {
        val start = AccountsModel(persistentMapOf(), AccountId("ghost"))
        val next = accountsReducer(start, EditProfile("x", "y", "z", null))
        assertSame(start, next)
    }

    @Test
    fun accountsReducerReturnsSameInstanceForUnhandled() {
        val start = AccountsModel(persistentMapOf(summary("a").let { it.id to it }))
        assertSame(start, accountsReducer(start, Refresh))
    }

    // --- appSettingsReducer ---

    @Test
    fun setThemeUpdatesTheme() {
        val next = appSettingsReducer(AppSettingsModel(), SetTheme(Theme.Dark))
        assertEquals(Theme.Dark, next.theme)
    }

    @Test
    fun setLatencyUpdatesFakeService() {
        val next = appSettingsReducer(AppSettingsModel(), SetLatency(10, 20))
        assertEquals(10, next.fakeService.latencyMinMs)
        assertEquals(20, next.fakeService.latencyMaxMs)
    }

    @Test
    fun setFailureRateUpdatesFakeService() {
        val next = appSettingsReducer(AppSettingsModel(), SetFailureRate(0.5f))
        assertEquals(0.5f, next.fakeService.failureRate)
    }

    @Test
    fun setBotEnabledUpdatesFakeService() {
        val next = appSettingsReducer(AppSettingsModel(), SetBotEnabled(false))
        assertEquals(false, next.fakeService.botEnabled)
    }

    @Test
    fun setOnlineUpdatesFakeService() {
        val next = appSettingsReducer(AppSettingsModel(), SetOnline(false))
        assertEquals(false, next.fakeService.online)
    }

    @Test
    fun appSettingsReducerReturnsSameInstanceForUnhandled() {
        val start = AppSettingsModel()
        assertSame(start, appSettingsReducer(start, Refresh))
    }

    // --- authFlowReducer ---

    @Test
    fun startLoginSetsModeClearsError() {
        val start = AuthFlowModel(mode = AuthMode.Login, error = "boom")
        val next = authFlowReducer(start, StartLogin(AuthMode.AddAccount))
        assertEquals(AuthMode.AddAccount, next.mode)
        assertNull(next.error)
    }

    @Test
    fun loginRequestedSetsInFlightClearsError() {
        val start = AuthFlowModel(error = "boom")
        val next = authFlowReducer(start, LoginRequested)
        assertTrue(next.inFlight)
        assertNull(next.error)
    }

    @Test
    fun accountLoggedInClearsInFlightAndError() {
        val start = AuthFlowModel(inFlight = true, error = "boom")
        val next = authFlowReducer(start, AccountLoggedIn(summary("ann")))
        assertEquals(false, next.inFlight)
        assertNull(next.error)
    }

    @Test
    fun loginFailedSetsErrorClearsInFlight() {
        val start = AuthFlowModel(inFlight = true)
        val next = authFlowReducer(start, LoginFailed("nope"))
        assertEquals(false, next.inFlight)
        assertEquals("nope", next.error)
    }

    @Test
    fun authFlowReducerReturnsSameInstanceForUnhandled() {
        val start = AuthFlowModel()
        assertSame(start, authFlowReducer(start, Refresh))
    }
}
