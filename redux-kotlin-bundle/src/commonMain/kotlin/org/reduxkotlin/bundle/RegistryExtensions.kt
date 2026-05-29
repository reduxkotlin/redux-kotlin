package org.reduxkotlin.bundle

import org.reduxkotlin.Store
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.concurrent.LogAndContinue
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.registry.StoreKey
import org.reduxkotlin.registry.StoreRegistry
import org.reduxkotlin.registry.TypedStoreRegistry
import org.reduxkotlin.routing.OnWrite
import org.reduxkotlin.routing.RoutingBuilder

/**
 * Returns the routed concurrent [Store] registered under [id], creating and
 * caching it on first access (concurrency-safe via the registry's atomic
 * get-or-create). Parameters other than [id] are forwarded to
 * [createConcurrentModelStore] and ignored on cache hits.
 */
public fun <K : Any> StoreRegistry<K, ModelState>.getOrCreateConcurrentModelStore(
    id: K,
    enhancer: StoreEnhancer<ModelState>? = null,
    notificationContext: NotificationContext = NotificationContext.Inline,
    onError: (Throwable) -> Unit = LogAndContinue,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> =
    getOrCreate(id) { createConcurrentModelStore(enhancer, notificationContext, onError, devChecks, onWrite, block) }

/**
 * [TypedStoreRegistry] variant keyed by a typed [StoreKey]
 * (build one with `storeKey<ModelState>(id)`). Parameters other than [key]
 * are forwarded to [createConcurrentModelStore] and ignored on cache hits.
 */
public fun <K : Any> TypedStoreRegistry.getOrCreateConcurrentModelStore(
    key: StoreKey<K, ModelState>,
    enhancer: StoreEnhancer<ModelState>? = null,
    notificationContext: NotificationContext = NotificationContext.Inline,
    onError: (Throwable) -> Unit = LogAndContinue,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> =
    getOrCreate(key) { createConcurrentModelStore(enhancer, notificationContext, onError, devChecks, onWrite, block) }
