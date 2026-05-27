---
id: granular-subscriptions
title: Granular Subscriptions
sidebar_label: Granular Subscriptions
---

# Granular Subscriptions

`redux-kotlin-granular` is an opt-in companion module that adds a
declarative API for subscribing to a specific field or selector with an
`(oldValue, newValue) -> Unit` callback. The listener fires **only when
the selected value actually changes**, removing the bulk of the
"re-read state and diff in every subscriber" boilerplate that's typical
in UI binding code.

The granular module sits on top of the core `redux-kotlin` `Store`
contract; it does not modify the core library and does not require a
particular store implementation. Anything that obeys the universal
Redux serial-dispatch contract — including `createStore`,
`createThreadSafeStore`, and future store implementations — composes
with it cleanly.

## Installation

Add the artefact to your module's dependencies (alongside whichever
core / threadsafe store you use):

```kotlin
implementation("org.reduxkotlin:redux-kotlin-granular:<version>")
```

The module targets every platform the core library supports: JVM,
Android, JS, wasmJs, iosArm64, iosX64, iosSimulatorArm64, macosArm64,
macosX64, linuxX64, mingwX64.

## Single-field subscription

```kotlin
import org.reduxkotlin.granular.subscribeTo

data class AppState(val user: User? = null, val count: Int = 0)

val store = createThreadSafeStore(rootReducer, AppState())

val unsubscribe = store.subscribeTo(AppState::user) { oldUser, newUser ->
    if (newUser != null) profileHeader.bind(newUser)
}

// later, in onDestroy / tear-down:
unsubscribe()
```

The listener fires:

- **Once at subscription time** with `(currentValue, currentValue)` —
  this is the `triggerOnSubscribe = true` default. Set
  `triggerOnSubscribe = false` if you want change-only semantics (e.g.
  for analytics or logging code that doesn't care about the initial
  value).
- **On each subsequent dispatch** where `selector(newState) !=
  previouslyObservedValue`. The comparison is reference-equality
  (`===`) first for the fast path, then structural (`==`).

### Lambda selectors

Use a lambda when you want to project or derive a value rather than
read a property directly:

```kotlin
val unsubscribe = store.subscribeTo({ state -> state.todos.count { !it.completed } }) { _, openCount ->
    statusBar.text = "$openCount open"
}
```

The listener only re-fires when `openCount` itself changes — adding a
completed item doesn't re-trigger.

## Batch subscription with `subscribeFields`

When a screen watches several fields, batch them with `subscribeFields`.
The DSL collapses `N` registrations into a **single** underlying
`store.subscribe` listener, which is cheaper under contention than N
separate `subscribeTo` calls:

```kotlin
import org.reduxkotlin.granular.subscribeFields

override fun onStart() {
    super.onStart()
    storeSubscription = store.subscribeFields {
        on(AppState::user)             { _, new -> userHeader.bind(new) }
        on(AppState::todos)            { _, new -> todoAdapter.submit(new) }
        on(AppState::visibilityFilter) { _, new -> todoAdapter.applyFilter(new) }
        on(AppState::theme)            { _, new -> applyTheme(new) }
    }
}

override fun onStop() {
    storeSubscription()
    super.onStop()
}
```

The returned `StoreSubscription` tears down every inner entry and the
single underlying listener when invoked.

### Selector error handling

If a selector throws — either at registration time or on a later
dispatch — the offending entry is forwarded to an optional
`onSelectorError` handler and skipped. **Other subscribers are not
affected**. This is a defence-in-depth property; the dispatch hot path
never breaks because of one bad selector.

```kotlin
val sub = store.subscribeFields(
    onSelectorError = { cause -> crashReporter.recordNonFatal(cause) },
) { scope ->
    scope.on({ state -> state.user!!.profile.displayName }) { _, name ->
        // ... if user is null, the NullPointerException above is captured
        // and the crashReporter is called; other entries continue.
    }
    scope.on(AppState::count) { _, n -> tickCounter.update(n) }
}
```

## Threading

The granular module does **not** introduce any locks of its own and
does **not** depend on `ThreadSafeStore`'s specific locking strategy.
It only depends on a property that every Redux store implementation
must already provide: **serial dispatch** — that subscribers attached
via `store.subscribe(...)` are invoked one at a time per dispatch and
that a dispatch completes before the next begins.

Concretely:

- `entry.last` is `@Volatile` for cross-thread visibility (the next
  dispatch may run on a different thread than the previous, and must
  see the previous tick's write).
- The entries list is **sealed at activation** — no mutation on the
  dispatch hot path, so no copying is required for safe iteration.
- No `AtomicReference`, no `synchronized` block, no `Mutex`, no
  `kotlinx.atomicfu` lock.

Composes safely with:

- The default single-threaded `createStore`
- `createThreadSafeStore` from `redux-kotlin-threadsafe`
- Any future store implementation that respects Redux's serial-dispatch
  contract (coroutine-serialised, lock-free MPSC queue, read-write
  lock, …).

A subscribe/unsubscribe-during-dispatch-storm test surfaced a
pre-existing race in `redux-kotlin-threadsafe` (the unsubscribe lambda
returned by `store.subscribe(...)` mutates the listener list outside
the lock). The granular layer itself never mutates its entries list
after activation, so this is a `ThreadSafeStore`-level issue — see the
`redux-kotlin-threadsafe` notes if you tear down subscriptions
concurrently with dispatches.

## Performance characteristics

Per-target framework overhead per dispatch with 100 distinct granular
entries:

| Target | All-`===`-hit floor (no field changed) | Mixed-change ceiling (50% entries differ) |
|---|---|---|
| JVM 21, hot JIT | ~0.5 µs | ≤ 5 µs |
| Kotlin/Native (iOS/macOS, release) | ~1 µs | ~10 µs |
| Kotlin/JS (V8, hot) | ~5 µs | ~50 µs |

The DSL form (`subscribeFields { on(...) ×N }`) is faster than N
separate `subscribeTo` calls because it amortises the underlying
`store.subscribe` callback across all entries: one `getState()` per
dispatch and one re-entry into `synchronized(this)` (on
`ThreadSafeStore`) instead of N.

## Swift consumption (iOS)

The lambda overloads of `subscribeTo` and the `on()` method on
`FieldSubscriptionScope` are usable from Swift. The Kotlin
property-reference overloads (`subscribeTo(AppState::user) { … }`)
are hidden from the Swift API surface via `@HiddenFromObjC` — Swift
has no equivalent of Kotlin's `::` property-reference syntax and can't
construct a `KProperty1` instance.

Swift call site:

```swift
// Assuming a Kotlin/Native framework exporting `Store<AppState>`,
// `FieldSubscriptionScope<AppState>`, and `subscribeFields(...)`.

let unsubscribe = StoreKt.subscribeFields(
    store,
    onSelectorError: nil
) { (scope: FieldSubscriptionScope<AppState>) in
    scope.on(
        selector: { (stateAny: Any?) -> Any? in (stateAny as! AppState).user },
        triggerOnSubscribe: true
    ) { (_: Any?, newUser: Any?) in
        guard let user = newUser as? User else { return }
        profileHeader.bind(user: user)
    }
    scope.on(
        selector: { (stateAny: Any?) -> Any? in (stateAny as! AppState).todos },
        triggerOnSubscribe: true
    ) { (_, newTodos) in
        guard let todos = newTodos as? [Todo] else { return }
        todoAdapter.submit(todos: todos)
    }
}
// later:
unsubscribe()
```

The `as!` downcasts are the friction price of Kotlin generics from
Swift — `FieldSubscriptionScope<State>` erases its generic at the
framework boundary, so selector lambdas receive `Any?`. A future
`redux-kotlin-swiftui` companion module will ship a typed Swift wrapper
to eliminate the casts.

## JavaScript / TypeScript consumption

The lambda overloads are usable from Kotlin/JS-from-Kotlin code today.
Raw JS / TypeScript export via `@JsExport` is **deferred to v2** — the
v1 lambda overload signatures use generic type parameters that
Kotlin/Wasm-JS doesn't allow in exported interop. Kotlin consumers
compiling to JS still get full access to the API; raw-JS-from-outside
will work once the export story is resolved.

## Marker enhancer

A no-op `granularSubscriptionsEnhancer<State>()` is provided for
authors who want to advertise the feature in their store-creation
chain:

```kotlin
val store = createStore(
    reducer,
    initialState,
    compose(
        applyMiddleware(loggingMiddleware),
        createThreadSafeStoreEnhancer(),
        granularSubscriptionsEnhancer(),
    ),
)
```

The enhancer doesn't wrap the store today — the granular API is purely
extension functions on `Store<State>`. The marker exists to reserve a
seam for future per-store optimisations.

## See also

- [Threading](../introduction/threading) — background on the
  serial-dispatch contract that granular subscriptions rely on.
- [createThreadSafeStore](../api/createthreadsafestore) — the
  recommended underlying store when multiple threads may dispatch.
