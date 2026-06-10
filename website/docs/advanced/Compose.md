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

## Saving state across rotation & process death

`redux-kotlin-compose-saveable` persists a slice of store state so it
survives Android **configuration changes / rotation** and **process
death**, restoring it when the app relaunches. It rides Compose's
`SaveableStateRegistry` (the machinery behind `rememberSaveable`), so one
mechanism covers both; on platforms without OS state restoration (desktop /
JS / wasm) it is a safe no-op.

```kotlin
implementation("org.reduxkotlin:redux-kotlin-compose-saveable:<version>")
```

### Why a singleton store isn't enough

A store held as a process/DI singleton already survives rotation — the
process lives. But on **process death** the OS recreates the process, the
singleton is rebuilt from its *initial* state, and that state is lost.
And because the bindings above are one-directional (`store → State`),
restoring a value only in a Composable would be **overwritten** by the
store's initial state on the next subscription. The fix is to write the
restored value back into the store the only way a store can change — by
`dispatch`ing an action.

### 1. Describe what to save with `StateSaver`

A `StateSaver` is three things: a `@Serializable` snapshot of just the
fields worth keeping (keep it small — it goes into the platform's saved
instance state), a `save` projection from state, and a `restore` function
that turns a decoded snapshot into an action your reducer applies.

```kotlin
import kotlinx.serialization.Serializable
import org.reduxkotlin.compose.saveable.StateSaver

@Serializable
data class UiSnapshot(val tab: Int, val query: String)

// An action your reducer handles:
data class RehydrateUi(val tab: Int, val query: String)

val uiSaver = StateSaver(
    serializer = UiSnapshot.serializer(),
    save = { s: AppState -> UiSnapshot(s.tab, s.query) },
    restore = { RehydrateUi(it.tab, it.query) },
)
```

The reducer applies the restore action like any other:

```kotlin
fun appReducer(state: AppState, action: Any): AppState = when (action) {
    is RehydrateUi -> state.copy(tab = action.tab, query = action.query)
    // … other cases
    else -> state
}
```

### 2. Anchor it with `rememberSaveableState`

Place the anchor **once** per persisted scope — typically near the root,
or once per screen:

```kotlin
import org.reduxkotlin.compose.saveable.rememberSaveableState

@Composable
fun App(store: Store<AppState>) {
    store.rememberSaveableState(uiSaver)
    // child fieldState / selectorState bindings observe the rehydrated store
    Screen(store)
}
```

On a real restore the snapshot is decoded and `RehydrateUi` is dispatched
exactly once, before the bindings settle. On a normal cold start nothing is
dispatched. The snapshot is serialized only when the platform actually
saves (e.g. when the app is backgrounded) — there is no per-dispatch cost.

### Lists, navigation & multiple anchors

The anchor derives a key from its call-site position. If you persist
several independent scopes, place anchors inside a list, or move across a
navigation graph where positions can collide, pass an explicit, stable
`key`:

```kotlin
store.rememberSaveableState(detailSaver, key = "board-$boardId")
```

### Versioning & failures

Restore is **best-effort**: if a saved snapshot can't be decoded (e.g. a
new app version ships an incompatible `UiSnapshot`), it is dropped and the
app starts cold rather than crashing. For additive changes pass a lenient
codec via `StateSaver(json = Json { ignoreUnknownKeys = true })`; for
breaking changes, add a `version` field to the snapshot and branch on it
inside `restore`.

### What to persist — and what not to

Persist the **volatile UI state a user would miss**: the current route or
tab, an active filter or query, a selected item. Leave out anything that
is *transient* interaction state:

- **Modes and overlays** — if a detail screen was in an *edit* mode when
  the process died, restore it in *view* mode. Persisting the mode makes
  interrupted interactions feel sticky; the snapshot type simply doesn't
  carry the field.
- **Text drafts local to one Composable** — keep them out of the store
  entirely. A plain `rememberSaveable { mutableStateOf("") }` rides the
  same `SaveableStateRegistry` with zero store involvement.
- **Anything durable** — data that must survive a normal app restart
  (not just process death) belongs in real storage (a database, files),
  restored via [`preloadedState`](#rehydrating-at-construction-preloadedstate)
  below.

### Restore order & the first frame

The restore action is dispatched **synchronously during composition** of
the anchor, before any child binding reads the store — so on a
synchronous-dispatch store the very first frame already shows the
rehydrated state. There is no intermediate frame rendered from the
store's initial state. Place the anchor *above* the Composables that
read the restored slice.

### Threading & platforms

The snapshot is read and the restore action dispatched on the **main
thread**, so the persisted store must accept main-thread reads and dispatch
— the Compose-facing store (the concurrent/threadsafe bundle store, or a
main-confined store). On Android the snapshot rides `savedInstanceState`
(rotation + process death); iOS uses state restoration; desktop, JS and
wasm have no OS restore concept, so the anchor is a no-op there.

:::tip Bundled
`redux-kotlin-compose-saveable` ships inside
[`redux-kotlin-bundle-compose`](../introduction/ecosystem) — if you use the
Compose bundle you already have it.
:::

## Rehydrating at construction: `preloadedState`

`rememberSaveableState` covers state the *OS* saves for you. For state
**you** persist — a session loaded from disk, models read from a local
database — seed the store with it at construction instead of dispatching
it after the UI is up:

```kotlin
// Core store: pass the restored state as the initial state.
val store = createStore(::appReducer, restoredAppState)

// Routed ModelState store (routing / bundle modules): overlay restored
// models onto the declared defaults with `preloadedState`.
val store = createConcurrentModelStore(
    preloadedState = ModelState.of(
        NavModel(restoredStack),
        FilterModel(restoredQuery),
    ),
) {
    model(NavModel()) { /* handlers */ }
    model(FilterModel()) { /* handlers */ }
    model(BoardModel()) { /* handlers */ }   // not preloaded — keeps its default
}
```

`preloadedState` overlays the declared defaults via
`ModelState.withAll(other)` — its key set must be a **subset** of the
declared models, and every slot you don't preload keeps its declared
initial value. Because the store is *born* rehydrated, the first
`getState()` / first render is already correct: no post-paint dispatch,
no flash of initial state.

**Choosing between the two:** they compose, and a real app often uses
both —

| | `rememberSaveableState` | `preloadedState` |
|---|---|---|
| Storage | OS saved-instance state | Your own (DB, files, server) |
| Survives | Rotation + process death | Anything, incl. normal restart |
| Size | Small snapshots only | Whatever you load |
| Restore point | First composition of the anchor | Store construction |

:::info Real-world example — TaskFlow
The [TaskFlow sample](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/taskflow)
([ARCHITECTURE.md](https://github.com/reduxkotlin/redux-kotlin/blob/master/examples/taskflow/ARCHITECTURE.md))
splits persistence exactly this way: boards/cards/accounts are durable in
SQLDelight (domain state, restored at store construction), while the
per-account nav stack + board filter ride a single
`StateSaver<ModelState, UiSnapshot>` anchored with an account-scoped key
(`key = "account-ui-$accountId"`), restoring in view mode and keeping
new-card drafts in plain `rememberSaveable`.
:::

## Lifecycle and threading

## Lifecycle and threading

Each binding subscribes inside a `DisposableEffect` and unsubscribes in
`onDispose`, so subscriptions follow the Composable's lifecycle
automatically — no manual tear-down. The underlying granular
subscription inherits the store's threading guarantees; pair the bridge
with [`createThreadSafeStore`](../api/createthreadsafestore) if you
dispatch from multiple threads.

Bindings are **lag-free by construction**: `fieldState` / `selectorState`
read `store.state` synchronously on every read — the subscription only
schedules recomposition — so a binding never shows a stale value even
when the store delivers notifications asynchronously. With a concurrent
store that posts notifications to the main thread, wrap the post in
`coalescingNotificationContext(isOnTargetThread, post)` (from
`redux-kotlin-concurrent`): a main-thread dispatch then notifies
subscribers inline with no extra frame of latency, while off-main
dispatches still marshal to main.

## See also

- [Granular Subscriptions](granular-subscriptions) — the field-level
  subscription layer the Compose bridge is built on.
- [Ecosystem](../introduction/ecosystem) — the full first-party module
  list, including `redux-kotlin-multimodel`.
