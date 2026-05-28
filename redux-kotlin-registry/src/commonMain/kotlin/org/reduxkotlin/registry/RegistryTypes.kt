package org.reduxkotlin.registry

import kotlin.reflect.KClass

/**
 * Returned by registry `addListener` calls. Invoke to unregister.
 *
 * Mirrors `org.reduxkotlin.StoreSubscription` for ergonomic parity.
 */
public typealias RegistrySubscription = () -> Unit

/**
 * Event emitted by [StoreRegistry] when its membership changes. See the registry
 * KDoc and the design spec for the listener invocation contract.
 */
public sealed interface RegistryEvent<out K> {
    public val id: K
    public data class Added<K>(override val id: K) : RegistryEvent<K>
    public data class Removed<K>(override val id: K) : RegistryEvent<K>
}

/**
 * Type-safe heterogeneous container key for [TypedStoreRegistry].
 *
 * Equality is on `(id, stateType)` — two keys with the same `id` but different
 * `stateType` are distinct entries (no silent aliasing across state types).
 *
 * Construct with the [storeKey] factory; the constructor is `@PublishedApi
 * internal` to preserve the invariant that `stateType` matches the static type
 * of `S` at the call site.
 */
public class StoreKey<K : Any, S : Any> @PublishedApi internal constructor(
    public val id: K,
    public val stateType: KClass<S>,
) {
    override fun equals(other: Any?): Boolean =
        other is StoreKey<*, *> && other.id == id && other.stateType == stateType

    override fun hashCode(): Int = 31 * id.hashCode() + stateType.hashCode()

    override fun toString(): String = "StoreKey($id, ${stateType.simpleName})"
}

/**
 * Inline factory; captures the static state type `S` via `reified`.
 */
public inline fun <K : Any, reified S : Any> storeKey(id: K): StoreKey<K, S> =
    StoreKey(id, S::class)

/**
 * Event emitted by [TypedStoreRegistry]. The carried key is star-projected
 * because events are erased across state types; consumers narrow via
 * `event.key.stateType` when needed.
 */
public sealed interface TypedRegistryEvent {
    public val key: StoreKey<*, *>
    public data class Added(override val key: StoreKey<*, *>) : TypedRegistryEvent
    public data class Removed(override val key: StoreKey<*, *>) : TypedRegistryEvent
}
