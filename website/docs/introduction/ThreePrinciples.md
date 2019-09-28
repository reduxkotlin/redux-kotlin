---
id: three-principles
title: Three Principles
sidebar_label: Three Principles
hide_title: true
---

# Three Principles

Redux can be described in three fundamental principles:

### Single source of truth

**The [state](../Glossary.md#state) of your whole application is stored in an object tree within a 
single [store](../Glossary.md#store).**

A single state tree also makes it easier to debug or inspect an application; it also enables you to
persist your app's state in development, for a faster development cycle. Some functionality which
has been traditionally difficult to implement - Undo/Redo, for example - can suddenly become
possible to implement, if all of your state is stored in a single tree.

```kotlin
logger.info(store.state)

/* Prints
Appstate(visibilityFilter = "SHOW_ALL", todos = [Todo(text = "Consider using Redux", completed = true)])
 */
```

### State is read-only

**The only way to change the state is to emit an [action](../Glossary.md#action), an object
describing what happened.**

This ensures that neither the views nor the network callbacks will ever write directly to the state.
Instead, they express an intent to transform the state. Because all changes are centralized and
happen one by one in a strict order, there are no subtle race conditions to watch out for. As
actions are just plain objects, they can be logged, serialized, stored, and later replayed for
debugging or testing purposes.

```kotlin
store.dispatch(CompleteTodo(index = 1))

store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_COMPLETED))
```

### Changes are made with pure functions

**To specify how the state tree is transformed by actions, you write pure 
[reducers](../Glossary.md#reducer).**

Reducers are just pure functions that take the previous state and an action, and return the next
state. Remember to return new state objects, instead of mutating the previous state. You can start
with a single reducer, and as your app grows, split it off into smaller reducers that manage
specific parts of the state tree. Because reducers are just functions, you can control the order in
which they are called, pass additional data, or even make reusable reducers for common tasks such as
pagination.

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
    
fun rootReducer(state: AppState, action: Any) = AppState(
    todos = todosReducer(state.todos, action),
    visibilityFilter = visibilityFilterReducer(state.visibilityFilter, action)
)

val store = createStore(::rootReducer, AppState.INITIAL_STATE)
```

That's it! Now you know what Redux is all about.
