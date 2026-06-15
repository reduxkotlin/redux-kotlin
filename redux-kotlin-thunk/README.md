# redux-kotlin-thunk

The classic Redux thunk middleware for [redux-kotlin](../redux-kotlin): dispatch
a function (a *thunk*) instead of a plain action to run async/conditional logic
with access to `dispatch` and `getState`.

## Install

```kotlin
implementation("org.reduxkotlin:redux-kotlin-thunk:<version>")
```

## Quick start

```kotlin
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore
import org.reduxkotlin.thunk.createThunkMiddleware

val store = createStore(reducer, AppState(), applyMiddleware(createThunkMiddleware()))

fun loadUser(id: String): Thunk<AppState> = { dispatch, getState, _ ->
    dispatch(LoadStarted)
    val user = api.fetch(id)
    dispatch(LoadSucceeded(user))
}

store.dispatch(loadUser("ann"))
```

`createThunkMiddleware(extraArgument)` optionally injects a third argument
(e.g. a repository or API client) into every thunk.

## See also

- [Async actions](https://reduxkotlin.org/advanced/async-actions) · [Async flow](https://reduxkotlin.org/advanced/async-flow)
