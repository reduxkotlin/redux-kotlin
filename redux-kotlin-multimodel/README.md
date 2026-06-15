# redux-kotlin-multimodel

`ModelState` — a typesafe, heterogeneous bag of models held in a single
[redux-kotlin](../redux-kotlin) store. Compose a large app's state from many
independently-typed models without hand-writing one giant root data class.

## Install

```kotlin
implementation("org.reduxkotlin:redux-kotlin-multimodel:<version>")
```

(Already included transitively by [`redux-kotlin-bundle`](../redux-kotlin-bundle).)

## Quick start

```kotlin
import org.reduxkotlin.multimodel.ModelState

data class UserModel(val name: String = "")
data class CartModel(val items: Int = 0)

val initial = ModelState.of(UserModel(), CartModel())
val user: UserModel = state.get()             // typed lookup (or state.get<UserModel>())
val next = state.with(UserModel(name = "ann")) // replace one model slot
val merged = state.withAll(otherState)         // overlay another ModelState
```

Each model is keyed by its type, so lookups are typesafe and two models of
different types never collide. Pairs naturally with the routed-reducer DSL in
[`redux-kotlin-routing`](../redux-kotlin-routing).

## See also

- [MultiModel](https://reduxkotlin.org/advanced/multimodel) · [Routing](https://reduxkotlin.org/advanced/routing)
- Granular subscriptions over models: [`redux-kotlin-multimodel-granular`](../redux-kotlin-multimodel-granular)
