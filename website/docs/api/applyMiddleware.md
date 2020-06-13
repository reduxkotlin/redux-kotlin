---
id: applymiddleware
title: applyMiddleware
sidebar_label: applyMiddleware
hide_title: true
---

# `applyMiddleware(vararg middlewares: Middleware<State>): StoreEnhancer<State>`

Middleware is the suggested way to extend Redux with custom functionality. Middleware lets you wrap
the store's [`dispatch`](Store.md#dispatchaction) method for fun and profit. The key feature of 
middleware is that it is composable. Multiple middleware can be combined together, where each 
middleware requires no knowledge of what comes before or after it in the chain.

The most common use case for middleware is to support asynchronous actions without much boilerplate
code. It does so by letting you dispatch [async actions](../Glossary.md#async-action) in addition to 
normal actions.

For example, [redux-thunk](https://github.com/gaearon/redux-thunk) lets the action creators invert 
control by dispatching functions. They would receive [`dispatch`](Store.md#dispatchaction) as an 
argument and may call it asynchronously. Such functions are called _thunks_.  Currently that is the 
only middleware available for ReduxKotlin, though others could be added easily.

Middleware is not baked into [`createStore`](createStore.md) and is not a fundamental part of the 
Redux architecture, but we consider it useful enough to be supported right in the core. This way, 
there is a single standard way to extend [`dispatch`](Store.md#dispatchaction) in the ecosystem, and
different middleware may compete in expressiveness and utility.

#### Arguments

- `vararg middleware: Middleware<State>` (_arguments_): Functions that conform to the Redux
  _middleware API_. For ReduxKotlin this is a typealias:
```kotlin
typealias Middleware<State> = (store: Store<State>) -> (next: Dispatcher) -> (action: Any) -> Any
```
Each middleware receives [`Store`](Store.md)'s [`dispatch`](Store.md#dispatchaction) and 
[`getState`](Store.md#getState) functions as named arguments, and returns a function. That function
will be given the `next` middleware's dispatch method, and is expected to return a function of
`action` calling `next(action)` with a potentially different argument, or at a different time, or
maybe not calling it at all. The last middleware in the chain will receive the real store's
[`dispatch`](Store.md#dispatchaction) method as the `next` parameter, thus ending the chain.

#### Returns

(_Function_) A store enhancer that applies the given middleware. The store enhancer signature is:
 ```kotlin
typealias StoreEnhancer<State> = (StoreCreator<State>) -> StoreCreator<State>
 ```
but the easiest way to apply it is to pass it to [`createThreadSafeStore()`](./createStore.md) as the last 
`enhancer` argument.

#### Example: Custom Logger Middleware

```kotlin
fun loggerMiddleware2(store: Store<AppState>) = { next: Dispatcher ->
    { action: Any ->
        Logger.d("will dispatch $action")
        
        // Call the next dispatch method in the middleware chain.
        val returnValue = next(action)
        Logger.d("state after dispatch: ${store.state}")
        
        // This will likely be the action itself, unless
        // a middleware further in chain changed it.
        result
    }
}


val store = createThreadSafeStore(todos, AppState.INITIAL_STATE, applyMiddleware(::logger))

store.dispatch(AddTodoAction(text = "Understand middleware"))
// (These lines will be logged by the middleware:)
// will dispatch: AddTodoAction(text = "Understand middleware")
// state after dispatch: AppState(todos = [AddTodo(text = "Understand middleware")]
```

#### Tips

- Middleware only wraps the store's [`dispatch`](Store.md#dispatchaction) function. Technically, 
  anything a middleware can do, you can do manually by wrapping every `dispatch` call, but it's 
  easier to manage this in a single place and define action transformations on the scale of the 
  whole project.

- If you use other store enhancers in addition to `applyMiddleware`, make sure to put
  `applyMiddleware` before them in the composition chain because the middleware is potentially
  asynchronous.

- Ever wondered what `applyMiddleware` itself is? It ought to be an extension mechanism more
  powerful than the middleware itself. Indeed, `applyMiddleware` is an example of the most powerful
  Redux extension mechanism called [store enhancers](../Glossary.md#store-enhancer). It is highly 
  unlikely you'll ever want to write a store enhancer yourself. Another example of a store enhancer 
  is [redux-devtools](https://github.com/reduxjs/redux-devtools). Middleware is less powerful than a
  store enhancer, but it is easier to write.

- Middleware sounds much more complicated than it really is. The only way to really understand
  middleware is to see how the existing middleware works, and try to write your own. The function
  nesting can be intimidating, but most of the middleware you'll find are, in fact, 10-liners, and
  the nesting and composability is what makes the middleware system powerful.

- To apply multiple store enhancers, you may use [`compose()`](./compose.md).
