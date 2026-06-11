// Mirrors core's Combinators.kt — file name kept identical so the JVM file facade
// (`CombinatorsKt`) matches the debug artifact's.
package org.reduxkotlin.devtools

import org.reduxkotlin.Middleware
import org.reduxkotlin.Reducer
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.combineReducers

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
