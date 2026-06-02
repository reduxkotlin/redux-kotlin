package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable
import org.reduxkotlin.Middleware
import org.reduxkotlin.Reducer
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.combineReducers

/** No-op replacement of `DevToolsConfig` for release builds. All fields inert. */
public data class DevToolsConfig(
    /** Inert. */
    public val name: String = "redux-kotlin",
    /** Inert. */
    public val instanceId: String? = null,
    /** Inert. */
    public val maxAge: Int = 50,
    /** Inert. */
    public val allowlist: List<String> = emptyList(),
    /** Inert. */
    public val denylist: List<String> = emptyList(),
    /** Inert. */
    public val logger: (String) -> Unit = {},
)

/** No-op enhancer: returns an identity store enhancer (no recording, no hub). */
@Suppress("UnusedParameter")
public fun <State> devTools(config: DevToolsConfig = DevToolsConfig()): StoreEnhancer<State> =
    { storeCreator -> storeCreator }

/** No-op labeled middleware. */
public class NamedMiddleware<State> internal constructor(internal val middleware: Middleware<State>)

/** No-op labeled reducer. */
public class NamedReducer<State> internal constructor(internal val reducer: Reducer<State>)

/** Labels a middleware (label ignored in release). */
@Suppress("UnusedParameter")
public fun <State> named(label: String, middleware: Middleware<State>): NamedMiddleware<State> = NamedMiddleware(
    middleware,
)

/** Labels a reducer (label ignored in release). */
@Suppress("UnusedParameter")
public fun <State> named(label: String, reducer: Reducer<State>): NamedReducer<State> = NamedReducer(reducer)

/** No-op: behaves exactly like `applyMiddleware` with no instrumentation. */
@Suppress("UnusedParameter")
public fun <State> devToolsMiddleware(
    config: DevToolsConfig,
    vararg middlewares: NamedMiddleware<State>,
): StoreEnhancer<State> = applyMiddleware(*middlewares.map { it.middleware }.toTypedArray())

/** No-op: behaves exactly like `combineReducers` with no instrumentation. */
@Suppress("UnusedParameter")
public fun <State> devToolsCombineReducers(
    config: DevToolsConfig,
    vararg reducers: NamedReducer<State>,
): Reducer<State> = combineReducers(*reducers.map { it.reducer }.toTypedArray())

/** No-op trigger enum (kept for API parity). */
public enum class DevToolsTrigger {
    /** Inert. */
    BUBBLE,

    /** Inert. */
    EDGE_SWIPE,
}

/** No-op theme mode (kept for API parity). */
public enum class DevToolsThemeMode {
    /** Inert. */
    SYSTEM,

    /** Inert. */
    DARK,

    /** Inert. */
    LIGHT,
}

/** No-op start tab (kept for API parity). */
public enum class DevToolsTab {
    /** Inert. */
    ACTIONS,

    /** Inert. */
    STATE,

    /** Inert. */
    DIFF,

    /** Inert. */
    PIPELINE,

    /** Inert. */
    OUTPUTS,
}

/** No-op replacement of `InAppConfig`. */
public data class InAppConfig(
    /** Inert. */
    public val triggers: Set<DevToolsTrigger> = setOf(DevToolsTrigger.BUBBLE, DevToolsTrigger.EDGE_SWIPE),
    /** Inert. */
    public val startTab: DevToolsTab = DevToolsTab.ACTIONS,
    /** Inert. */
    public val theme: DevToolsThemeMode = DevToolsThemeMode.DARK,
    /** Inert. */
    public val instanceId: String? = null,
)

/** No-op programmatic control (does nothing in release). */
public object ReduxDevTools {
    /** No-op. */
    public fun open() { /* no-op */ }

    /** No-op. */
    public fun close() { /* no-op */ }
}

/** No-op host: renders [content] directly, with no overlay, no hub, no Compose-material3. */
@Suppress("UnusedParameter")
@Composable
public fun ReduxDevToolsHost(config: InAppConfig = InAppConfig(), content: @Composable () -> Unit) {
    content()
}
