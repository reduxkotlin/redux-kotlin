---
id: core-concepts
title: Core Concepts
sidebar_label: Core Concepts
hide_title: true
---

# Core Concepts

Imagine your app’s state is described as a plain object. For example, the state of a todo app might
look like this:

```kotlin
data class AppState(
    val todos: List<Todo>
    val visibilityFilter: VisibilityFilter
)
```

This object is like a “model” except all fields are vals. This is so that different parts of the
code can’t change the state arbitrarily, causing hard-to-reproduce bugs.

To change something in the state, you need to dispatch an action. An action is a plain data Kotlin
class (notice how we don’t introduce any magic?) that describes what happened. Here are a few
example actions:

```kotlin
data class AddTodo(val text: String)
data class ToggleTodo(val index: Int)
data class SetVisibilityFilter(val filter: VisibilityFilter)

store.dispatch(AddTodo("Write my awesome app"))
store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_ALL))
```

Enforcing that every change is described as an action lets us have a clear understanding of what’s
going on in the app. If something changed, we know why it changed. Actions are like breadcrumbs of
what has happened. Finally, to tie state and actions together, we write a function called a reducer.
Again, nothing magical about it—it’s just a function that takes state and action as arguments, and
returns the next state of the app. It would be hard to write such a function for a big app, so we
write smaller functions managing parts of the state:

```kotlin
fun visibilityFilterReducer(state: VisibilityFilter, action: Any) =
    when (action) {
        is SetVisibilityFilter -> action.visibilityFilter
        else -> state
    }

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
```

And we write another reducer that manages the complete state of our app by calling those two
reducers for the corresponding state keys. This is known as the root reducer:

```kotlin
fun rootReducer(state: AppState, action: Any) = AppState(
    todos = todosReducer(state.todos, action),
    visibilityFilter = visibilityFilterReducer(state.visibilityFilter, action)
)
```

This is basically the whole idea of Redux. Note that we haven’t used any Redux APIs. It comes with a
few utilities to facilitate this pattern, but the main idea is that you describe how your state is
updated over time in response to action objects, and 90% of the code you write is just plain Kotlin,
with no use of Redux itself, its APIs, or any magic.
