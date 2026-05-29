# Design: redux-kotlin "one and all" packaging

- **Status:** Draft (design complete; pre-implementation)
- **Date:** 2026-05-28
- **Depends on:** `redux-kotlin-routing` (PR #303, not yet on `master`). This work and its PR are **stacked on** `feat/redux-kotlin-routing`.

## Problem

A typical redux-kotlin app wants several modules — thread-safe store, granular subscriptions, the store registry, and the routed-reducer DSL — and must add each as a separate dependency, keep their versions aligned by hand, and wire them together. That is mental overhead and boilerplate. Goal: collapse the common case to **one dependency** (and cut the wiring boilerplate too), without forcing heavy optional deps (Compose) on projects that don't use them.

## Module dependency facts (verified)

`api`-deps re-export transitively, so an aggregator that `api`s a module exposes that module's whole public surface to consumers.

- `threadsafe` → `api(core)`
- `granular` → `api(core)` (its `threadsafe` dep is `implementation`, internal)
- `registry` → `api(core)`
- `multimodel` → `api(core)`
- `multimodel-granular` → `api(granular)` + `api(multimodel)`
- `compose` → `api(granular)`; `compose-multimodel` → `api(compose)` + `api(multimodel)`
- `routing` → `api(core)` + `api(multimodel)` (+ internal `granular`/`threadsafe`)
- `routing-codegen` → a `kotlin("jvm")` **KSP processor** (not a runtime `api` dep — cannot be bundled)

## Solution — three new modules

### 1. `redux-kotlin-bundle` (KMP library; `convention.library-mpp-loved` + `convention.publishing-mpp`)

Aggregates the agreed base via `api(...)`:
```kotlin
commonMain.dependencies {
    api(project(":redux-kotlin-threadsafe"))
    api(project(":redux-kotlin-registry"))
    api(project(":redux-kotlin-routing"))             // → core + multimodel
    api(project(":redux-kotlin-multimodel-granular"))  // → granular + multimodel
}
```
Transitive closure a consumer gets from this one dependency: **core, threadsafe, granular, multimodel, multimodel-granular, registry, routing** — granular subscriptions work over both flat `State` and `ModelState`.

Plus a small **convenience API** (the only hand-written code in the module), composing public APIs of the bundled modules:

```kotlin
/** Builds a routed ModelState store wrapped for thread-safe cross-thread access. */
public fun createThreadSafeModelStore(
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> =
    ThreadSafeStore(createModelStore(devChecks = devChecks, onWrite = onWrite, block = block))

/** Get-or-create a routed thread-safe store in this registry under [id] (lazy, concurrency-safe). */
public fun <K : Any> StoreRegistry<K, ModelState>.getOrCreateThreadSafeModelStore(
    id: K,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> =
    getOrCreate(id) { createThreadSafeModelStore(devChecks, onWrite, block) }

/** TypedStoreRegistry variant, keyed by a typed [StoreKey]. */
public fun <K : Any> TypedStoreRegistry.getOrCreateThreadSafeModelStore(
    key: StoreKey<K, ModelState>,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> =
    getOrCreate(key) { createThreadSafeModelStore(devChecks, onWrite, block) }
```

Rationale: there is **no** one-liner today for a thread-safe *routed* store — `createModelStore` builds on plain `createStore`, and `ThreadSafeStore(store)` is a public wrapper over any `Store`. The factory closes that gap; the registry extensions wire it into the keyed multi-store container in one call. INIT dispatches on the calling thread during `createModelStore`, then the wrapper synchronizes all later `dispatch`/`getState`/`subscribe` — safe.

### 2. `redux-kotlin-bundle-compose` (KMP library)

```kotlin
commonMain.dependencies {
    api(project(":redux-kotlin-bundle"))
    api(project(":redux-kotlin-compose-multimodel"))   // → compose + multimodel
}
```
One dependency = the base bundle **plus** Compose `State<T>` bindings (`fieldState`, `selectorState`, `StableStore`, ModelState variants). This pulls the Compose runtime, so it is the **opt-in Compose path**; non-Compose projects use `redux-kotlin-bundle` and incur no Compose dependency. No hand-written code (pure aggregation).

### 3. `redux-kotlin-bom` (`java-platform`)

A versionless constraints artifact listing **every** published module at the project version: core, threadsafe, granular, registry, multimodel, multimodel-granular, compose, compose-multimodel, routing, routing-codegen, bundle, bundle-compose. À-la-carte consumers import it with `implementation(platform("org.reduxkotlin:redux-kotlin-bom:VERSION"))` and then list modules without versions; bundle users already get alignment from the single artifact. Published via the existing publishing convention (or a thin platform-publishing setup — see Open items).

## Consumer experience

- **Typical app:** `implementation("org.reduxkotlin:redux-kotlin-bundle:VERSION")` → all base APIs in scope; build a store with `createThreadSafeModelStore { model(Initial()){ on<Action>{} } }` or register many with `registry.getOrCreateThreadSafeModelStore(id) { … }`.
- **Compose app:** swap to `redux-kotlin-bundle-compose`.
- **À-la-carte:** import the BOM, list chosen modules without versions.
- **Codegen (optional, separate):** add the `redux-kotlin-routing-codegen` KSP processor + `kspCommonMainMetadata` wiring per its README. **Not** in the bundle (KSP processor, not a runtime dep).

## What's deliberately out

- **Compose in the base bundle** — pulls the Compose runtime onto every consumer; kept in `-bundle-compose`.
- **`routing-codegen` in any bundle** — it's a KSP processor, wired via `kspCommonMainMetadata`, not an `api` dependency.
- **Speculative convenience APIs** — only the thread-safe routed factory + the two registry extensions (the wiring the user actually hits). No flat-state or non-routed sugar (YAGNI; the underlying module APIs remain directly available).

## Testing

- `redux-kotlin-bundle` `commonTest`: (a) `createThreadSafeModelStore { }` dispatches correctly and survives concurrent dispatch (JVM stress, mirroring the routing module's pattern); (b) `registry.getOrCreateThreadSafeModelStore(id)` returns the same instance on repeat and a distinct one per id; (c) a compile-level "surface reachable" test importing and touching one public symbol from each re-exported module (threadsafe, granular, multimodel-granular, registry, routing) — proves the aggregation exposes them.
- `redux-kotlin-bundle-compose` `commonTest` (or jvmTest where Compose test infra requires it): a compile-level test importing a Compose-binding symbol, proving the aggregation works on top of the base bundle.
- BOM: no code/ABI; covered by `apiCheck`/`build`.
- All modules run the standard KMP target matrix via the convention; iOS-simulator/browser test execution is environmental (trust CI).

## API stability

`redux-kotlin-bundle` and `-bundle-compose` carry committed ABI dumps (the convenience factory + registry extensions are public surface; the re-exported `api` deps also appear). Dumps are small and stable (the factory signatures don't change with bundled-module internals). The BOM has no ABI. Run `apiDump` after adding the modules.

## Open items / pre-release follow-ups

- **BOM publishing:** confirm the vanniktech publish plugin handles a `java-platform` module, or add a thin platform-publishing configuration. (The existing `convention.publishing-mpp` is MPP-only; the BOM is not MPP.)
- **Stacking:** the bundle depends on `redux-kotlin-routing` (PR #303). Its PR is stacked on `feat/redux-kotlin-routing`; retarget to `master` after #303 merges. (`-bundle-compose` and the BOM also reference routing transitively / by constraint.)
- **Naming:** chosen `redux-kotlin-bundle` / `-bundle-compose` / `-bom`.
