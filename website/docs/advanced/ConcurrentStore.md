---
id: concurrent-store
title: Concurrent Store
sidebar_label: Concurrent Store
---

# Concurrent Store

`redux-kotlin-concurrent` provides the recommended thread-safe store:
**lock-free reads, serialized writes** (the `CallerSerialized` strategy). It
replaces the deprecated `redux-kotlin-threadsafe`, which took one lock around
every store function.

```kotlin
implementation("org.reduxkotlin:redux-kotlin-concurrent:<version>")
```

(Already included if you use [`redux-kotlin-bundle`](bundle).)

## Creating a store

```kotlin
import org.reduxkotlin.concurrent.createConcurrentStore

val store = createConcurrentStore(reducer, initialState)
```

Full signature:

```kotlin
fun <State> createConcurrentStore(
    reducer: Reducer<State>,
    preloadedState: State,                       // must be deeply immutable
    notificationContext: NotificationContext = NotificationContext.Inline,
    onError: (Throwable) -> Unit = LogAndContinue,
    enhancer: StoreEnhancer<State>? = null,      // e.g. applyMiddleware(...)
): ConcurrentStore<State>
```

A typed variant `createTypedConcurrentStore` exists for `TypedStore` users,
and `Store<State>.asConcurrent(notificationContext, onError)` adopts an
existing store (the bundle uses this to adopt the routed model store).

:::note Install middleware via the `enhancer` parameter
Pass `applyMiddleware(...)` to `createConcurrentStore` rather than enhancing a
store you built elsewhere — that way middleware re-dispatch is routed through
the write sequencer. `asConcurrent` on an already-enhanced store works, but
middleware that captured the dispatch *function by value* at construction is
not intercepted.
:::

## Semantics

- **Reads never block.** `getState` and `subscribe` are lock-free — they don't
  wait, even while a dispatch is in flight.
- **Writes are serialized.** `dispatch` (and `replaceReducer`) go through a
  reentrant writer lock; concurrent dispatchers queue, state transitions stay
  sequential.
- **Read consistency.** Reads *off* the dispatching thread return an atomic
  state mirror published at the end of each dispatch — eventually consistent
  (they may briefly observe the previous state while a dispatch completes).
  Reads *on* the dispatching thread see the in-progress state, matching core
  Redux semantics (middleware `getState` works as expected).

## `NotificationContext`: where subscribers run

A `NotificationContext` decides on which thread subscriber callbacks (and the
`onError` handler) are invoked:

- **`NotificationContext.Inline`** (default) — runs every callback
  synchronously on the dispatching thread *while the writer lock is held*. A
  slow subscriber delays other dispatchers, never readers.
- **`coalescingNotificationContext(isOnTargetThread, post)`** — the right
  choice for UI apps. Runs the callback inline when the dispatch is already on
  the target (main) thread, and marshals it via `post` otherwise. This avoids
  the frame of read-after-dispatch lag a bare always-posting context (e.g. a
  plain `Handler::post`) introduces:

```kotlin
import org.reduxkotlin.concurrent.coalescingNotificationContext

val mainHandler = Handler(Looper.getMainLooper())
val store = createConcurrentStore(
    reducer,
    initialState,
    notificationContext = coalescingNotificationContext(
        isOnTargetThread = { Looper.myLooper() == Looper.getMainLooper() },
        post = { block -> mainHandler.post(block) },
    ),
)
```

"Coalescing" refers to the inline-vs-marshal routing only — bursts are **not**
collapsed; every dispatch delivers exactly one callback per subscriber.

Custom contexts must execute posted blocks one at a time, in post order
(single-threaded executor, main-thread post, or inline all qualify). Handing
blocks to a multi-threaded executor is unsupported — diff-based consumers
(granular subscriptions, and therefore `fieldState`/`selectorState`) assume
serial notification.

A notification is a *signal*, never a payload: callbacks must pull current
state via `getState`, since later dispatches may already have landed by the
time a posted callback runs. The store publishes its read mirror **before**
signaling, so a callback always observes state at least as new as the dispatch
that triggered it.

## `onError`: listener isolation

A throwing subscriber never aborts the dispatch or delivery to the remaining
subscribers. The throwable is handed to `onError`; the default `LogAndContinue`
prints and continues. Override it to forward to your logging:

```kotlin
val store = createConcurrentStore(
    reducer, initialState,
    onError = { t -> crashReporter.logNonFatal(t) },
)
```

The handler itself must not throw; if it does, the throwable is printed and
swallowed.

## Migrating from `createThreadSafeStore`

`redux-kotlin-threadsafe` is **deprecated**. For most code the migration is a
drop-in:

```kotlin
// before
val store = createThreadSafeStore(reducer, initialState, applyMiddleware(logging))

// after
val store = createConcurrentStore(reducer, initialState, enhancer = applyMiddleware(logging))
```

Note the `enhancer` is a *named* parameter (it sits after
`notificationContext`/`onError` in the signature). Behavioral differences to
review:

- Off-thread `getState` during an in-flight dispatch returns the **previous**
  state instead of blocking until the dispatch finishes. If a reader relied on
  blocking for freshness, subscribe instead.
- Subscriber callbacks can be marshaled off the dispatching thread via
  `NotificationContext` — with the default `Inline` they run on the
  dispatching thread, like the threadsafe store.
- `createSynchronizedStoreEnhancer` setups collapse into the single
  `createConcurrentStore(..., enhancer = ...)` call.

See [Threading](../introduction/threading) for the full decision table.

## See also

- [Bundles](bundle) — `createConcurrentModelStore`, the concurrent store over
  routed multi-model state.
- [Compose integration — lifecycle and threading](compose-integration#lifecycle-and-threading)
  — how the bindings ride the notification context.
- [Granular subscriptions — threading](granular-subscriptions#threading).
