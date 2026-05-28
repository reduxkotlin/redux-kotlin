package org.reduxkotlin.registry

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.atomicfu.update

/**
 * Internal concurrency core shared by [StoreRegistry] and [TypedStoreRegistry].
 *
 * Invariants — see the design spec §6 for the full rationale.
 *
 *  - Reads (`get`, `size`, fast path of `getOrCreate`) are lock-free: one
 *    atomic load on [ref] + `HashMap.get`.
 *  - Mutating ops (`getOrCreate` slow path, `remove`, `clear`) acquire [lock].
 *  - `creator` in `getOrCreate` runs at most once per `id` across concurrent
 *    callers.
 *  - Listeners are invoked synchronously **under the lock** so the global event
 *    order matches the global mutation order. Listeners must not call back into
 *    the same registry's mutating methods (deadlock); reads from a listener are
 *    safe.
 *  - Map snapshots are immutable; readers holding an older snapshot see
 *    consistent state.
 */
internal class RegistryCore<K : Any, V : Any> {

    private val ref = atomic<Map<K, V>>(emptyMap())
    private val lock = SynchronizedObject()
    private val listeners = atomic<List<(Event<K>) -> Unit>>(emptyList())

    internal sealed interface Event<out K> {
        val id: K
        data class Added<K>(override val id: K) : Event<K>
        data class Removed<K>(override val id: K) : Event<K>
    }

    val size: Int get() = ref.value.size
    val isEmpty: Boolean get() = ref.value.isEmpty()

    fun get(id: K): V? = ref.value[id]

    fun getOrCreate(id: K, creator: () -> V): V {
        ref.value[id]?.let { return it }
        return synchronized(lock) {
            val existing = ref.value[id]
            if (existing != null) {
                existing
            } else {
                val v = creator()
                ref.value = ref.value + (id to v)
                fireUnderLock(Event.Added(id))
                v
            }
        }
    }

    fun remove(id: K): Boolean = synchronized(lock) {
        val cur = ref.value
        if (id !in cur) {
            false
        } else {
            ref.value = cur - id
            fireUnderLock(Event.Removed(id))
            true
        }
    }

    fun clear(): Unit = synchronized(lock) {
        val cur = ref.value
        ref.value = emptyMap()
        cur.keys.forEach { fireUnderLock(Event.Removed(it)) }
    }

    /**
     * Registers a listener and returns an unsubscribe lambda.
     *
     * If the **same** lambda instance is registered more than once, each
     * registration is independent: every event fires once per registration,
     * and each returned unsubscribe lambda only removes the registration it
     * was paired with (the first matching entry by `List.minus` semantics).
     * Callers who care about idempotent subscription should hold a single
     * reference and avoid double-registering.
     */
    fun addListener(listener: (Event<K>) -> Unit): RegistrySubscription {
        listeners.update { it + listener }
        return { listeners.update { cur -> cur - listener } }
    }

    private fun fireUnderLock(event: Event<K>) {
        // Capture a snapshot of the listener list. The list itself is CoW-immutable,
        // so adds/removes happening on other threads only swap in a new list — they
        // can't perturb iteration in progress here.
        val snapshot = listeners.value
        snapshot.forEach { it(event) }
    }
}
