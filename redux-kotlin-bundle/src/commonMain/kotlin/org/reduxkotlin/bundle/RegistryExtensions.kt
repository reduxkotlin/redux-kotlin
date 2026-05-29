package org.reduxkotlin.bundle

import org.reduxkotlin.Store
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.registry.StoreKey
import org.reduxkotlin.registry.StoreRegistry
import org.reduxkotlin.registry.TypedStoreRegistry
import org.reduxkotlin.routing.OnWrite
import org.reduxkotlin.routing.RoutingBuilder

/**
 * Returns the routed thread-safe [Store] registered under [id], creating and
 * caching it on first access. Subsequent calls with the same [id] return the
 * same instance (concurrency-safe via the registry's atomic get-or-create).
 *
 * All parameters except [id] are forwarded verbatim to [createThreadSafeModelStore]
 * and are ignored on cache hits.
 */
public fun <K : Any> StoreRegistry<K, ModelState>.getOrCreateThreadSafeModelStore(
    id: K,
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> = getOrCreate(id) { createThreadSafeModelStore(enhancer, devChecks, onWrite, block) }

/**
 * [TypedStoreRegistry] variant — returns the routed thread-safe [Store]
 * registered under [key], creating and caching it on first access.
 *
 * Build a [key] with `storeKey<ModelState>(id)` from `org.reduxkotlin.registry`.
 * All parameters except [key] are forwarded verbatim to [createThreadSafeModelStore]
 * and are ignored on cache hits.
 */
public fun <K : Any> TypedStoreRegistry.getOrCreateThreadSafeModelStore(
    key: StoreKey<K, ModelState>,
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> = getOrCreate(key) { createThreadSafeModelStore(enhancer, devChecks, onWrite, block) }
