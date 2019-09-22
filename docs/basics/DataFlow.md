---
id: data-flow
title: Data flow
sidebar_label: Data flow
hide_title: true
---

# Data Flow

Redux architecture revolves around a **strict unidirectional data flow**.

This means that all data in an application follows the same lifecycle pattern, making the logic of
your app more predictable and easier to understand. It also encourages data normalization, so that
you don't end up with multiple, independent copies of the same data that are unaware of one another.

If you're still not convinced, read [Motivation](../introduction/Motivation.md) and 
[The Case for Flux](https://medium.com/@dan_abramov/the-case-for-flux-379b7d1982c6) for a compelling
argument in favor of unidirectional data flow. Although 
[Redux is not exactly Flux](../introduction/PriorArt.md), it shares the same key benefits.
//TODO - include links to Android/iOS specific articles/talks on UDF

The data lifecycle in any Redux app follows these 4 steps:

1. **You call** [`store.dispatch(action)`](../api/Store.md#dispatchaction).

An [action](Actions.md) is a plain object describing _what happened_. For example:

```kotlin
data class LikeArticle(val articleId: Int)
data class FetchUserSuccess(val response: ApiResponse)
data class AddTodo(val text: String)
```

Think of an action as a very brief snippet of news. “Mary liked article 42.” or “'Read the Redux
docs.' was added to the list of todos.”

You can call
[`store.dispatch(action)`](../api/Store.md#dispatchaction) from anywhere in your app or even at
scheduled intervals.
//TODO note on threading & reducer -- also TODO note in reducer section on threading

2. **The Redux store calls the reducer function you gave it.**

The [store](Store.md) will pass two arguments to the [reducer](Reducers.md): the current state tree
and the action. For example, in the todo app, the root reducer might receive something like this:

```kotlin
val previousState = AppState(
    visibilityFilter = VisibilityFilter.SHOW_ALL,
    todos = listOf(
        Todo(
            text = "Read the docs.",
            completed = false
        )
    )
)

val action = AddTodo(text = "Understand the flow")

// The action being performed (adding a todo)
val nextState = todoReducer(previousState, action)
```

Note that a reducer is a pure function. It only _computes_ the next state. It should be completely
predictable: calling it with the same inputs many times should produce the same outputs. It
shouldn't perform any side effects like API calls or router transitions. These should happen before
an action is dispatched.

3. **The root reducer may combine the output of multiple reducers into a single state tree.**

How you structure the root reducer is completely up to you. 

//TODO - reference `combineReducers` & registering reducers

4. **The Redux store saves the complete state tree returned by the root reducer.**

This new tree is now the next state of your app! Every listener registered with 
[`store.subscribe(listener)`](../api/Store.md#subscribelistener) will now be invoked; listeners may 
call [`store.getState()` or `store.state`](../api/Store.md#getState) to get the current state.

Now, the UI can be updated to reflect the new state. 

## Next Steps

//TODO link to how to connect to UI in realish app

> ##### Note for Advanced Users
>
> If you're already familiar with the basic concepts and have previously completed this tutorial, 
> don't forget to check out [async flow](../advanced/AsyncFlow.md) in the 
> [advanced tutorial](../advanced/README.md) to learn how middleware transforms 
> [async actions](../advanced/AsyncActions.md) before they reach the reducer.
