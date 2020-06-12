---
id: api-reference
title: API Reference
sidebar_label: API Reference
hide_title: true
---

# API Reference

The Redux API surface is tiny. Redux defines a set of contracts for you to implement (such as 
[reducers](../Glossary.md#reducer)) and provides a few helper functions to tie these contracts 
together.

This section documents the complete Redux API. Keep in mind that Redux is only concerned with
managing the state. In a real app, you'll also want to bind the state to the UI. There are several
approaches to binding state to the UI, and these docs will evolve as patterns emerge for
multiplatform. One approach is the [Presenter-middleware](TODO)


### Typealiases 
ReduxKotlin keeps the same type definitions as Javascript Redux so it will be consistent, and
possibly share code with the web. Typealiases are used to define all the types and all are contained
in
[Definitions.kt](https://github.com/reduxkotlin/redux-kotlin/blob/master/lib/src/commonMain/kotlin/org/reduxkotlin/Definitions.kt).
Generics are used for the state, and `Any` is used for the action type.

__Definitions.kt__
```kotlin
/**
 * See also https://github.com/reactjs/redux/blob/master/docs/Glossary.md#reducer
 */
typealias Reducer<State> = (state: State, action: Any) -> State

/**
 * Reducer for a particular subclass of actions. Useful for Sealed classes &
 * exhaustive when statements. See [reducerForActionType].
 */
typealias ReducerForActionType<TState, TAction> = (state: TState, action: TAction) -> TState

typealias GetState<State> = () -> State
typealias StoreSubscriber = () -> Unit
typealias StoreSubscription = () -> Unit
typealias Dispatcher = (Any) -> Any
// Enhancer is type Any? to avoid a circular dependency of types.
typealias StoreCreator<State> = (
    reducer: Reducer<State>,
    initialState: State,
    enhancer: Any?
) -> Store<State>

/**
 * Take a store creator and return a new enhanced one.
 * See https://github.com/reactjs/redux/blob/master/docs/Glossary.md#store-enhancer
 */
typealias StoreEnhancer<State> = (StoreCreator<State>) -> StoreCreator<State>

/**
 *  https://github.com/reactjs/redux/blob/master/docs/Glossary.md#middleware
 */
typealias Middleware<State> = (store: Store<State>) -> (next: Dispatcher) -> (action: Any) -> Any


data class Store<State>(
    val getState: GetState<State>,
    var dispatch: Dispatcher,
    val subscribe: (StoreSubscriber) -> StoreSubscription,
    val replaceReducer: (Reducer<State>) -> Unit
) {
    val state: State
        get() = getState()
}
```

### Top-Level Functions

- [createStore(reducer: Reducer, preloadedState: State, enhancer: StoreEnhancer)](createStore.md)
- [createThreadSafeStore(reducer: Reducer, preloadedState: State, enhancer: StoreEnhancer)](createThreadSafeStore.md)
- [applyMiddleware(...middlewares)](applyMiddleware.md)
- [compose(...functions)](compose.md)

### Store API

- [Store](Store.md)
  - [getState()](Store.md#getState) - also available as [state] property
  - [dispatch(action)](Store.md#dispatchaction)
  - [subscribe(listener)](Store.md#subscribelistener)
  - [replaceReducer(nextReducer)](Store.md#replacereducernextreducer)
