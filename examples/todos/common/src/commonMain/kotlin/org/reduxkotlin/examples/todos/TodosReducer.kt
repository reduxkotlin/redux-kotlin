package org.reduxkotlin.examples.todos


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
