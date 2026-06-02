package org.reduxkotlin.devtools

import org.reduxkotlin.Store
import org.reduxkotlin.StoreCreator
import org.reduxkotlin.StoreEnhancer

/**
 * The one DevTools store enhancer. Records actions and resulting state into a [DevToolsSession] in
 * the [DevToolsHub] and publishes them; it owns no transport. Outputs (in-app drawer, remote WS)
 * subscribe to the session feed. All instrumentation is wrapped so it can never break the host store.
 * Pass [config] to control the store name, filters, and serializer.
 */
public fun <State> devTools(config: DevToolsConfig = DevToolsConfig()): StoreEnhancer<State> =
    { storeCreator: StoreCreator<State> ->
        { reducer, initialState, enhancer ->
            val store: Store<State> = storeCreator(reducer, initialState, enhancer)
            val session = runCatching { DevToolsHub.createSession(config) }
                .onFailure { config.logger("devtools: init failed: ${it.message}") }
                .getOrNull()

            if (session != null) {
                runCatching { session.init(store.getState()) }
                    .onFailure { config.logger("devtools: init-state failed: ${it.message}") }

                val origDispatch = store.dispatch
                store.dispatch = { action ->
                    val result = origDispatch(action)
                    @Suppress("TooGenericExceptionCaught") // devtools must never break the host store
                    try {
                        session.record(action, store.getState())
                    } catch (t: Throwable) {
                        config.logger("devtools: record failed: ${t.message}")
                    }
                    result
                }
            }
            store
        }
    }
