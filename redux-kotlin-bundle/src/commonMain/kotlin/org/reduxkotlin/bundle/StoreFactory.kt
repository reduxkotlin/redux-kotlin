package org.reduxkotlin.bundle

import org.reduxkotlin.Store
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.routing.OnWrite
import org.reduxkotlin.routing.RoutingBuilder
import org.reduxkotlin.routing.createModelStore
import org.reduxkotlin.threadsafe.ThreadSafeStore

/**
 * Builds a routed [ModelState] store (see `createModelStore`) wrapped in a
 * thread-safe store for cross-thread access. The optional [enhancer]
 * (e.g. `applyMiddleware(...)`) is applied to the routed store before
 * wrapping, so middleware runs inside the synchronized dispatch.
 *
 * @param enhancer optional store enhancer forwarded to `createModelStore`.
 * @param devChecks forwarded: throws on a wasteful structurally-equal write.
 * @param onWrite forwarded: observes effective model writes.
 * @param block registers models and handlers via the routing DSL.
 */
public fun createThreadSafeModelStore(
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> = ThreadSafeStore(
    createModelStore(enhancer = enhancer, devChecks = devChecks, onWrite = onWrite, block = block),
)
