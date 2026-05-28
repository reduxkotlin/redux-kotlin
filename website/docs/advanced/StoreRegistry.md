---
id: store-registry
title: Store Registry
sidebar_label: Store Registry
---

# Store Registry

`redux-kotlin-registry` is an opt-in companion module that manages
**multiple stores keyed by a unique identifier**, with thread-safe
`getOrCreate` semantics. Use it whenever your app has scoped state that
must not bleed between instances:

- a per-thread-view store in a messaging app,
- a per-call store in a calling app,
- a per-screen store driven by a route identifier.

The registry is a container only. It does not participate in dispatch or
reducer execution, and a store's own thread-safety is orthogonal — wrap
a registered store in [`createThreadSafeStore`](../api/createthreadsafestore)
if it needs concurrent dispatch.

## Installation

```kotlin
implementation("org.reduxkotlin:redux-kotlin-registry:<version>")
```

The module targets every platform the core library supports: JVM,
Android, JS, wasmJs, iosArm64, iosX64, iosSimulatorArm64, macosArm64,
macosX64, linuxX64, mingwX64. Its only dependencies are `redux-kotlin`
and `kotlinx.atomicfu` — no coroutines requirement.

## Tier 1: `StoreRegistry<K, S>`

The headline API. One registry holds many stores of the **same** state
type `S`, keyed by an identifier of type `K`.

```kotlin
import org.reduxkotlin.createStore
import org.reduxkotlin.registry.StoreRegistry

typealias ThreadId = String

val threadStores = StoreRegistry<ThreadId, ThreadState>()

fun openThread(id: ThreadId): Store<ThreadState> =
    threadStores.getOrCreate(id) {
        createThreadSafeStore(threadReducer, ThreadState())
    }

fun closeThread(id: ThreadId) {
    threadStores.remove(id)
}

fun onLogout() {
    threadStores.clear()
}
```

### The singleton pattern

`StoreRegistry` is intentionally a class, not a Kotlin `object`, so it
stays testable (you can create a fresh one per test). If you want a
process-global registry, hold a top-level `val` — that *is* the
singleton, with zero ceremony:

```kotlin
val threadStores = StoreRegistry<ThreadId, ThreadState>()
```

Or inject one per surface through your DI graph.

### API surface

| Member | Behaviour |
|---|---|
| `get(id): Store<S>?` | Lock-free lookup. Returns `null` if absent. Never invokes a creator. |
| `getOrCreate(id, creator): Store<S>` | Returns the existing store, or creates and inserts one. `creator` runs **at most once per id** across concurrent callers. |
| `remove(id): Boolean` | Evicts the entry. Returns `true` if anything was removed. |
| `clear()` | Atomically evicts all entries. |
| `size` / `isEmpty` | Snapshot counters. |
| `addListener(listener): RegistrySubscription` | Membership-change callback. Returns an unsubscribe lambda. |

### Listening for membership changes

```kotlin
import org.reduxkotlin.registry.RegistryEvent

val off = threadStores.addListener { event ->
    when (event) {
        is RegistryEvent.Added   -> telemetry.log("thread_store_opened", event.id)
        is RegistryEvent.Removed -> telemetry.log("thread_store_closed", event.id)
    }
}

// later, on tear-down:
off()
```

`clear()` fires one `Removed` event per evicted entry (not a single
coarse "cleared" event), so a listener that maintains a per-id index can
use the same code path it uses for `remove(id)`.

## Tier 2: `TypedStoreRegistry`

Opt-in, for the rarer case where one logical bag must hold stores of
**different** state types. Keys carry a `KClass<S>` type witness via
[`storeKey`][storekey], so the registry stays type-safe at the lookup
boundary.

```kotlin
import org.reduxkotlin.registry.TypedStoreRegistry
import org.reduxkotlin.registry.storeKey

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

[storekey]: #storekey

### `storeKey`

`storeKey<S>(id)` builds a `StoreKey<K, S>` that pairs your identifier
with a state-type witness. Two keys are equal **only** when both the
`id` and the state type match:

```kotlin
storeKey<CallState>("x") == storeKey<CallState>("x")   // true
storeKey<CallState>("x") == storeKey<ThreadState>("x") // false — different state types
```

That discrimination is the safety property: two callers using the same
`id` but different state types address **distinct** entries and can
never silently alias each other's store. Internally there is a single
unchecked cast on retrieval, which is provably safe because the only
insertion path is the typed `getOrCreate`.

### Choosing a tier

Prefer **Tier 1**. Reach for Tier 2 only when one registry genuinely
must mix state types; the `storeKey<S>(...)` ceremony at every call site
is deliberate friction that steers you back to Tier 1 when it would do.

## Concurrency

Concurrency is a first-class concern of this module.

- **Reads are lock-free.** `get(id)` and the fast path of
  `getOrCreate` perform a single atomic load of an immutable map
  snapshot followed by a `HashMap` lookup — no lock, no contention.
- **`getOrCreate` runs `creator` at most once per id.** Under
  contention, only one thread executes the creator; the others observe
  the inserted store and return it. This matters when the creator has
  side effects (opening a connection, allocating resources).
- **Writes take a brief lock.** `getOrCreate` (on a miss), `remove`,
  and `clear` acquire a `kotlinx.atomicfu` `SynchronizedObject` for the
  duration of the map swap. Writes copy the map snapshot (`O(n)`), which
  is microseconds for the dozens-to-thousands of entries typical of
  scoped registries.
- **Listeners fire under the lock.** Events are dispatched synchronously
  on the mutating thread, while the lock is held, so the global order of
  `Added`/`Removed` events matches the global order of mutations. The
  practical consequences:
  - Keep listener work short — a slow listener stalls all writers.
  - **Do not call back into a mutating method** (`getOrCreate`,
    `remove`, `clear`) from inside a listener — it will deadlock.
  - Reads (`get`, `size`) from inside a listener are safe, because
    reads never take the lock.

If a listener throws, the exception propagates to the caller of the
mutating operation and the remaining listeners are skipped. The mutation
itself has already completed and is observable. Listeners should not
throw.

## Lifecycle

Lifecycle is **manual**. The registry never auto-evicts — there is no
TTL, reference counting, or weak-reference reaping. Tie eviction to
whatever scope makes sense in your app (a ViewModel `onCleared`, a
screen disposal, a logout flow).

Removing a store from the registry does **not** dispatch a teardown
action to it, does **not** unsubscribe its listeners, and does **not**
invalidate references that callers already hold. The registry simply
forgets it; any further cleanup of the store is the caller's
responsibility.

## See also

- [createThreadSafeStore](../api/createthreadsafestore) — wrap a
  registered store when multiple threads dispatch to it.
- [Granular Subscriptions](granular-subscriptions) — bind UI to a
  specific field of any store, including ones held in a registry.
