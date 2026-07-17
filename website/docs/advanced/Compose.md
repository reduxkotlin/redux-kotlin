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

## Composition root: `SelectorStore`

Create one `SelectorStore` at the root of each Compose composition, then pass it
to the screens that bind state. The capability is `@Stable`, provides
`dispatch`, and shares one final-store subscription among its bindings without
exposing direct state reads or the raw store. A component that only receives
finished values and callbacks should not receive a store at all.

```kotlin
import org.reduxkotlin.compose.SelectorStore
import org.reduxkotlin.compose.rememberSelectorStore

// At the platform/composition host:
setContent { App(rememberSelectorStore(store)) }

@Composable
fun App(store: SelectorStore<AppState>) = Home(store)
```

## Binding a field: `fieldState`

The common case — bind one property of state to a `State<T>` using a
Kotlin property reference:

```kotlin
import androidx.compose.runtime.getValue
import org.reduxkotlin.compose.SelectorStore
import org.reduxkotlin.compose.fieldState

data class AppState(val user: User? = null, val count: Int = 0)

@Composable
fun Counter(store: SelectorStore<AppState>) {
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
fun OpenTodoBadge(store: SelectorStore<AppState>) {
    val openCount by store.selectorState { it.todos.count { todo -> !todo.completed } }
    Badge { Text("$openCount") }
}
```

The Composable recomposes only when `openCount` itself changes — marking
a todo complete that doesn't alter the count won't trigger it.

:::note Selector captures
The lambda passed to the unkeyed `selectorState` is retained for the binding's
lifetime. When it captures an id, filter, or other changing Compose value, use
the keyed overload so Compose removes the old selector and installs the new
one:

```kotlin
val todo by store.selectorState(todoId) { state -> state.todosById[todoId] }
```

The first frame is race-safe: the bridge re-samples current state after the
subscription is installed, so a dispatch landing between composition and
commit is reflected immediately.
:::

## Skippability and shared delivery: `SelectorStore`

Compose's stability inferrer treats interfaces as unstable. Because
`Store<S>` is an interface, a Composable that takes a `Store<S>`
parameter directly becomes **non-skippable** — it recomposes
unconditionally whenever its parent does.

`SelectorStore` restores skippability for downstream binding components and
also removes N final-store subscribers in favor of one shared callback. It is
deliberately not a `Store`: runtime and effect code keep the raw store, while
Compose binding code can only select state and dispatch. It is the recommended
capability for Compose bindings:

```kotlin
import org.reduxkotlin.compose.SelectorStore
import org.reduxkotlin.compose.fieldState
import org.reduxkotlin.compose.rememberSelectorStore

setContent { Content(rememberSelectorStore(store)) }

@Composable
fun Content(store: SelectorStore<AppState>) {
    val user by store.fieldState(AppState::user)
    val onRefresh = remember(store) { { store.dispatch(RefreshUser) } }
    UserContent(user = user, onRefresh = onRefresh)
}
```

Each active selector still compares after a store update. Keep selectors narrow
and use `memoizedSelector` for expensive derived transforms. `StableStore`
remains binary compatible but is deprecated: its `.value` escape exposes the
raw store and should not be used for new Compose bindings.

### Stable command boundaries

Do not replace a raw store with a large set of loose callback parameters or a
second class that manually forwards every method. When a screen tree has many
user intents, define a method-only command interface and pass one stable-identity
implementation from the host. The interface must not expose state, engines,
scopes, or other mutable runtime objects.

If the command implementation may depend on Compose runtime, annotate it
`@Stable`. If the command module must remain Compose-free, list the interface in
the application's Compose compiler stability configuration instead. The latter
is a contract, not an optimization hint: every implementation must retain a
stable identity, and an adapter created with `remember` must include every
captured callback in its keys. Event arguments should be complete immutable
tap-time values so asynchronous work does not re-read mutable UI or store state.

## Threading and mobile targets

`SelectorStore` inherits the callback thread of its final store. For a
`createConcurrentStore` that can be dispatched from effects or other workers,
use a serial `NotificationContext` that posts to the platform UI thread. On
Android, wrap the main-looper `Handler` with `coalescingNotificationContext`:

```kotlin
val notifications = coalescingNotificationContext(
    isOnTargetThread = { Looper.myLooper() == Looper.getMainLooper() },
    post = { block -> check(Handler(Looper.getMainLooper()).post(block)) },
)
val store = createConcurrentStore(reducer, AppState(), notificationContext = notifications)
```

This runs an idle UI-thread dispatch inline and marshals worker dispatches to
main without allowing them to overtake earlier work. Do not use the default
`NotificationContext.Inline` for a Compose store dispatched off-main, and do
not use a multi-threaded executor: granular selector diffs require serial
delivery. One shared callback reduces posting contention, but it still compares
every active selector on the target thread, so avoid broad or expensive
selectors in large lists.

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
import org.reduxkotlin.compose.SelectorStore

@Composable
fun ProfileHeader(store: SelectorStore<ModelState>) {
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
    // Child bindings observe the rehydrated store through the shared facade.
    Screen(rememberSelectorStore(store))
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

### Restoration replays no events — key effects on state

A restore dispatches exactly **one** action (your `restore` action). None
of the user events that originally produced the saved state are replayed:
no clicks, no `Navigate`-style actions. Anything your app loads *in
response to an event* therefore never loads on the restore path.

This is the same bug class as a web page that fetches in a click handler
and breaks on browser refresh: restoration — like a deep link — enters a
screen without the events that normally precede it. Redux adds more entry
points with the same shape: DevTools time-travel, replay, and hydrating an
account switch all *set* state without re-running events.

Two patterns survive all of them:

- **Derive the effect from state** (preferred). Key the load on the state
  the restore produces, not on the action that usually produces it:

  ```kotlin
  val route by store.fieldState(NavState::route)
  DisposableEffect(route) {
      if (route is Route.Detail) store.dispatch(LoadDetailRequested(route.id))
      onDispose { /* cancel / close */ }
  }
  ```

  Because the restore is applied synchronously during composition, the
  effect's first key evaluation already sees the restored route and the
  load fires — exactly as it would after a real navigation.

- **React to the restore action in middleware** (fallback). The restore
  action is a normal dispatch through the **full middleware chain**, so an
  effects middleware can match it and kick the loads explicitly.

Either way, write the `restore` action's downstream handling to tolerate
**stale references** — a snapshot can outlive the data it points at (a
deleted item, a removed board). Treat "referenced entity not found" as a
navigate-away or empty state, never a crash.

### Threading & platforms

The snapshot is read and the restore action dispatched on the **main
thread**, so the persisted store must accept main-thread reads and dispatch
— the Compose-facing store (the concurrent/threadsafe bundle store, or a
main-confined store). The anchor rides whatever `SaveableStateRegistry`
the platform's Compose runtime provides. On Android that registry is wired
to `savedInstanceState`, so snapshots survive rotation **and** process
death. On iOS, desktop, JS and wasm the Compose runtime does not currently
wire the registry to an OS restore mechanism, so the anchor is a no-op for
process death there — persist anything durable yourself and seed it via
[`preloadedState`](#rehydrating-at-construction-preloadedstate) instead.

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

Each binding subscribes inside a `DisposableEffect` and unsubscribes in
`onDispose`, so subscriptions follow the Composable's lifecycle
automatically — no manual tear-down. The underlying granular
subscription inherits the store's threading guarantees; if you dispatch
from multiple threads, use a concurrent store (the bundle's
`createConcurrentModelStore`, or `createConcurrentStore` from
`redux-kotlin-concurrent`) or wrap the store with
[`createThreadSafeStore`](../api/createthreadsafestore).

`fieldState` / `selectorState` read `store.state` synchronously on every
read — the subscription only *schedules recomposition*, it never caches a
value. So whenever a binding is read (any recomposition, however
triggered), it returns the store's current state, not a stale snapshot.
The recomposition that a dispatch itself triggers rides the store's
notification: inline contexts deliver it synchronously; a posting context
delivers it on a later main-loop iteration. With a concurrent store, wrap
the main-thread post in `coalescingNotificationContext(isOnTargetThread,
post)` (from `redux-kotlin-concurrent`): a **main-thread** dispatch then
notifies subscribers inline with no extra frame of latency, while
off-main dispatches still marshal to main (at most one loop hop).

## See also

- [Granular Subscriptions](granular-subscriptions) — the field-level
  subscription layer the Compose bridge is built on.
- [Ecosystem](../introduction/ecosystem) — the full first-party module
  list, including `redux-kotlin-multimodel`.
