---
id: compose-integration
title: Jetpack / Multiplatform Compose
sidebar_label: Compose Integration
---

# Compose Integration

`redux-kotlin-compose` bridges a `redux-kotlin` `Store` to
[Jetpack Compose](https://developer.android.com/jetpack/compose) /
Compose Multiplatform. It turns a selected slice of store state into a
Compose [`State<T>`](https://developer.android.com/jetpack/compose/state),
so a Composable recomposes **only** when the slice it reads actually
changes.

The bridge is built on top of [Granular Subscriptions](granular-subscriptions):
each binding is a `subscribeTo` under the hood, so recomposition is
scoped to the exact field a Composable observes rather than every
dispatch.

## Installation

```kotlin
implementation("org.reduxkotlin:redux-kotlin-compose:<version>")
```

The module depends on `redux-kotlin-granular` and the Compose runtime.
It targets the platforms that Compose Multiplatform supports.

## Binding a field: `fieldState`

The common case — bind one property of state to a `State<T>` using a
Kotlin property reference:

```kotlin
import androidx.compose.runtime.getValue
import org.reduxkotlin.compose.fieldState

data class AppState(val user: User? = null, val count: Int = 0)

@Composable
fun Counter(store: Store<AppState>) {
    val count by store.fieldState(AppState::count)
    Text("Count: $count")
}
```

`Counter` recomposes when `count` changes, but not when an unrelated
field (e.g. `user`) changes.

## Deriving a value: `selectorState`

When you need to project or compute a value rather than read a property
directly, use the lambda form:

```kotlin
import org.reduxkotlin.compose.selectorState

@Composable
fun OpenTodoBadge(store: Store<AppState>) {
    val openCount by store.selectorState { it.todos.count { todo -> !todo.completed } }
    Badge { Text("$openCount") }
}
```

The Composable recomposes only when `openCount` itself changes — marking
a todo complete that doesn't alter the count won't trigger it.

:::note Selector stability
The lambda passed to `selectorState` is remembered against the store and
**frozen at first composition**. If the selector closes over other
Composable state that should refresh the binding, prefer `fieldState`
with a property reference, or re-key the subscription yourself inside a
`LaunchedEffect`. The first frame is race-safe: the bridge re-samples
the current state inside a `DisposableEffect`, so a dispatch landing
between composition and commit is reflected immediately.
:::

## Skippability: `StableStore`

Compose's stability inferrer treats interfaces as unstable. Because
`Store<S>` is an interface, a Composable that takes a `Store<S>`
parameter directly becomes **non-skippable** — it recomposes
unconditionally whenever its parent does.

Wrap the store in `StableStore` to restore skippability for downstream
Composables:

```kotlin
import org.reduxkotlin.compose.StableStore
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.compose.fieldState

@Composable
fun App(store: Store<AppState>) {
    val stable = rememberStableStore(store)
    Content(stable)
}

@Composable
fun Content(store: StableStore<AppState>) {
    val user by store.value.fieldState(AppState::user)
    // Content is now skippable: it only recomposes when `user` changes.
}
```

`StableStore` is a `@Stable` value class, so the wrapper costs nothing at
runtime once inlined. `rememberStableStore(store)` is shorthand for
`remember(store) { StableStore(store) }`.

## Multi-model stores

If you use [`ModelState`](../introduction/ecosystem) from `redux-kotlin-multimodel`,
add `redux-kotlin-compose-multimodel` for property-reference bindings
that resolve a field on a specific feature model — the call site never
names `ModelState`:

```kotlin
implementation("org.reduxkotlin:redux-kotlin-compose-multimodel:<version>")
```

```kotlin
import org.reduxkotlin.compose.multimodel.fieldState

@Composable
fun ProfileHeader(store: Store<ModelState>) {
    // M (LoggedInUserModel) is inferred from the property reference's receiver.
    val displayName by store.fieldState(LoggedInUserModel::displayName)
    Text("Hello, $displayName")
}
```

For callers that hold the model type as a `KClass` rather than a
compile-time generic (raw JS/TS consumers, or generic helper code), use
the non-inline `fieldStateOf`:

```kotlin
import org.reduxkotlin.compose.multimodel.fieldStateOf

val displayName by store.fieldStateOf(LoggedInUserModel::class) { it.displayName }
```

## Lifecycle and threading

Each binding subscribes inside a `DisposableEffect` and unsubscribes in
`onDispose`, so subscriptions follow the Composable's lifecycle
automatically — no manual tear-down. The underlying granular
subscription inherits the store's threading guarantees; pair the bridge
with [`createThreadSafeStore`](../api/createthreadsafestore) if you
dispatch from multiple threads.

## See also

- [Granular Subscriptions](granular-subscriptions) — the field-level
  subscription layer the Compose bridge is built on.
- [Ecosystem](../introduction/ecosystem) — the full first-party module
  list, including `redux-kotlin-multimodel`.
