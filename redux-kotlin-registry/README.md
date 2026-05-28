# redux-kotlin-registry

A thread-safe registry for multiple [redux-kotlin](../redux-kotlin) stores, keyed by a unique identifier of your choosing.

## When to use

Whenever your app has scoped state that must not bleed between instances:

- Per-thread-view store in a messaging app.
- Per-call store in a calling app.
- Per-screen store driven by a route identifier.

## Quick start (Tier 1 — same state type, many ids)

```kotlin
import org.reduxkotlin.createStore
import org.reduxkotlin.registry.RegistryEvent
import org.reduxkotlin.registry.StoreRegistry

typealias ThreadId = String

val threadStores = StoreRegistry<ThreadId, ThreadState>()

fun openThread(id: ThreadId) =
    threadStores.getOrCreate(id) { createStore(threadReducer, ThreadState()) }

fun closeThread(id: ThreadId) { threadStores.remove(id) }

fun onLogout() { threadStores.clear() }

val off = threadStores.addListener { event ->
    when (event) {
        is RegistryEvent.Added   -> telemetry.log("thread_store_opened", event.id)
        is RegistryEvent.Removed -> telemetry.log("thread_store_closed", event.id)
    }
}
// later: off()
```

## Heterogeneous (Tier 2 — mixed state types in one bag)

```kotlin
import org.reduxkotlin.registry.TypedStoreRegistry
import org.reduxkotlin.registry.storeKey

val global = TypedStoreRegistry()

val callStore   = global.getOrCreate(storeKey<CallState>(callId))     { createStore(callReducer, CallState()) }
val threadStore = global.getOrCreate(storeKey<ThreadState>(threadId)) { createStore(threadReducer, ThreadState()) }
```

`StoreKey<K, S>` carries a `KClass<S>` type witness so that two callers using the same `id` but different state types cannot silently alias each other's stores.

## Concurrency notes

- `get` and the fast path of `getOrCreate` are **lock-free**.
- `getOrCreate` runs your `creator` lambda **at most once per id** even under heavy concurrent contention.
- `remove`, `clear`, and the slow path of `getOrCreate` take a brief internal lock (via `kotlinx.atomicfu`).
- Listeners are dispatched **synchronously on the mutating thread, under the same internal lock**, so the global event order matches the global mutation order. Keep listener work short and **do not call back into the registry's mutating methods from a listener** (deadlock). Reads from a listener are safe.

## Lifecycle

Manual. The registry never auto-evicts. Removing a store from the registry does **not** dispatch any teardown action to it; callers still holding a reference may continue to use the store.

## See also

- Full design spec: `docs/superpowers/specs/2026-05-27-store-registry-design.md`
- Core store: [`redux-kotlin`](../redux-kotlin)
- Thread-safe store wrapper: [`redux-kotlin-threadsafe`](../redux-kotlin-threadsafe) — use to wrap each registered store if you need concurrent dispatch on it.
