# Store Registry — Design Spec

**Status:** Draft (awaiting user review)
**Date:** 2026-05-27
**Module:** `redux-kotlin-registry` (new)

## 1. Purpose

Add a capability to redux-kotlin for managing **multiple concurrent stores keyed by a unique identifier**, with safe **get-or-create** semantics under high thread contention. Primary use cases: stores scoped to a surface (per-screen), per-data-source (per-thread-view in a messaging app, per-call in a calling app), or any other natural scoping where state must not leak between instances.

The registry is a container; it does **not** itself participate in dispatch or reducer execution. Per-store thread-safety remains the responsibility of the store (use `redux-kotlin-threadsafe` if the underlying store needs concurrent dispatch).

## 2. Non-goals

- Automatic lifecycle (reference counting, TTL, coroutine-scope binding, weak refs). Manual `remove(id)` / `clear()` only.
- Cross-process / cross-VM coordination.
- Persisting registry state across process restarts.
- Replacement for `Store.subscribe` — registry listeners observe membership changes only, not state changes inside stores.

## 3. Constraints

- **KMP target parity** with `redux-kotlin-threadsafe`: `jvm`, `android`, `js`, `wasmJs`, `iosArm64`, `iosX64`, `iosSimulatorArm64`, `macosArm64`, `macosX64`, `linuxArm64`, `linuxX64`, `mingwX64`.
- **No `kotlinx.coroutines` dependency** in the module. Listener is a plain synchronous callback.
- **Lock-free reads.** `get(id)` and `getOrCreate` fast path must not acquire any lock.
- **Creator-at-most-once** semantics: under concurrent `getOrCreate(id, creator)`, `creator` runs at most once per id across all callers.
- **No `synchronized` blocks beyond what the existing project already uses.** The project precedent (`ThreadSafeStore`) uses `kotlinx.atomicfu.locks.SynchronizedObject`; we use the same primitive on the write path only.

## 4. Architecture

### 4.1 Module layout

```
redux-kotlin-registry/
  src/
    commonMain/kotlin/org/reduxkotlin/registry/
      RegistryCore.kt          (internal)
      StoreRegistry.kt         (Tier 1 — typed homogeneous)
      TypedStoreRegistry.kt    (Tier 2 — heterogeneous)
      RegistryEvent.kt
      StoreKey.kt
    commonTest/kotlin/org/reduxkotlin/registry/
      StoreRegistryTest.kt
      StoreRegistryListenerTest.kt
      TypedStoreRegistryTest.kt
    jvmTest/kotlin/org/reduxkotlin/registry/concurrency/
      StoreRegistryConcurrencyStressTest.kt
  build.gradle.kts
```

### 4.2 Dependencies

```kotlin
// build.gradle.kts (sketch)
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))           // for Store<S>
                implementation(libs.kotlinx.atomicfu)   // atomic refs + SynchronizedObject
            }
        }
        // jvmCommonTest is the parent source set for jvmTest + androidUnitTest;
        // concurrency stress tests live under jvmTest/ (raw java.util.concurrent
        // primitives — no extra test deps needed).
    }
}
```

No new dependencies beyond what `redux-kotlin-threadsafe` already uses. Concurrency stress tests rely on `java.util.concurrent` from the JVM stdlib (kotlin-test only).

## 5. Public API

### 5.1 Shared types

```kotlin
package org.reduxkotlin.registry

public typealias RegistrySubscription = () -> Unit
```

Mirrors `StoreSubscription` for ergonomic parity.

### 5.2 Tier 1 — `StoreRegistry<K : Any, S>` (primary API)

```kotlin
public class StoreRegistry<K : Any, S> {

    /** Lock-free read. Returns null if absent. Does NOT invoke creator. */
    public fun get(id: K): Store<S>?

    /**
     * Returns the existing store for [id], or atomically creates one via [creator] and inserts it.
     * [creator] is invoked **at most once per id** across all concurrent callers.
     * Listener fires Added(id) only on actual creation; not on a hit.
     */
    public fun getOrCreate(id: K, creator: () -> Store<S>): Store<S>

    /**
     * Evicts the entry for [id]. Returns true if anything was removed.
     * Listener fires Removed(id) only when the return value is true.
     */
    public fun remove(id: K): Boolean

    /**
     * Atomically evicts all entries.
     * Listener fires Removed(id) once per evicted entry, in unspecified order.
     */
    public fun clear()

    public val size: Int
    public val isEmpty: Boolean get() = size == 0

    /**
     * Register a listener. Returns a subscription that, when invoked, unregisters.
     * Listener invocation contract: see §6.3.
     */
    public fun addListener(listener: (RegistryEvent<K>) -> Unit): RegistrySubscription
}

public sealed interface RegistryEvent<out K> {
    public val id: K
    public data class Added<K>(override val id: K) : RegistryEvent<K>
    public data class Removed<K>(override val id: K) : RegistryEvent<K>
}
```

**Notes:**
- `K : Any` because map keys are non-null.
- `S` is unbounded (matches `redux-kotlin` core `State`).
- `S` parameter is at the type level only; the registry does not require reflection on `S`.
- The "singleton" pattern is realized by users: hold a top-level `val` or DI-scoped instance. The class itself is not an `object` (testability).

### 5.3 Tier 2 — `TypedStoreRegistry` (heterogeneous, opt-in)

For users who need one registry holding stores of **mixed state types**, keyed by ids that may also be heterogeneous.

```kotlin
public class StoreKey<K : Any, S : Any> @PublishedApi internal constructor(
    public val id: K,
    public val stateType: KClass<S>,
) {
    override fun equals(other: Any?): Boolean =
        other is StoreKey<*, *> && other.id == id && other.stateType == stateType
    override fun hashCode(): Int = 31 * id.hashCode() + stateType.hashCode()
    override fun toString(): String = "StoreKey($id, ${stateType.simpleName})"
}

public inline fun <K : Any, reified S : Any> storeKey(id: K): StoreKey<K, S> =
    StoreKey(id, S::class)

public class TypedStoreRegistry {
    public fun <K : Any, S : Any> get(key: StoreKey<K, S>): Store<S>?
    public fun <K : Any, S : Any> getOrCreate(
        key: StoreKey<K, S>,
        creator: () -> Store<S>,
    ): Store<S>
    public fun <K : Any, S : Any> remove(key: StoreKey<K, S>): Boolean
    public fun clear()

    public val size: Int
    public val isEmpty: Boolean get() = size == 0

    public fun addListener(listener: (TypedRegistryEvent) -> Unit): RegistrySubscription
}

public sealed interface TypedRegistryEvent {
    public val key: StoreKey<*, *>
    public data class Added(override val key: StoreKey<*, *>) : TypedRegistryEvent
    public data class Removed(override val key: StoreKey<*, *>) : TypedRegistryEvent
}
```

**Type safety:** the `StoreKey<K, S>` is a *type-safe heterogeneous container key* (Bloch pattern). `KClass<S>` carried in the key acts as a type witness:
- Two `storeKey<StateA>(id)` calls produce equal keys (insert/retrieve same entry).
- `storeKey<StateA>(id)` and `storeKey<StateB>(id)` are **distinct** entries (no aliasing).
- Internally one unsafe cast on retrieval; safe because insertion path enforces the type witness.

`KClass<S>` is multiplatform (works on JVM/JS/Native/WASM); does not require `kotlin-reflect`.

`@PublishedApi internal constructor` forces creation through the `storeKey<S>()` factory, preserving the invariant that `stateType` matches the static type of `S`.

## 6. Concurrency model

### 6.1 Internal core

Both registries delegate to a private parameterized core (no unsafe casts in Tier 1; one justified cast in Tier 2):

```kotlin
internal class RegistryCore<K : Any, V : Any> {
    private val ref = atomic<Map<K, V>>(emptyMap())
    private val lock = SynchronizedObject()
    private val listeners = atomic<List<(Event<K>) -> Unit>>(emptyList())

    internal sealed interface Event<K> {
        data class Added<K>(val id: K) : Event<K>
        data class Removed<K>(val id: K) : Event<K>
    }

    val size: Int get() = ref.value.size

    fun get(id: K): V? = ref.value[id]                              // lock-free

    fun getOrCreate(id: K, creator: () -> V): V {
        ref.value[id]?.let { return it }                            // fast path
        return synchronized(lock) {
            val existing = ref.value[id]
            if (existing != null) existing
            else {
                val v = creator()
                ref.value = ref.value + (id to v)
                fireUnderLock(Event.Added(id))
                v
            }
        }
    }

    fun remove(id: K): Boolean = synchronized(lock) {
        val cur = ref.value
        if (id !in cur) false
        else {
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

    fun addListener(l: (Event<K>) -> Unit): RegistrySubscription {
        listeners.update { it + l }
        return { listeners.update { cur -> cur - l } }
    }

    private fun fireUnderLock(e: Event<K>) {
        // Snapshot the listener list — safe against concurrent add/remove during dispatch.
        listeners.value.forEach { it(e) }
    }
}
```

### 6.2 Invariants

1. **Lock-free reads.** `get`, `size`, fast-path of `getOrCreate` perform exactly one atomic load on `ref` followed by a `HashMap.get`. No `synchronized` taken.
2. **Creator-at-most-once.** Under contention on the same `id`, exactly one thread enters the lock-protected block; subsequent threads observe the inserted store and return it.
3. **Atomic mutation + event dispatch.** Each `Added`/`Removed` event is fired **under the same lock** as the mutation that produced it. This guarantees a total order on events that matches the total order of mutations across all threads.
4. **Map snapshots are immutable.** Each write produces a new `Map` instance via `+`/`-`. Readers holding a stale snapshot see consistent state — no torn reads, no `ConcurrentModificationException`.
5. **Listener list is CoW.** `addListener` / unsubscribe is lock-free (`AtomicRef<List<Listener>>` + `update`). Dispatch iterates a snapshot of listeners.
6. **Memory ordering.** `atomicfu` atomics + `SynchronizedObject` give full happens-before across writers and readers.

### 6.3 Listener invocation contract

- **Synchronous, on the calling thread, under the registry lock.** The thread performing `getOrCreate` (when it creates), `remove` (when it actually removes), or `clear` invokes all listeners synchronously before returning.
- **Listeners must complete quickly.** A slow listener blocks all writers (the lock is held for the duration of dispatch). Same constraint as `Store.subscribe` in this project.
- **Listeners must not call back into mutating methods on the same registry** (`getOrCreate`, `remove`, `clear`) — this would deadlock. Read methods (`get`, `size`, `isEmpty`) are safe to call from a listener because they are lock-free.
- **Exception behavior:** if a listener throws, the exception propagates to the caller of the mutating operation. Remaining listeners are not invoked. The mutation itself has already completed and is observable. Documented behavior; listeners should not throw.
- **`clear()` event order:** `Removed(id)` events are fired in the iteration order of the underlying `Map.keys` — effectively unspecified across platforms. Consumers must not rely on order.
- **`clear()` state visibility from listeners:** by the time the first `Removed(id)` listener invocation runs, the registry is already empty (`size == 0`). Listeners reading the registry during a `clear()` dispatch will not see intermediate per-entry state. This differs from single `remove(id)` where state and event are 1:1.
- **Newly-registered listeners and in-flight events:** a listener added via `addListener` during another thread's `fireUnderLock` may or may not observe in-flight events. No ordering guarantee. After the in-flight dispatch returns, future events are observed.

### 6.4 Performance characteristics

| Operation | Cost | Lock | Allocation |
|---|---|---|---|
| `get(id)` hit | 1 atomic load + HashMap.get (~5–20 ns on JVM) | none | none |
| `get(id)` miss | same as above | none | none |
| `getOrCreate` hit (fast path) | same as `get` | none | none |
| `getOrCreate` miss (creates) | fast-path read + lock + creator() + map copy + N listeners | brief | new Map snapshot |
| `remove(id)` | lock + map copy + N listeners | brief | new Map snapshot |
| `clear()` | lock + 1 map alloc + (N entries × M listeners) calls | brief (entire dispatch) | one empty Map |
| `addListener` / unsubscribe | CAS loop on listener list | none | new List |

Map snapshot copy is O(n) per write. For `n ≤ 1000` typical, copies complete in microseconds and beat a persistent (HAMT-style) map on cache locality. Above ~10k entries, consider switching the snapshot type to `kotlinx.collections.immutable.PersistentMap` — recorded here as a future optimization, not in scope for v1.

Memory pressure scales with **write rate**, not read rate. Steady-state registries with low churn produce minimal GC.

## 7. Lifecycle

- **Manual only.** `remove(id)` and `clear()`.
- No automatic eviction. Users tie lifecycle to whatever scope makes sense in their app (ViewModel cleared callback, screen disposal, logout flow, etc.).
- Removing a store from the registry does **not** dispatch any action to that store, does **not** unsubscribe its listeners, and does **not** prevent further use by anyone still holding a reference. The registry only forgets it. Cleanup of the store itself (if needed) is the caller's responsibility.

## 8. Error model

- `getOrCreate`'s `creator` may throw. If it does:
  - The exception propagates to the caller.
  - The lock is released (the `synchronized` block exits normally on rethrow).
  - The map is **not** mutated (the `creator()` call precedes the `ref.value = ...` assignment).
  - No listener fires.
  - Next `getOrCreate(id, …)` call sees no entry; will retry the creator.
- Listener throws → see §6.3.

## 9. Testing strategy

### 9.1 `commonTest` (KMP-uniform API correctness)

`StoreRegistryTest.kt`:
- `get(absent)` returns null.
- `getOrCreate` invokes creator exactly once across two sequential calls.
- `remove(present)` returns true; `remove(absent)` returns false; idempotent.
- `clear()` empties; `size == 0`, `isEmpty == true`.
- `getOrCreate` after `remove` re-invokes creator; new Added event fires.

`StoreRegistryListenerTest.kt`:
- Added fires on creation only, not on hit.
- Removed fires on successful `remove` only, not on `remove(absent)`.
- `clear()` of N entries fires N Removed events.
- Unsubscribe stops events.
- Multiple listeners: each receives each event exactly once.
- Listener reading `registry.get(id)` from inside its own Added callback observes the new entry.

`TypedStoreRegistryTest.kt`:
- `storeKey<A>(id)` equals another `storeKey<A>(id)`.
- `storeKey<A>(id)` and `storeKey<B>(id)` are distinct keys (verified by storing distinct stores under each).
- Retrieved `Store<S>` allows typed dispatch without explicit cast.

### 9.2 `jvmTest` (concurrency stress — JVM only)

Pattern mirrors `redux-kotlin-granular`'s `ConcurrencyStressTest.kt`.

`StoreRegistryConcurrencyStressTest.kt`:

1. **Creator-at-most-once under contention.** 32 threads release on a `CyclicBarrier`, all call `getOrCreate(id) { counter.incrementAndCreate() }`. Assert counter == 1. Repeat 100 iterations across multiple ids.

2. **No torn reads under concurrent writers.** One writer thread loops `remove(id)` + `getOrCreate(id, …)`. Many reader threads spam `get(id)`. Assert: every read returns either null or a fully constructed store; no exceptions.

3. **Listener concurrency.** Producers spam mutations while one consumer thread loops `addListener` / unsubscribe. Assert: no `ConcurrentModificationException`; valid listeners observe correct event sets.

4. **`clear()` under load.** Producer threads churn `getOrCreate` + `remove`; one thread periodically `clear()`s. Final invariant after barrier: every Added(id) observed by a long-lived listener is paired with exactly one Removed(id) for that key's lifetime within the observed window.

5. **Memory visibility.** Writer populates many keys; reader on a separate thread reads each `get(id)` after `memoryBarrier` (implicit via atomic load). Assert all writes visible.

Each stress test runs in a bounded iteration loop (≤ 100 iterations) to keep CI runtime under ~10 s total.

### 9.3 Coverage target

- ≥ 95% branch coverage of `RegistryCore`.
- All public API methods exercised in both Tier 1 and Tier 2.
- Stress tests are regression smoke; not exhaustive proof of correctness.

## 10. Open questions / future work

- **Persistent-collection snapshot.** Above ~10k entries, swap `Map` for `kotlinx.collections.immutable.PersistentMap` to reduce per-write copy cost. Not in v1.
- **Coroutine-bound lifecycle helper.** Optional extension module `redux-kotlin-registry-coroutines` providing `getOrCreate(id, scope, creator)` that auto-removes on `scope.cancel()`. Out of v1 scope (no coroutine dep in core registry).
- **Reference-counting lifecycle helper.** Same: optional future extension.
- **Async creator.** A `getOrCreateAsync(id, creator: suspend () -> Store<S>)` overload — also a candidate for the coroutines extension.

## 11. Sample usage

### 11.1 Tier 1, per-thread scoping in a messaging app

```kotlin
typealias ThreadId = String

val threadStores = StoreRegistry<ThreadId, ThreadState>()

fun openThread(id: ThreadId): Store<ThreadState> =
    threadStores.getOrCreate(id) {
        createThreadSafeStore(threadReducer, ThreadState())
    }

fun closeThread(id: ThreadId) {
    threadStores.remove(id)
}

fun onUserLoggedOut() {
    threadStores.clear()
}
```

### 11.2 Tier 1 + listener for telemetry

```kotlin
val unsubscribe = threadStores.addListener { event ->
    when (event) {
        is RegistryEvent.Added   -> telemetry.log("thread_store_opened", event.id)
        is RegistryEvent.Removed -> telemetry.log("thread_store_closed", event.id)
    }
}
// later
unsubscribe()
```

### 11.3 Tier 2, mixed state types in a single bag

```kotlin
val global = TypedStoreRegistry()

val callStore: Store<CallState> =
    global.getOrCreate(storeKey<CallState>(callId)) {
        createStore(callReducer, CallState())
    }

val threadStore: Store<ThreadState> =
    global.getOrCreate(storeKey<ThreadState>(threadId)) {
        createStore(threadReducer, ThreadState())
    }
```

## 12. Acceptance criteria

- New module `redux-kotlin-registry` compiles and publishes for all KMP targets listed in §3.
- All `commonTest` cases pass on every target.
- All `jvmTest` concurrency stress tests pass deterministically over the iteration count.
- Public API matches §5 exactly.
- Documentation: KDoc on every public symbol; module README with quick-start mirroring §11.
- CI green on the PR branch.
