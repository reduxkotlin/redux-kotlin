package org.reduxkotlin.bundle

import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.concurrent.ConcurrentStore
import org.reduxkotlin.concurrent.LogAndContinue
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.asConcurrent
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.routing.OnWrite
import org.reduxkotlin.routing.RoutingBuilder
import org.reduxkotlin.routing.createModelStore

/**
 * Builds a routed [ModelState] store (see `createModelStore`) adopted as a
 * [ConcurrentStore] (lock-free reads, caller-serialized writes). The optional
 * [enhancer] (e.g. `applyMiddleware(...)`) is applied to the routed store
 * before it is adopted.
 *
 * @param enhancer optional store enhancer forwarded to `createModelStore`.
 * @param notificationContext where subscriber callbacks run (default: inline on the dispatching thread).
 * @param onError isolates listener throwables (default: log and continue).
 * @param devChecks forwarded: throws on a wasteful structurally-equal write.
 * @param onWrite forwarded: observes effective model writes.
 * @param preloadedState optional restored/persisted models overlaid onto the declared defaults at
 *   construction; forwarded to createModelStore. Seeds the store synchronously so the first
 *   read/render already reflects restored state.
 * @param block registers models and handlers via the routing DSL.
 */
public fun createConcurrentModelStore(
    enhancer: StoreEnhancer<ModelState>? = null,
    notificationContext: NotificationContext = NotificationContext.Inline,
    onError: (Throwable) -> Unit = LogAndContinue,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    preloadedState: ModelState? = null,
    block: RoutingBuilder.() -> Unit,
): ConcurrentStore<ModelState> = createModelStore(
    enhancer = enhancer,
    devChecks = devChecks,
    onWrite = onWrite,
    preloadedState = preloadedState,
    block = block,
)
    .asConcurrent(notificationContext, onError)
