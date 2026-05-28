package org.reduxkotlin.registry

import org.reduxkotlin.Store

/**
 * Heterogeneous, type-safe registry. Holds [Store] instances of differing
 * `State` types, keyed by [StoreKey], which combines an arbitrary identifier
 * with a `KClass` type witness for the state.
 *
 * Use this only when one logical bag must contain stores of different state
 * types. For the common "one state type per registry" case, prefer the
 * simpler [StoreRegistry].
 *
 * Concurrency, lifecycle, and listener contract are identical to
 * [StoreRegistry] — see that class's KDoc for the details.
 */
public class TypedStoreRegistry {

    private val core = RegistryCore<StoreKey<*, *>, Store<*>>()

    /** Number of entries currently in the registry. */
    public val size: Int get() = core.size

    /** `true` iff [size] is zero. */
    public val isEmpty: Boolean get() = core.isEmpty

    /**
     * Lock-free lookup. Returns `null` if no store has been registered for [key].
     */
    @Suppress("UNCHECKED_CAST")
    public fun <K : Any, S : Any> get(key: StoreKey<K, S>): Store<S>? =
        core.get(key) as Store<S>?

    /**
     * Returns the existing store registered for [key], or creates one with
     * [creator]. Creator runs at most once per key. See [StoreRegistry.getOrCreate]
     * for the full contract.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <K : Any, S : Any> getOrCreate(
        key: StoreKey<K, S>,
        creator: () -> Store<S>,
    ): Store<S> = core.getOrCreate(key) { creator() } as Store<S>

    /** Evicts the entry for [key]. Returns true iff anything was removed. */
    public fun <K : Any, S : Any> remove(key: StoreKey<K, S>): Boolean = core.remove(key)

    /** Atomically evicts all entries. Fires [TypedRegistryEvent.Removed] per entry. */
    public fun clear(): Unit = core.clear()

    /**
     * Registers a listener. Returns a subscription that, when invoked, unregisters.
     * See [StoreRegistry.addListener] for the invocation contract.
     */
    public fun addListener(listener: (TypedRegistryEvent) -> Unit): RegistrySubscription =
        core.addListener { coreEvent ->
            listener(coreEvent.toPublic())
        }

    private fun RegistryCore.Event<StoreKey<*, *>>.toPublic(): TypedRegistryEvent = when (this) {
        is RegistryCore.Event.Added -> TypedRegistryEvent.Added(id)
        is RegistryCore.Event.Removed -> TypedRegistryEvent.Removed(id)
    }
}
