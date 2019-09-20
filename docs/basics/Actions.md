---
id: actions
title: Actions
sidebar_label: Actions
hide_title: true
---

# Actions

First, let's define some actions.

**Actions** are payloads of information that send data from your application to your store. They are the _only_ source of information for the store. You send them to the store using [`store.dispatch()`](../api/Store.md#dispatchaction).

Here's an example action which represents adding a new todo item:

```kotlin
data class AddTodoAction(val text: String)
```

```kotlin
AddTodoAction("Build my first Redux app")
```

Actions are plain objects. 

> ##### Note on Javascript Redux
>
> In Javascript Redux, Actions must have a `type` property that indicates the type of action being performed.
> ReduxKotlin leverages the type system and the `type` field is not needed.

We'll add one more action type to describe a user ticking off a todo as completed. We refer to a particular todo by `index` because we store them in an array. In a real app, it is wiser to generate a unique ID every time something new is created.

```kotlin
data class ToggleTodoAction(val index: Int)
```

It's a good idea to pass as little data in each action as possible. For example, it's better to pass `index` than the whole todo object.

Finally, we'll add one more action type for changing the currently visible todos.

```kotlin
data class SetVisibilityFilterAction(val filter: VisibilityFilter)
```

> ##### Note on Action Creators
> In the Javascript world there are ["Action Creators"](https://redux.js.org/basics/actions#action-creators).  There are just functions that create actions.
> Generally this pattern is not needed with ReduxKotlin due ease of use and conciseness of Kotlin's constructors, however if you find them helpful, go for it!  


## Source Code

### `Actions.kt`

```kotlin
/*
 * action types
 */
data class AddTodoAction(val text: String)
data class ToggleTodoAction(val index: Int)
data class SetVisibilityFilterAction(val filter: VisibilityFilter)

/*
 * other declarations
 */

enum class VisibilityFilters {
  SHOW_ALL,
  SHOW_COMPLETED,
  SHOW_ACTIVE
}
```

## Next Steps

Now let's [define some reducers](Reducers.md) to specify how the state updates when you dispatch these actions!
