# redux-kotlin-routing

Routed `(model, action)` dispatch over `ModelState`. Replaces the
`when(action) {}` cascade with exact-leaf-class routing: an action only
visits the handlers registered for its concrete class, and only the
models a handler changes are rebuilt (preserving `===` identity of the
rest, so the granular subscription layer stays precise).

## Quick start

```kotlin
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

## Semantics

- **Exact-leaf matching.** `on<Open>` matches `Open`, not subtypes of a
  shared sealed parent. Register each leaf, or use `onBroadcast` for
  cross-cutting actions.
- **Structural init.** A model's starting value is its `model(initial)`
  declaration. There is no INIT-action fan-out.
- **Order fixed at creation.** Handlers for the same action run in
  registration order; `install(module)` order is the composition point.
- **Last-write-wins** on same-model writes within one dispatch.
- **Immutability is required.** Return a new instance to signal a
  change, the same instance for "no change". Enable `devChecks = true`
  to fail fast on wasteful structurally-equal copies.
- **Handlers must be pure.** `on` / `onAction` / `onBroadcast` handlers
  compute the next model(s) from their inputs only — never call
  `dispatch` or read the store from inside a handler (side effects
  belong in middleware). The same applies to the `onWrite` observer.
- **All-or-nothing.** A handler that throws aborts the whole dispatch;
  no partial commit.

Built on `redux-kotlin` + `redux-kotlin-multimodel`. Wrap with
`createThreadSafeStore` for cross-thread access; composes with
`redux-kotlin-granular` and the Compose bindings unchanged.
