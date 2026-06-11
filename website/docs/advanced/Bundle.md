---
id: bundle
title: The Bundles
sidebar_label: Bundles
---

# The Bundles

`redux-kotlin-bundle` and `redux-kotlin-bundle-compose` are one-dependency
assemblies of the recommended redux-kotlin stack. Instead of picking five or
six companion modules by hand, add one artifact and get a coherent,
version-aligned setup.

## What's inside

`org.reduxkotlin:redux-kotlin-bundle` transitively brings:

| Module | What it adds |
|---|---|
| `redux-kotlin` | Core `Store` / `Reducer` / `Middleware` contracts |
| `redux-kotlin-concurrent` | [Concurrent store](concurrent-store) — lock-free reads, serialized writes |
| `redux-kotlin-granular` | [Granular subscriptions](granular-subscriptions) — field-level `(old, new)` callbacks |
| `redux-kotlin-multimodel` | [`ModelState`](multimodel) — typesafe bag of feature models |
| `redux-kotlin-multimodel-granular` | Granular subscriptions over `ModelState` |
| `redux-kotlin-registry` | [Store registry](store-registry) — keyed multi-store container |
| `redux-kotlin-routing` | [Routing DSL](routing) — routed `(model, action)` reducers |

`org.reduxkotlin:redux-kotlin-bundle-compose` is the bundle **plus** the
Compose bindings — `redux-kotlin-compose` (`fieldState` / `selectorState`),
`redux-kotlin-compose-multimodel` (model-aware bindings), and
`redux-kotlin-compose-saveable` (snapshot persistence across rotation +
process death). It pulls the Compose runtime, so non-Compose projects should
stick with `redux-kotlin-bundle`.

```kotlin
// Compose Multiplatform apps:
implementation("org.reduxkotlin:redux-kotlin-bundle-compose:<version>")

// Everything else:
implementation("org.reduxkotlin:redux-kotlin-bundle:<version>")
```

**Not included:** `redux-kotlin-routing-codegen` (a KSP processor, wired via
`kspCommonMainMetadata`, not a runtime dependency — see
[Routing](routing#code-generation-reduce--reduxinitial)), the
[DevTools family](devtools), and `redux-kotlin-thunk`.

## Creating a store: `createConcurrentModelStore`

The bundle's entry point builds a routed [`ModelState`](multimodel) store (see
the [routing DSL](routing)) and adopts it as a `ConcurrentStore` — lock-free
reads, caller-serialized writes:

```kotlin
import org.reduxkotlin.bundle.createConcurrentModelStore

val store = createConcurrentModelStore {
    model(UserModel()) {
        on<LoggedIn>  { s, a -> s.copy(user = a.user) }
        on<LoggedOut> { s, _ -> s.copy(user = null) }
    }
    model(CartModel()) {
        on<AddItem> { s, a -> s.copy(items = s.items + a.item) }
    }
}
store.dispatch(LoggedIn("ann"))
val user = store.state.get<UserModel>().user
```

The full signature:

```kotlin
fun createConcurrentModelStore(
    enhancer: StoreEnhancer<ModelState>? = null,          // e.g. applyMiddleware(...)
    notificationContext: NotificationContext = NotificationContext.Inline,
    onError: (Throwable) -> Unit = LogAndContinue,        // isolates listener throwables
    devChecks: Boolean = false,                           // throws on structurally-equal writes
    onWrite: OnWrite? = null,                             // observes effective model writes
    preloadedState: ModelState? = null,                   // restored models, overlaid at construction
    block: RoutingBuilder.() -> Unit,                     // the routing DSL
): ConcurrentStore<ModelState>
```

- **`enhancer`** — forwarded to the underlying `createModelStore` *before* the
  store is adopted as concurrent, so middleware re-dispatch is routed through
  the write sequencer. Pass `applyMiddleware(...)` here, never wrap afterwards.
- **`notificationContext` / `onError`** — where subscriber callbacks run and
  how listener exceptions are isolated; see the
  [Concurrent Store guide](concurrent-store#notificationcontext-where-subscribers-run).
  UI apps typically pass `coalescingNotificationContext` around the main
  thread.
- **`devChecks` / `onWrite`** — routing-layer diagnostics; see
  [Routing](routing).

### Rehydrating with `preloadedState`

`preloadedState` overlays restored/persisted models onto the declared defaults
at construction, so the very first `getState()` already reflects restored
state — no post-paint dispatch, no flash of initial state:

```kotlin
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

Its key set must be a **subset** of the declared models; every slot you don't
preload keeps its declared initial value. See
[Compose integration — rehydrating at construction](compose-integration#rehydrating-at-construction-preloadedstate)
for how this pairs with `rememberSaveableState`.

## Multiple stores via the registry

For store-per-account / store-per-document topologies, the bundle adds
registry extensions with atomic get-or-create:

```kotlin
import org.reduxkotlin.bundle.getOrCreateConcurrentModelStore
import org.reduxkotlin.registry.StoreRegistry

val registry = StoreRegistry<String, ModelState>()
val userStore = registry.getOrCreateConcurrentModelStore("user") {
    model(UserModel()) { on<LoggedIn> { s, a -> s.copy(user = a.user) } }
}
```

Parameters other than the key mirror `createConcurrentModelStore` (minus
`preloadedState`) and are ignored on cache hits. A `TypedStoreRegistry`
variant is keyed by a typed key:

```kotlin
registry.getOrCreateConcurrentModelStore(storeKey<ModelState>("user")) { /* … */ }
```

See [Store Registry](store-registry) for lifecycle and membership listening.

## Bundle vs à-la-carte

Use the **bundle** when building an app: it encodes the recommended
architecture (concurrent store over routed multi-model state with granular
subscriptions) and keeps the module set version-aligned with zero thought.

Go **à-la-carte** when you:

- are writing a library/middleware that should only depend on the core
  contracts (`org.reduxkotlin:redux-kotlin`),
- target JS-only or another single-threaded environment where the concurrent
  machinery is unnecessary,
- want a flat single-`data class` state without `ModelState`/routing — then
  `redux-kotlin-concurrent` (+ `redux-kotlin-granular` or
  `redux-kotlin-compose` as needed) is the minimal set,
- need tight control over your dependency graph.

## Aligning versions: the BOM

For à-la-carte setups, `org.reduxkotlin:redux-kotlin-bom` is a Maven BOM
(Gradle *platform*) that pins every `org.reduxkotlin` module to one version —
import it once and drop the version from individual modules:

```kotlin
dependencies {
    implementation(platform("org.reduxkotlin:redux-kotlin-bom:<version>"))

    implementation("org.reduxkotlin:redux-kotlin")
    implementation("org.reduxkotlin:redux-kotlin-concurrent")
    implementation("org.reduxkotlin:redux-kotlin-compose")
}
```

The BOM covers all published modules: the core, `threadsafe` (deprecated),
`concurrent`, `thunk`, `granular`, `registry`, `multimodel`,
`multimodel-granular`, the Compose trio, `routing`, the bundles — and the
DevTools family.

:::caution DevTools are experimental
The `redux-kotlin-devtools-*` artifacts are aligned by the BOM but **exempt
from semver** until the devtools surface stabilizes — pin behind the BOM and
expect breaking changes between minor versions. See [DevTools](devtools).
:::

## See also

- [Concurrent Store](concurrent-store) — the threading model under the bundle.
- [Routing](routing) — the `model { on<Action> }` DSL in depth.
- [MultiModel](multimodel) — `ModelState` and granular subscriptions over it.
- [Compose integration](compose-integration) — the bindings the Compose bundle adds.
- [TaskFlow](../introduction/examples#taskflow--the-reference-architecture) —
  a full app on `redux-kotlin-bundle-compose`.
