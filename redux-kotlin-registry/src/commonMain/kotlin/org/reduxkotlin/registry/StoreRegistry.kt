package org.reduxkotlin.registry

import org.reduxkotlin.Store

/**
 * A thread-safe registry of [Store] instances keyed by an identifier of type [K].
 *
 * The registry is intended for use cases where multiple stores of the same
 * `State` type ([S]) need to coexist under disjoint scopes — for example a
 * per-thread-view store in a messaging app, or a per-call store in a calling
 * app. Two different state types call for two different registries (or use
 * [TypedStoreRegistry] for one bag of heterogeneous stores).
 *
 * Lifecycle is fully manual: call [remove] when a scope ends, or [clear] to
 * wipe everything (e.g. on logout). The registry never auto-evicts.
 *
 * Concurrency
 * -----------
 *
 *  - [get] and the fast path of [getOrCreate] are **lock-free**: a single
 *    atomic load on the underlying snapshot.
 *  - [getOrCreate] guarantees its `creator` lambda is invoked **at most once
 *    per [id] across all concurrent callers** — under contention, losers
 *    observe the winner's store and return it.
 *  - [remove] and [clear] take a brief internal lock.
 *  - Membership-change listeners are dispatched **synchronously** on the
 *    mutating thread, under the same internal lock, so the global event order
 *    matches the global mutation order. Listeners must complete quickly and
 *    **must not call back into mutating methods** on this registry (deadlock);
 *    reads from a listener are safe.
 *
 * Singleton pattern
 * -----------------
 *
 * The class is intentionally not a Kotlin `object` (testability). Users who
 * want a process-global registry hold a top-level `val`:
 *
 * ```kotlin
 * val threadStores = StoreRegistry<ThreadId, ThreadState>()
 * ```
 */
public class StoreRegistry<K : Any, S> {

    private val core = RegistryCore<K, Store<S>>()

    /** Number of entries currently in the registry. */
    public val size: Int get() = core.size

    /** `true` iff [size] is zero. */
    public val isEmpty: Boolean get() = core.isEmpty

    /**
     * Lock-free lookup. Returns `null` if no store has been registered for [id]
     * (or if it has been removed). Does **not** invoke any creator.
     */
    public fun get(id: K): Store<S>? = core.get(id)

    /**
     * Returns the existing store registered for [id], or atomically inserts and
     * returns a new one produced by [creator].
     *
     * `creator` is invoked at most once per [id] across all concurrent callers.
     * If `creator` throws, the exception propagates and the registry is left
     * unchanged.
     *
     * Fires [RegistryEvent.Added] only on actual creation; not on a hit.
     */
    public fun getOrCreate(id: K, creator: () -> Store<S>): Store<S> = core.getOrCreate(id, creator)

    /**
     * Evicts the entry for [id] and returns `true` if anything was removed.
     * Fires [RegistryEvent.Removed] only when the return value is `true`.
     *
     * Does **not** notify the store itself (no teardown action is dispatched).
     * Callers still holding a reference may continue to use the store; the
     * registry simply forgets it.
     */
    public fun remove(id: K): Boolean = core.remove(id)

    /**
     * Atomically evicts all entries. Fires [RegistryEvent.Removed] once per
     * evicted id, in unspecified order. By the time the first listener
     * invocation runs the registry is already empty (`size == 0`).
     */
    public fun clear(): Unit = core.clear()

    /**
     * Registers a synchronous listener. Returns a subscription that, when
     * invoked, unregisters. See the class KDoc for the listener invocation
     * contract.
     */
    public fun addListener(listener: (RegistryEvent<K>) -> Unit): RegistrySubscription = core.addListener { coreEvent ->
        listener(coreEvent.toPublic())
    }

    private fun RegistryCore.Event<K>.toPublic(): RegistryEvent<K> = when (this) {
        is RegistryCore.Event.Added -> RegistryEvent.Added(id)
        is RegistryCore.Event.Removed -> RegistryEvent.Removed(id)
    }
}
