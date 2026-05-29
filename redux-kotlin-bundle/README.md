# redux-kotlin-bundle

One dependency for the common redux-kotlin stack. Adding `redux-kotlin-bundle`
transitively brings: **redux-kotlin** (core), **redux-kotlin-concurrent**,
**redux-kotlin-granular**, **redux-kotlin-multimodel**,
**redux-kotlin-multimodel-granular**, **redux-kotlin-registry**, and
**redux-kotlin-routing** — so granular subscriptions work over both flat
state and `ModelState`, and the routed-reducer DSL is available.

## Quick start

```kotlin
val store = createConcurrentModelStore {
    model(UserModel()) {
        on<LoggedIn>  { s, a -> s.copy(user = a.user) }
        on<LoggedOut> { s, _ -> s.copy(user = null) }
    }
}
store.dispatch(LoggedIn("ann"))
```

`createConcurrentModelStore` builds a routed `ModelState` store and adopts it as
a `ConcurrentStore` (lock-free reads + caller-serialized writes). It forwards an
optional `enhancer` for `applyMiddleware`, plus `notificationContext`/`onError`
(subscriber dispatch + listener-error isolation) and `devChecks`/`onWrite`.

## Multiple stores via the registry

```kotlin
val registry = StoreRegistry<String, ModelState>()
val userStore = registry.getOrCreateConcurrentModelStore("user") {
    model(UserModel()) { on<LoggedIn> { s, a -> s.copy(user = a.user) } }
}
// TypedStoreRegistry variant: registry.getOrCreateConcurrentModelStore(storeKey<ModelState>("user")) { … }
```

## Not included

- **Compose bindings** — use `redux-kotlin-bundle-compose` (it pulls the Compose runtime).
- **Codegen** — `redux-kotlin-routing-codegen` is a KSP processor, wired via
  `kspCommonMainMetadata`, not a runtime dependency; add it separately if you want
  `@Reduce`-generated modules.
