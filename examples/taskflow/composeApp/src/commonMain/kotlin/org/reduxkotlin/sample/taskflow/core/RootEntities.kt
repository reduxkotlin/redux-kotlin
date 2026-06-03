package org.reduxkotlin.sample.taskflow.core

/** Summary of an account as shown in the account switcher and board list. */
public data class AccountSummary(val id: AccountId, val displayName: String, val email: String, val avatarUrl: String)

/** App-wide settings stored in local state. */
public data class AppSettingsModel(
    val theme: Theme = Theme.System,
    val language: String = "en",
    val fakeService: FakeServiceConfig = FakeServiceConfig(),
)

/** UI color-scheme preference. */
public enum class Theme { System, Light, Dark }

/** Configuration for the fake (in-process) remote service. */
public data class FakeServiceConfig(
    val latencyMinMs: Int = 300,
    val latencyMaxMs: Int = 800,
    val failureRate: Float = 0.10f, // 0f..1f
    val botEnabled: Boolean = true,
    val botIntervalMs: Int = 4_000,
    val online: Boolean = true, // connectivity toggle: when false, RemoteApi is unreachable
    val syncIntervalMs: Int = 10_000, // periodic Refresh tick (>=10s per cross-cutting rule F)
)
