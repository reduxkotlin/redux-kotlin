---
id: routing
title: Routed Reducers
sidebar_label: Routing
---

# Routed Reducers

`redux-kotlin-routing` provides routed `(model, action)` dispatch over
[`ModelState`](multimodel). It replaces the `when(action) {}` cascade with
**exact-leaf-class routing**: an action only visits the handlers registered for
its concrete class, and only the models a handler changes are rebuilt — the
rest keep `===` identity, so the
[granular subscription layer](granular-subscriptions) stays precise.

```kotlin
implementation("org.reduxkotlin:redux-kotlin-routing:<version>")
```

(Already included if you use [`redux-kotlin-bundle`](bundle).)

## The DSL

```kotlin
import org.reduxkotlin.routing.createModelStore

val store = createModelStore {
    model(UserModel()) {
        on<LoggedIn>  { s, a -> s.copy(user = a.user) }
        on<LoggedOut> { s, _ -> s.copy(user = null) }
    }
    model(CartModel()) {
        on<AddItem> { s, a -> s.copy(items = s.items + a.item) }
    }
    onAction<Checkout> { reads, _ ->
        val cart = reads.get<CartModel>()
        writeSet { set(cart.copy(closed = true)) }
    }
    onBroadcast<Logout> { model, _ -> /* reset each model */ model }
    install(SomeFeatureModule)
}
```

- **`model(initial) { on<Action> { … } }`** — declares a state slot with its
  initial value and per-action handlers `(model, action) -> model`.
- **`onAction<A>`** — a multi-model handler: read any models via
  `reads.get<M>()`, return a `writeSet { set(…) }` of the models it changes.
- **`onBroadcast<A>`** — runs `(model, action) -> model` against *every*
  declared model; the place for cross-cutting actions like `Logout`.
- **`install(module)`** — composes a reusable `ReduxModule` (hand-written or
  [generated](#code-generation-reduce--reduxinitial)) into the store.

## Semantics

- **Exact-leaf matching.** `on<Open>` matches `Open`, not subtypes of a shared
  sealed parent. Register each leaf, or use `onBroadcast` for cross-cutting
  actions.
- **Structural init.** A model's starting value is its `model(initial)`
  declaration. There is no INIT-action fan-out.
- **Order fixed at creation.** Handlers for the same action run in
  registration order; `install(module)` order is the composition point.
- **Last-write-wins** on same-model writes within one dispatch.
- **Immutability is required.** Return a new instance to signal a change, the
  same instance for "no change". Enable `devChecks = true` to fail fast on
  wasteful structurally-equal copies.
- **Handlers must be pure.** `on` / `onAction` / `onBroadcast` handlers compute
  the next model(s) from their inputs only — never call `dispatch` or read the
  store from inside a handler (side effects belong in middleware). The same
  applies to the `onWrite` observer.
- **All-or-nothing.** A handler that throws aborts the whole dispatch; no
  partial commit.
- **Rehydration at construction.** The optional `preloadedState: ModelState?`
  parameter overlays restored/persisted models onto the declared defaults (its
  key set must be a subset of the declared slots), so the first `getState()`
  already reflects restored state — no post-paint dispatch. See
  [Bundles — rehydrating with `preloadedState`](bundle#rehydrating-with-preloadedstate).

## Threading

`createModelStore` builds a plain (not thread-safe) store. On multi-threaded
platforms use [`redux-kotlin-bundle`](bundle)'s `createConcurrentModelStore`,
which builds the same routed store and adopts it as a
[concurrent store](concurrent-store). The routing layer composes with
[granular subscriptions](granular-subscriptions) and the
[Compose bindings](compose-integration) unchanged.

## Code generation: `@Reduce` / `@ReduxInitial`

`redux-kotlin-routing-codegen` is a KSP processor that generates a
`ReduxModule` from annotated handler functions, so you annotate functions
instead of writing the DSL by hand:

```kotlin
@ReduxInitial fun userInitial(): UserModel = UserModel()
@Reduce fun onLoggedIn(s: UserModel, a: LoggedIn): UserModel = s.copy(user = a.user)
@Reduce fun onLoggedOut(s: UserModel, a: LoggedOut): UserModel = s.copy(user = null)
```

The processor generates `object MyFeature : ReduxModule`, installed like any
hand-written module:

```kotlin
val store = createModelStore { install(MyFeature) }
```

:::caution Not yet on Maven Central
The processor is currently consumed as an in-repo `project(...)` dependency —
publishing it is a pre-release follow-up. The setup below shows the wiring as
used by the repository's
[codegen sample](https://github.com/reduxkotlin/redux-kotlin/tree/master/redux-kotlin-routing-codegen-sample).
:::

### Setup (consumer module `build.gradle.kts`)

```kotlin
plugins {
    kotlin("multiplatform") // your KMP setup
    id("com.google.devtools.ksp")
}
dependencies {
    add("kspCommonMainMetadata", project(":redux-kotlin-routing-codegen"))
}
ksp {
    arg("routing.moduleName", "MyFeature")             // REQUIRED — names the generated object
    arg("routing.generatedPackage", "com.example.gen") // optional, defaults to org.reduxkotlin.routing.generated
}
kotlin {
    sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
```

### Rules

- `@Reduce` must be a **top-level** function `(M, A) -> M` (returns the model
  type). Model and action types must be **non-generic, non-nullable,
  public/internal** classes. Matching is by the action's **exact leaf class**
  (not subtypes).
- `@ReduxInitial` is a **top-level** `() -> M` provider. **Exactly one per
  model type, in the same module as that model's `@Reduce` handlers.** A model
  with handlers but no in-module `@ReduxInitial` is a compile error — for
  models shared across modules, register handlers with the hand-written DSL
  instead.
- Handlers must live in **`commonMain`** (only `kspCommonMainMetadata` is
  wired).

### Ordering with the hand-written DSL

`install(MyFeature)` registers its handlers at that point in the
`createModelStore { }` sequence. A hand-written handler for the same action
placed before/after the `install(...)` runs before/after the generated ones
(registration order fixes dispatch order, and last-write-wins applies within a
dispatch). If mixing, install generated modules first unless you intend
otherwise.

### v1 limitations

Single-model `@Reduce` handlers only. Multi-model (`onAction`) and broadcast
(`onBroadcast`) handlers, and handlers in platform source sets, are **not**
generated — use the hand DSL for those.

## See also

- [MultiModel](multimodel) — the `ModelState` container the routing layer
  drives.
- [Bundles](bundle) — `createConcurrentModelStore`, the routed store with
  thread safety.
- [Granular subscriptions](granular-subscriptions) — why preserved `===`
  identity matters.
