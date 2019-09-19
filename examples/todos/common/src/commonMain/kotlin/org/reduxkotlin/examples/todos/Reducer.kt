package org.reduxkotlin.examples.todos

import org.reduxkotlin.Reducer
import org.reduxkotlin.createStore


data class AddTodo(val text: String, val completed: Boolean = false)
data class ToggleTodo(val index: Int)
data class SetVisibilityFilter(val visibilityFilter: VisibilityFilter)

data class Todo(val text: String, val completed: Boolean = false)

enum class VisibilityFilter {
    SHOW_ALL,
    SHOW_COMPLETED,
    SHOW_ACTIVE
}

data class AppState(
    val todos: List<Todo> = listOf(),
    val visibilityFilter: VisibilityFilter = VisibilityFilter.SHOW_ALL
) {
    val visibleTodos: List<Todo>
        get() = getVisibleTodos(visibilityFilter)

    private fun getVisibleTodos(visibilityFilter: VisibilityFilter) = when (visibilityFilter) {
        VisibilityFilter.SHOW_ALL -> todos
        VisibilityFilter.SHOW_ACTIVE -> todos.filter { !it.completed }
        VisibilityFilter.SHOW_COMPLETED -> todos.filter { it.completed }
    }
}

val todosReducer: Reducer<List<Todo>> = { state, action ->
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
}

val visibilityFilterReducer: Reducer<VisibilityFilter> = { state, action ->
    when (action) {
        is SetVisibilityFilter -> action.visibilityFilter
        else -> state
    }
}

val rootReducer: Reducer<AppState> = { state, action ->
    AppState(
        todos = todosReducer(state.todos, action),
        visibilityFilter = visibilityFilterReducer(state.visibilityFilter, action)
    )
}

val store = createStore(rootReducer, AppState())