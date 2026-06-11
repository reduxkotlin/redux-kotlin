---
id: multimodel
title: Multi-Model State
sidebar_label: MultiModel
---

# Multi-Model State

`redux-kotlin-multimodel` provides `ModelState` — an immutable, typesafe bag of
independent feature models keyed by their concrete class. Every screen or
subsystem owns its own model type; one store drives them all.

```kotlin
implementation("org.reduxkotlin:redux-kotlin-multimodel:<version>")
```

(Already included if you use [`redux-kotlin-bundle`](bundle).)

## `ModelState`

```kotlin
import org.reduxkotlin.multimodel.ModelState

data class UserModel(val user: String? = null)
data class CartModel(val items: List<Item> = emptyList())

val state = ModelState.of(UserModel(), CartModel())

val user: UserModel = state.get<UserModel>()          // typed read
val next = state.with(UserModel(user = "ann"))        // replace one slot
```

Key properties:

- **Sealed key set.** The set of model *classes* is locked at construction
  (`ModelState.of(...)`) — reducers can replace an instance but never add or
  remove a slot. `get<M>()` is therefore guaranteed non-null; asking for an
  undeclared model class throws `IllegalStateException` (a programming error,
  not a runtime branch to handle).
- **"Not yet loaded" lives in the model.** Give each model a default
  constructor or a `NOT_SET` sentinel so dependents read `String` rather than
  `String?`.
- **Identity-preserving updates.** `with(model)` and the batch
  `withAll(changes)` copy the map once and share every untouched slot —
  unchanged models keep `===` identity, which is what makes granular
  subscriptions precise.
- **`withAll(other: ModelState)`** overlays every slot present in `other` onto
  the receiver (its key set must be a subset) — the primitive behind
  [`preloadedState` rehydration](bundle#rehydrating-with-preloadedstate).
- There is **no `getModel()`** — typed access is `state.get<M>()`, or the
  non-reified `state.get(M::class)` for raw JS/TS/Swift consumers and generic
  helpers.

## Driving it from a store

Two ways to reduce a `ModelState`:

**Routing DSL (recommended)** — [`redux-kotlin-routing`](routing) /
the bundle's `createConcurrentModelStore` declare slots and per-action handlers
in one place:

```kotlin
val store = createConcurrentModelStore {
    model(UserModel()) { on<LoggedIn> { s, a -> s.copy(user = a.user) } }
    model(CartModel()) { on<AddItem> { s, a -> s.copy(items = s.items + a.item) } }
}
```

**`combineModelReducers`** — the plain-reducer composition from this module,
for stores assembled by hand:

```kotlin
import org.reduxkotlin.multimodel.combineModelReducers
import org.reduxkotlin.multimodel.modelReducer

val rootReducer = combineModelReducers(
    modelReducer<UserModel> { model, action ->
        when (action) {
            is LoggedIn -> model.copy(user = action.user)
            else -> model
        }
    },
    modelReducer<CartModel> { model, action -> /* … */ model },
)
val store = createConcurrentStore(rootReducer, ModelState.of(UserModel(), CartModel()))
```

Each entry sees only its own model. Unlike the routing DSL, every reducer runs
on every action (`when`-cascade style); a non-reified `modelReducerOf(KClass,
reducer)` exists for generic wiring.

## Granular subscriptions: `redux-kotlin-multimodel-granular`

Subscribes to a single field of a single model — listeners fire only when that
value changes, with `(old, new)` semantics:

```kotlin
implementation("org.reduxkotlin:redux-kotlin-multimodel-granular:<version>")
```

```kotlin
import org.reduxkotlin.multimodel.granular.subscribeTo

// The model type is inferred from the property reference's receiver —
// the call site never names ModelState.
val sub = store.subscribeTo(UserModel::user) { old, new ->
    println("user: $old -> $new")
}
```

Inside a `subscribeFields { … }` batch (one underlying `store.subscribe` for
many fields, possibly across models), use the `on` counterpart:

```kotlin
store.subscribeFields {
    on(UserModel::user) { _, new -> render(new) }
    on(CartModel::items) { _, new -> renderCart(new) }
}
```

The property-reference overloads are inline + reified, so they are hidden from
Swift and not `@JsExport`ed. Raw JS/TS and Swift consumers use the
class-based forms `subscribeToModel(M::class, selector, listener)` /
`onModel(...)`. Subscription semantics (trigger-on-subscribe, threading,
error handling) are inherited from
[granular subscriptions](granular-subscriptions#multi-model-stores).

## Compose bindings: `redux-kotlin-compose-multimodel`

Binds a model field to a Compose `State<T>` (included in
[`redux-kotlin-bundle-compose`](bundle)):

```kotlin
implementation("org.reduxkotlin:redux-kotlin-compose-multimodel:<version>")
```

```kotlin
import org.reduxkotlin.compose.multimodel.fieldState

@Composable
fun ProfileHeader(store: Store<ModelState>) {
    val displayName by store.fieldState(LoggedInUserModel::displayName)
    Text("Hello, $displayName")
}
```

For callers holding the model type as a `KClass` (raw JS/TS, generic helper
code), the non-inline form:

```kotlin
import org.reduxkotlin.compose.multimodel.fieldStateOf

val displayName by store.fieldStateOf(LoggedInUserModel::class) { it.displayName }
```

See [Compose integration — multi-model stores](compose-integration#multi-model-stores)
for skippability (`StableStore`) and lifecycle details.

## See also

- [Routing](routing) — the DSL that drives `ModelState` per-action.
- [Bundles](bundle) — the one-dependency stack with `ModelState` at the
  center.
- [Granular subscriptions](granular-subscriptions) — the field-subscription
  layer underneath.
