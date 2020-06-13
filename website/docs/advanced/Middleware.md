---
id: middleware
title: Middleware
sidebar_label: Middleware
hide_title: true
---

# Middleware

Middleware are functions that can have react to actions and have side effects. They can also
dispatch other actions. You've seen middleware in action in the 
[Async Actions](../advanced/AsyncActions.md) example. The best feature of middleware is that it's
composable in a chain. You can use multiple independent third-party middleware in a single project.

Middleware are any functions that meet this type alias:

`Definitions.kt`
```kotlin
typealias Middleware<State> = (store: Store<State>) -> (next: Dispatcher) -> (action: Any) -> Any
```
**It provides a third-party extension point between dispatching an action, and the moment it reaches 
the reducer.** People use Redux middleware for logging, crash reporting, talking to an asynchronous 
API, routing, and more.

Redux.js.org has an [in-depth intro to middleware](https://redux.js.org/advanced/middleware) that 
you may find helpful.

Here are a few examples of how to declare middleware using ReduxKotlin:


```kotlin

/**
 * Logs all actions and states after they are dispatched. 
 */
val loggerMiddleware = middleware<AppState> { store, next, action ->
    val result = next(action)
    Logger.d("DISPATCH action: ${action::class.simpleName}: $action")
    Logger.d("next state: ${store.state}")
    result
}

/**
 * Same functionality as above, but declared using a function
 */
fun loggerMiddleware2(store: Store<AppState>) = { next: Dispatcher ->
    { action: Any ->
        val result = next(action)
        Logger.d("DISPATCH action: ${action::class.simpleName}: $action")
        Logger.d("next state: ${store.state}")
        result
    }
}

/**
 * Sends crash reports as state is updated and listeners are notified.
 */
val crashReporter = middleware<AppState> { store, next, action ->
    try {
        return next(action)
    } catch (e: Exception) {
        // report to crashlytics, etc
        throw err     
    }
}

/**
 * From redux-kotlin-thunk.  This how thunks are executed.
 */
fun createThunkMiddleware(extraArgument: Any? = null): ThunkMiddleware =
    { store ->
        { next: Dispatcher ->
            { action: Any ->
                if (action is Function<*>) {
                    try {
                        (action as Thunk)(store.dispatch, store.getState, extraArgument)
                    } catch (e: Exception) {
                        throw IllegalArgumentException()
                    }
                } else {
                    next(action)
                }
            }
        }
    }
        
val store = createThreadSafeStore(
    reducer,
    applyMiddleware(
        createThunkMiddleware(),
        loggerMiddleware,
        ::loggerMiddleware,
        crashReporter
    )
)
```
