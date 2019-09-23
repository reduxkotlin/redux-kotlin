---
id: reducers
title: Reducers
sidebar_label: Reducers
hide_title: true
---

# Reducers

**Reducers** specify how the application's state changes in response to [actions](./Actions.md) sent
to the store. Remember that actions only describe _what happened_, but don't describe how the 
application's state changes.

## Designing the State Shape

In Redux, all the application state is stored as a single object. It's a good idea to think of its
shape before writing any code. What's the minimal representation of your app's state as an object?

For our todo app, we want to store two different things:

- The currently selected visibility filter.
- The actual list of todos.

You'll often find that you need to store some data, as well as some UI state, in the state tree.
This is fine, but try to keep the data separate from the UI state.

```kotlin
data class AppState(
    val visibilityFilter: VisibilityFilters = VisibilityFilters.SHOW_ALL,
    val todos: List<Todo> = listOf(
        Todo(text = "Consider using Redux",
            completed = true),
        Todo(text = "Keep all state in a single tree",
            completed = false)
    )
)
```

> ##### Note on Relationships
>
> In a more complex app, you're going to want different entities to reference each other. We suggest
> that you keep your state as normalized as possible, without any nesting. Keep every entity in an
> object stored with an ID as a key, and use IDs to reference it from other entities, or lists.
> Think of the app's state as a database. Adding functions to the `AppState` object is a good place
> to put logic for accessing these relationships.

## Handling Actions

Now that we've decided what our state object looks like, we're ready to write a reducer for it. The
reducer is a pure function that takes the previous state and an action, and returns the next state.

```kotlin
typealias Reducer<State> = (state: State, action: Any) -> State
```

There are at least 2 ways of defining reducers:

1) functions
```kotlin
fun reducer(state: AppState, action: Any): AppState {
   //do work 
   return newAppState
}
```

2) function objects -TODO is wording correct?
```kotlin
val reducer: Reducer<AppState> = {state, action ->
    //do work
    newAppState
}
```

It's called a reducer because it's the type of function you would pass to
[`Array.reduce(operation: (acc: S, T) -> S)`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/reduce.html).
It's very important that the reducer stays pure. Things you should **never** do inside a reducer:

- Mutate its arguments;
- Perform side effects like API calls and routing transitions;
- Call non-pure functions, e.g. `Date.now()` or `Math.random()`.

We'll explore how to perform side effects in the [advanced walkthrough](../advanced/README.md). For 
now, just remember that the reducer must be pure. **Given the same arguments, it should calculate 
the next state and return it. No surprises. No side effects. No API calls. No mutations. Just a 
calculation.**

With this out of the way, let's start writing our reducer by gradually teaching it to understand the
[actions](Actions.md) we defined earlier.

We'll start by specifying the initial state. Initial state can be defined in a few ways. In the
example above we supplied default values to the `AppState` constructor, so that may be used. Another
method is to use a `val` in the companion object of `AppState`.

> ##### Note of differences from JS Redux
> JS Redux allows initializing the store without an preloaded state by omitting the `preloadedState`
> parameter from the `createStore` function. ReduxKotlin requires a preloaded state to be passed to
> `createStore`. This allows us to use a nonnullable type for State.
```kotlin
val store = createStore(reducer, INITIAL_STATE)
```

Now let's handle `SET_VISIBILITY_FILTER`. All it needs to do is to change `visibilityFilter` on the
state. Easy:

```kotlin

fun todosReducer(state: AppState, action: Any) = 
    when (action) {
        is SetVisibilityFilter -> state.copy(visibilityFilter = action.visibilityFilter)
        else -> state
    }
```

Note that:

1. **We don't mutate the `state`.** We create a copy with the `copy` function on data classes. New
   state objects can be constructed with the constructor of the State class as well, but the `copy`
   method is generally a convenient way to change just part of the state.

2. **We return the previous `state` in the `else` case.** It's important to return the previous
   `state` for any unknown action.

3. **Note that the new state is returned from the function above. In kotlin lambdas the last
   expression is the return value and the `return` keyword is not used.**


## Handling More Actions

We have two more actions to handle! Just like we did with `SetVisibilityFilter`, we'll extend our
reducer to handle `AddTodo`.

```kotlin
fun todosReducer(state: AppState, action: Any) =
    when (action) {
        is SetVisibilityFilter -> state.copy(visibilityFilter = action.visibilityFilter)
        is AddTodo -> state.copy(todos = state.todos.plus(
                        Todo(
                            text = action.text,
                            completed = false
                            )
                        ))
        else -> state
    }
```

Just like before, we never write directly to `state` or its fields, and instead we return new
objects. The new `todos` is equal to the old `todos` concatenated with a single new item at the end.
The fresh todo was constructed using the data from the action.

Finally, the implementation of the `ToggleTodo` handler shouldn't come as a complete surprise:

```kotlin
    is ToggleTodo -> state.mapIndexed { index, todo ->
            if (index == action.index) {
                todo.copy(completed = !todo.completed)
            } else {
                todo
            }
        }
```

> ##### Note on Reducer naming
> In JS Redux it is helpful to name reducer functions the same name as the state field they will
> handle due to JS features that allow the `combineReducers` function to assign reducers to fields.
> For example:
> ```js
> const todoApp = combineReducers({
>   visibilityFilter,
>   todos
> })
> ```
> This is not a feature in ReduxKotlin, due to its statically-type nature. It is recommended to use
> a more descriptive name, such as `TodoReducer` to clearly identify the function.


Because we want to update a specific item in the array without resorting to mutations, we have to create a new array with the same items except the item at the index. Just remember to never assign to anything inside the `state` unless you clone it first.

## Splitting Reducers

Here is our code so far. It is rather verbose:
```kotlin
fun todosReducer(state: AppState, action: Any) = 
    when (action) {
        is SetVisibilityFilter -> state.copy(visibilityFilter = action.visibilityFilter)
        is AddTodo -> state.copy(todos = state.todos.plus(
                        Todo(
                            text = action.text,
                            completed = false
                            )
                        ))
        is ToggleTodo -> state.copy(
                todos = state.todos.mapIndexed { todo, index -> 
                    if (index == action.index) {
                        todo.copy(completed = !todo.completed)
                    } else {
                        todo
                    }
                }
            )
        else -> state
    }
```
One can see how this would quickly become a huge, bloated function.

Is there a way to make it easier to comprehend? It seems like `todos` and `visibilityFilter` are
updated completely independently. Sometimes state fields depend on one another and more
consideration is required, but in our case we can easily split updating `todos` into a separate
function:

```kotlin
fun todosReducer(state: List<Todos>, action: Any) = 
    when(action) {
        is AddTodo -> state.plus(
                        Todo(
                            text = action.text,
                            completed = false
                            )
                        )
        is ToggleTodo -> state.mapIndexed { todo, index -> 
                    if (index == action.index) {
                        todo.copy(completed = !todo.completed)
                    } else {
                        todo
                    }
                }
        else -> state
    }
```

Note that `todosReducer` also accepts `state`—but `state` is a List<Todo>! Now `todoReducer` gives
`todos` just a slice of the state to manage, and `todosReducer` knows how to update just that slice.
**This is called _reducer composition_, and it's the fundamental pattern of building Redux apps.**

```kotlin
fun visibilityFilterReducer(state: VisibilityFilter, action: Any): VisibilityFilter = 
    when (action) {
        is SetVisibilityFilter -> action.visibilityFilter
        else -> state
    }
```

Now we can rewrite the main reducer as a function that calls the reducers managing parts of the
state, and combines them into a single object.

```kotlin
fun todosReducer(state: List<Todos>, action: Any): List<Todos> =
    when(action) {
        is AddTodo -> state.plus(
                        Todo(
                            text = action.text,
                            completed = false
                            )
                        )
        is ToggleTodo -> state.mapIndexed { todo, index -> 
                    if (index == action.index) {
                        todo.copy(completed = !todo.completed)
                    } else {
                        todo
                    }
                }
        else -> state
    }

fun visibilityFilterReducer(state: VisibilityFilter, action: Any): VisibilityFilter =
    when (action) {
        is SetVisibilityFilter -> action.visibilityFilter
        else -> state
    }
    
fun rootReducer(state: AppState, action: Any) = AppState(
    todos = todosReducer(state.todos, action),
    visibilityFilter = visibilityFilterReducer(state.visibilityFilter, action)
)
```

**Note that each of these reducers is managing its own part of the global state. The `state`
parameter is different for every reducer, and corresponds to the part of the state it manages.**

This is already looking good! When the app is larger, we can split the reducers into separate files
and keep them completely independent and managing different data domains.

> ##### Note on combining reducer boilerplate
> Manually wiring together of the reducers does have bring some boilerplate with it. One alternative
> patter for ReduxKotlin is using a [Reducible interface](TODO). How reducers are split is up to you
> and your team, and it is recommended being consistent how this is handled throughout a project. In
> JS Redux `combineReducers` goes a long way to alleviate this boilerplate, however with statically 
> typed Kotlin that function can not be implemented easily. There is the possibility of using
> [generated code and annotations to help in this area](https://trello.com/c/WXS3RRKM/15-make-easy-to-register-reducers).
>
> ReduxKotlin does have a `combineReducers` function, however, it only combines reducers of the same
> state type. It is quite limited compared to the JS `combineReducers`.

## Next Steps

Next, we'll explore how to [create a Redux store](Store.md) that holds the state and takes care of 
calling your reducer when you dispatch an action.
