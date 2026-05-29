package org.reduxkotlin.sample.taskflow.model

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

// --- Root AppStore models ---

data class AccountsModel(
    val accounts: PersistentMap<AccountId, AccountSummary> = persistentMapOf(),
    val activeAccountId: AccountId? = null,
)

data class AccountSummary(val id: AccountId, val displayName: String, val email: String, val avatarUrl: String)

data class AppSettingsModel(
    val theme: Theme = Theme.System,
    val language: String = "en",
    val fakeService: FakeServiceConfig = FakeServiceConfig(),
)

enum class Theme { System, Light, Dark }

data class FakeServiceConfig(
    val latencyMinMs: Int = 300,
    val latencyMaxMs: Int = 800,
    val failureRate: Float = 0.10f, // 0f..1f
    val botEnabled: Boolean = true,
    val botIntervalMs: Int = 4_000,
    val online: Boolean = true, // connectivity toggle: when false, RemoteApi is unreachable
    val syncIntervalMs: Int = 10_000, // periodic Refresh tick (>=10s per cross-cutting rule F)
)

data class AuthFlowModel(val mode: AuthMode = AuthMode.Login, val inFlight: Boolean = false, val error: String? = null)

enum class AuthMode { Login, AddAccount }
