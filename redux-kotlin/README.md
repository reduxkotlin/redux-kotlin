# redux-kotlin

The core, deliberately-minimal port of the [Redux](https://redux.js.org)
contract to Kotlin Multiplatform: `Store<S>` / `TypedStore`, `Reducer`,
`Middleware`, `createStore`, `applyMiddleware`, `combineReducers`, `compose`.
Every companion module layers on this same `Store<S>` contract.

## Install

Most apps should depend on a [bundle](../redux-kotlin-bundle) instead of the
core directly. For the core alone:

```kotlin
implementation("org.reduxkotlin:redux-kotlin:<version>")
// or pin all modules together: implementation(platform("org.reduxkotlin:redux-kotlin-bom:<version>"))
```

## Quick start

```kotlin
import org.reduxkotlin.createStore

data class AppState(val count: Int = 0)
sealed interface Action
data object Increment : Action

val reducer = { state: AppState, action: Any ->
    when (action) {
        is Increment -> state.copy(count = state.count + 1)
        else -> state
    }
}

val store = createStore(reducer, AppState())
val unsubscribe = store.subscribe { println(store.state.count) }
store.dispatch(Increment)   // prints 1
unsubscribe()
```

`createStore` is **not** thread-safe by itself — for concurrent dispatch use
[`redux-kotlin-concurrent`](../redux-kotlin-concurrent).

## See also

- [Getting started](https://reduxkotlin.org/introduction/getting-started) · [Core concepts](https://reduxkotlin.org/introduction/core-concepts)
- Async actions: [`redux-kotlin-thunk`](../redux-kotlin-thunk)
- The full ecosystem: [reduxkotlin.org](https://reduxkotlin.org)
