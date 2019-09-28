---
id: glossary
title: Glossary
sidebar_label: Glossary
hide_title: true
---

# Glossary

This is a glossary of the core terms in Redux, along with their type signatures. The typealiases are
defined in [Definitions.kt](todo)

## State

_State_ (also called the _state tree_) is a broad term, but in the Redux API it usually refers to 
the single state value that is managed by the store and returned by 
[`getState()`](api/Store.md#getState) or the property syntax [`state`](api/Store.md#getState). It 
represents the entire state of a Redux application.

State can be any type of object, however data classes are well suited due to their `copy()` method 
for creating the new state.  The contents and structure of the State are largely up to requirements 
of the app and the opinion of the implementer.  In general, avoiding deeply nested structure makes 
creating the new state easier.  Also this is the state of your App, not necessarily the state of 
your UI. Still, you should do your best to keep the state serializable. Don't put anything inside it
that you can't easily turn into JSON.

## Action

An _action_ is an object that represents an intention to change the state. Actions are the only way 
to get data into the store. Any data, whether from UI events, network callbacks, system events, or 
other sources such as WebSockets needs to eventually be dispatched as an action.

ReduxKotlin differs a bit from JS Redux in that since we have a statically typed language, we can 
use the type as indication of the action to be performed. Typically this is done with a `when` 
statement:

```kotlin
when (action) {
   is MyAction -> // handle MyAction
   ...
}
```

In JS redux, a `type` field is used to indicate the type of action performed. This approach can be 
used with ReduxKotlin, however type safety and IDE benefits will be lost.

The structure of an action object is really up to you.  Data classes are a good choice, since 
`toString()` is implemented and can be useful for logging. Data inside the action is used to update 
the state inside reducers. For example, an API request result may be inside an Action.

See also [async action](#async-action) below.

## Reducer

```kotlin
typealias Reducer<State> = (state: State, action: Any) -> State
```

A _reducer_ (also called a _reducing function_) is a function that accepts an accumulation and a 
value and returns a new accumulation. They are used to reduce a collection of values down to a 
single value.

Reducers are not unique to Redux—they are a fundamental concept in functional programming. Even most 
non-functional languages, like Kotlin, have a built-in API for reducing. In Kotlin, reduce is a 
[`function on collections and many types`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/reduce.html).

In Redux, the accumulated value is the state object, and the values being accumulated are actions. 
Reducers calculate a new state given the previous state and an action. They must be _pure functions_
—functions that return the exact same output for given inputs. They should also be free of 
side-effects. This is what enables exciting features like [hot reloading and time travel](https://github.com/reduxjs/redux-devtools).
These are possible with ReduxKotlin, but not yet documented and may required additional dev work.

Reducers are the most important concept in Redux.

> #### Note about threading
> Reducers should always be ran on the same thread.  This is to avoid any
> race conditions with actions being processed.  Which thread is used is up
> to you and your team.  Main thread has traditionally been used without any problems,
> however there may be some situations where doing reducing fuctions on a background thread
> would benefit performance.

_Do not put API calls into reducers._

## Dispatching Function

```kotlin
typealias Dispatcher = (Any) -> Any
```

A _dispatching function_ (or simply _dispatch function_) is a function that accepts an action or an 
[async action](#async-action); it then may or may not dispatch one or more actions to the store.

We must distinguish between dispatching functions in general and the base 
[`dispatch`](api/Store.md#dispatchaction) function provided by the store instance without any 
middleware.

The base dispatch function _always_ synchronously sends an action to the store's reducer, along with
the previous state returned by the store, to calculate a new state. It expects actions to be plain 
objects ready to be consumed by the reducer.

[Middleware](#middleware) wraps the base dispatch function. It allows the dispatch function to 
handle [async actions](#async-action) in addition to actions. Middleware may transform, delay, 
ignore, or otherwise interpret actions or async actions before passing them to the next middleware. 
See below for more information.

## Action Creator

Action Creators are used in JS Redux, but are not as desirable in Kotlin. They are simply a function
that creates an Action or Async Action. In Kotlin, since we are typically using Data classes this is
not necessary. Data class constructors provide a nice syntax for creating actions. Using the Action
Creator pattern in Kotlin works as well, and is really up to you as to how and where actions are
created.

Calling an action creator only produces an action, but does not dispatch it. You need to call the
store's [`dispatch`](api/Store.md#dispatchaction) function to actually cause the mutation. Sometimes
we say _bound action creators_ to mean functions that call an action creator and immediately 
dispatch its result to a specific store instance.

If an action creator needs to read the current state, perform an API call, or cause a side effect,
like a routing transition, it should return an [async action](#async-action) instead of an action.

## Async Action

An _async action_ is a value that is sent to a dispatching function, but is not yet ready for
consumption by the reducer. It will be transformed by [middleware](#middleware) into an action (or a
series of actions) before being sent to the base [`dispatch()`](api/Store.md#dispatchaction) 
function. Async actions may have different types, depending on the middleware you use. They are 
often asynchronous primitives, like a thunk, which are not passed to the reducer immediately, but 
trigger action dispatches once an operation has completed.

Currently the only async middleware published is
[Thunk middleware](https://github.com/reduxkotlin/redux-kotlin-thunk). Thunk is just any function
that can be sent to the `dispatch()` function. The Thunk middleware will execute the function.

The design of Redux and its middleware allow creating other Async Action solutions.

## Middleware

```kotlin
typealias Middleware<State> = (store: Store<State>) -> (next: Dispatcher) -> (action: Any) -> Any
```

A middleware is a higher-order function that composes a
[dispatch function](#dispatching-function) to return a new dispatch function. It often turns
[async actions](#async-action) into actions.

Middleware is composable using function composition. It is useful for logging actions, performing
side effects like routing, or turning an asynchronous API call into a series of synchronous actions.

See [`applyMiddleware(...middlewares)`](./api/applyMiddleware.md) for a detailed look at middleware.

## Store

```kotlin
interface Store<State> {
    val getState: GetState<State>
    var dispatch: Dispatcher
    val subscribe: (StoreSubscriber) -> StoreSubscription
    val replaceReducer: (Reducer<State>) -> Unit
    val state: State
        get() = getState()
}
```

A store is an object that holds the application's state tree.  
There should only be a single store in a Redux app, as the composition happens on the reducer level.

- [`dispatch(action)`](api/Store.md#dispatchaction) is the base dispatch function described above.
- [`getState()`](api/Store.md#getState) returns the current state of the store.
- [`subscribe(listener)`](api/Store.md#subscribelistener) registers a function to be called on state
  changes.
- [`replaceReducer(nextReducer)`](api/Store.md#replacereducernextreducer) can be used to implement
  hot reloading and code splitting. Most likely you won't use it.

See the complete [store API reference](api/Store.md#dispatchaction) for more details.

## Store creator

```kotlin
// Enhancer is type Any? to avoid a circular dependency of types.
typealias StoreCreator<State> = (
    reducer: Reducer<State>,
    initialState: State,
    enhancer: Any?
) -> Store<State>
```

A store creator is a function that creates a Redux store. Like with dispatching function, we must
distinguish the base store creator, [`createStore(reducer, preloadedState)`](api/createStore.md) 
exported from the Redux package, from store creators that are returned from the store enhancers.

## Store enhancer

```kotlin
typealias StoreEnhancer<State> = (StoreCreator<State>) -> StoreCreator<State>
```

A store enhancer is a higher-order function that composes a store creator to return a new, enhanced
store creator. This is similar to middleware in that it allows you to alter the store interface in a
composable way.

Store enhancers are much the same concept as higher-order components in React, which are also
occasionally called “component enhancers”.

Copies can be easily created and modified without mutating the original store. There is an example
in
[`compose`](api/compose.md) documentation demonstrating that.

Most likely you'll never write a store enhancer. Amusingly, the
[Redux middleware implementation](api/applyMiddleware.md) is itself a store enhancer.
