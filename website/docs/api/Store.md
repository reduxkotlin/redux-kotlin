---
id: store
title: Store
sidebar_label: Store
hide_title: true
---

# Store

A store holds the whole [state tree](../Glossary.md#state) of your application.
The only way to change the state inside it is to dispatch an [action](../Glossary.md#action) on it.

A store is just a plain object that contains your current state and a 4 functions.
To create it, pass your root [reducing function](../Glossary.md#reducer) to 
[`createStore`](createStore.md).  The store has [same thread enforcement](../introduction/threading), meaning 
its methods must be called from the same thread where the store was created.

> ##### A Note for Flux Users
>
> If you're coming from Flux, there is a single important difference you need to understand. Redux
> doesn't have a Dispatcher or support many stores. **Instead, there is just a single store with a
> single root [reducing function](../Glossary.md#reducer).** As your app grows, instead of adding 
> stores, you split the root reducer into smaller reducers independently operating on the different 
> parts of the state tree. You can use a helper like `combineReducers` to 
> combine them. There is also an opportunity to use 
> [annotation processors, or Kotlin compiler plugins, to make combining reducers easier](https://trello.com/c/WXS3RRKM/15-make-easy-to-register-reducers).

### Store Methods

- [`getState()`](#getstate-_or_-state-property)
- [`dispatch(action)`](#dispatchaction-any-any)
- [`subscribe(listener)`](#subscribelistener-storesubscriber)
- [`replaceReducer(nextReducer)`](#replacereducernextreducer-reducer-state-unit)

## Store Methods

### getState() _or_ `state` property

Returns the current state tree of your application.
It is equal to the last value returned by the store's reducer.
`state` property has been included since it is more idiomatic Kotlin

#### Returns

_(State)_: The current state tree of your application with the generic `State` type supplied to the store.

<hr>

### dispatch(action: Any): Any

Dispatches an action. This is the only way to trigger a state change.

The store's reducing function will be called with the current [`getState()`](#getState) result and 
the given `action` synchronously. Its return value will be considered the next state. It will be 
returned from [`getState()`](#getState) from now on, and the change listeners will immediately be 
notified.

> ##### A Note for Flux Users
>
> If you attempt to call `dispatch` from inside the [reducer](../Glossary.md#reducer), it will throw 
> with an error saying “Reducers may not dispatch actions.” This is similar to “Cannot dispatch in a
> middle of dispatch” error in Flux, but doesn't cause the problems associated with it. In Flux, a 
> dispatch is forbidden while Stores are handling the action and emitting updates. This is 
> unfortunate because it makes it impossible to dispatch actions from component lifecycle hooks or 
> other benign places.
>
> In Redux, subscriptions are called after the root reducer has returned the new state, so you _may_
> dispatch in the subscription listeners. You are only disallowed to dispatch inside the reducers
> because they must have no side effects. If you want to cause a side effect in response to an
> action, the right place to do this is in the potentially async
> [action creator](../Glossary.md#action-creator).

#### Arguments

1. `action` (`Any`): A plain object describing the change that makes sense for your application.
   Actions are the only way to get data into the store, so any data, whether from the UI events,
   network callbacks, or other sources such as WebSockets needs to eventually be dispatched as
   actions. Actions can be defined as classes or data classes. How they are grouped and where they
   are defined is up to you and your team. Javascript Redux requires a `type` string field to denote
   the type of action, but ReduxKotlin has no such requirement. In Kotlin it is recommended to use
   separate classes for each action.

#### Returns

(`Any`): The dispatched action (see notes).

#### Notes

<sup>†</sup> The “vanilla” store implementation you get by calling [`createStore`](createStore.md) 
only supports plain object actions and hands them immediately to the reducer.

However, if you wrap [`createStore`](createStore.md) with [`applyMiddleware`](applyMiddleware.md), 
the middleware can interpret actions differently, and provide support for dispatching 
[async actions](../Glossary.md#async-action). Async actions are usually asynchronous primitives like 
thunks.

Middleware is created by the community and does not ship with Redux by default. You need to
explicitly install packages like [redux-thunk](https://github.com/reduxkotlin/redux-kotlin-thunk). 
You may also create your own middleware.

To learn how to describe asynchronous API calls, read the current state inside action creators,
perform side effects, or chain them to execute in a sequence, see the examples for
[`applyMiddleware`](applyMiddleware.md).

#### Example

```kotlin
val store = createThreadSafeStore(todos, AppState(list = listOf("Use Redux")))

data class AddTodo(
    val text: String
)

store.dispatch(AddTodo("Read the docs"))
store.dispatch(AddTodo("Read about the middleware"))
```

<hr>

### subscribe(listener: StoreSubscriber)

Adds a change listener. It will be called any time an action is dispatched, and some part of the
state tree may potentially have changed. You may then call [`getState()`](#getState) to read the 
current state tree inside the callback.

You may call [`dispatch()`](#dispatchaction) from a change listener, with the following caveats:

1. The listener should only call [`dispatch()`](#dispatchaction) either in response to user actions 
   or under specific conditions (e. g. dispatching an action when the store has a specific field). 
   Calling [`dispatch()`](#dispatchaction) without any conditions is technically possible, however 
   it leads to an infinite loop as every [`dispatch()`](#dispatchaction) call usually triggers the 
   listener again.

2. The subscriptions are snapshotted just before every [`dispatch()`](#dispatchaction) call. If you 
   subscribe or unsubscribe while the listeners are being invoked, this will not have any effect on 
   the [`dispatch()`](#dispatchaction) that is currently in progress. However, the next 
   [`dispatch()`](#dispatchaction) call, whether nested or not, will use a more recent snapshot of
   the subscription list.

3. The listener should not expect to see all state changes, as the state might have been updated
   multiple times during a nested [`dispatch()`](#dispatchaction) before the listener is called. It 
   is, however, guaranteed that all subscribers registered before the 
   [`dispatch()`](#dispatchaction) started will be called with the latest state by the time it 
   exits.

It is a low-level API. Most likely, instead of using it directly, you may want create a base class
or delegate that manages subscriptions. One solution available now is [Presenter-middleware](todo), 
and it is likely other approaches will develop.

To unsubscribe the change listener, invoke the function returned by `subscribe`.

#### Arguments

1. `listener` (`() -> Unit`): The callback to be invoked any time an action has been dispatched, and
   the state tree might have changed. You may call [`getState()`](#getState) inside this callback to 
   read the current state tree. It is reasonable to expect that the store's reducer is a pure 
   function, so you may compare references to some deep path in the state tree to learn whether its 
   value has changed.

##### Returns

(`StoreSubscription`): A function(`()-> Unit`) that unsubscribes the change listener.

##### Example

```kotlin
fun select(state) {
  return state.some.deep.property
}

var currentValue: String?
fun handleChange() {
  var previousValue = currentValue
  currentValue = store.some.deep.property

  if (previousValue != currentValue) {
    console.log("Some deep nested property changed from $previousValue to $currentvalue")
  }
}

val unsubscribe = store.subscribe(handleChange)

//later when no longer needed
unsubscribe()
```

<hr>

### replaceReducer(nextReducer: Reducer<State>): Unit

Replaces the reducer currently used by the store to calculate the state.

It is an advanced API. In JS Redux it is needed if your app implements code splitting, and you want
to load some of the reducers dynamically. You might also need this if you implement a hot reloading
mechanism for Redux.

#### Arguments

1. `nextReducer` (`Reducer<State>`) The next reducer for the store to use.
