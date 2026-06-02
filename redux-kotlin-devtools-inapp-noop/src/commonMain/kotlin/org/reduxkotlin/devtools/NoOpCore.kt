package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.Middleware
import org.reduxkotlin.Reducer
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.combineReducers

/**
 * No-op mirror of core's [ValueSerializer] (release builds never call it).
 * Present so that integrators can reference this type in shared/main code
 * without a compile error when the release classpath swaps core for the no-op.
 */
public interface ValueSerializer {
    /** Serializes [value] to a [JsonElement]; returns a string primitive on failure. */
    public fun toJson(value: Any?): JsonElement
}

/** No-op mirror of core's [ToStringValueSerializer]. */
public object ToStringValueSerializer : ValueSerializer {
    override fun toJson(value: Any?): JsonElement =
        JsonPrimitive(runCatching { value?.toString() }.getOrNull() ?: "null")
}

/** No-op replacement of the core `DevToolsConfig` for release builds. All fields inert. */
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
    public val serializer: ValueSerializer? = null,
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
public fun <State> named(label: String, middleware: Middleware<State>): NamedMiddleware<State> =
    NamedMiddleware(middleware)

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
