---
id: createstore
title: createStore
sidebar_label: createStore
---

# `createStore(reducer, preloadedState, enhancer)`

Creates a Redux [store](./store-api) that holds the complete state tree of your app.  
There should only be a single store in your app.

If using `createStore` directly, it will not be threadsafe.

On multi-threaded platforms it is ***STRONGLY*** recommended to use a thread-safe variant:
a concurrent store (`createConcurrentStore` from `redux-kotlin-concurrent` — lock-free reads,
serialized writes; see [Threading](../introduction/threading)) or the fully-synchronized
[`createThreadSafeStore()`](./createthreadsafestore).

The rest of this doc applies to all `createStore` functions.

#### Arguments

1. `reducer` _(Reducer)_: A [reducing function](../glossary#reducer) that returns the next 
   [state tree](../glossary#state), given the current state tree and an 
   [action](../glossary#action) to handle.

2. [`preloadedState`] _(State)_: The initial state. You may optionally specify it to hydrate the 
   state from the server, or to restore a previously serialized user session. Seeding restored
   state here (rather than dispatching it after creation) means the first read already sees the
   rehydrated state. For restoring state across rotation and process death in Compose apps — and
   the equivalent `preloadedState` parameter on the routed-store factories (`createModelStore` /
   `createConcurrentModelStore`) — see
   [Saving state across rotation & process death](../advanced/compose-integration#saving-state-across-rotation--process-death).

3. [`enhancer`] _(StoreEnhancer)_: The store enhancer. You may optionally specify it to enhance the 
   store with third-party capabilities such as middleware, time travel, persistence, etc. The only 
   store enhancer that ships with Redux is [`applyMiddleware()`](./applymiddleware).

#### Returns

([_`Store`_](./store-api)): An object that holds the complete state of your app. The only way to change
its state is by [dispatching actions](./store-api#dispatchaction-any-any). You may also 
[subscribe](./store-api#subscribelistener-storesubscriber) to the changes to its state to update the UI.

#### Example

```kotlin
fun todosReducer(state: List<Todo>, action: Any) =
    when (action) {
        is AddTodo -> state.plus(Todo(action.text))
        is ToggleTodo -> state.mapIndexed { index, todo ->
            if (index == action.index) {
                todo.copy(completed = !todo.completed)
            } else {
                todo
            }
        }
        else -> state
    }

val store = createStore(::todosReducer, AppState.INITIAL_STATE)


fun main() {
    store.dispatch(AddTodoAction(text = "Read the docs"))

    logger.debug(store.state)
    // AppState(list = [ 'Use Redux', 'Read the docs' ])
}
```

#### Tips

- Don't create more than one store in an application! Instead, use 
  [`combineReducers`](/basics/reducers) or combine your reducers in code to create a single root 
  reducer out of many.

- It is up to you to choose the state format and structure. Data classes work well as they have
  `toString()` and `copy()`. Also use immutable `val` rather than `var` except where unavoidable.

- For universal apps that run on the server, create a store instance with every request so that they
  are isolated. Dispatch a few data fetching actions to a store instance and wait for them to
  complete before rendering the app on the server.

- When a store is created, Redux dispatches a dummy action to your reducer to populate the store
  with the initial state. You are not meant to handle the dummy action directly.

- To apply multiple store enhancers, you may use [`compose()`](./compose).
