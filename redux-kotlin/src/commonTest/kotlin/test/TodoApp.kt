package test

import org.reduxkotlin.Reducer
import org.reduxkotlin.TypedReducer

object TodoApp {
    sealed interface TodoAction
    data class AddTodo(val id: String, val text: String) : TodoAction
    object DoNothing : TodoAction
    data class ToggleTodo(val id: String) : TodoAction
    data class Todo(val id: String, val text: String, val completed: Boolean = false)

    data class TodoState(val todos: List<Todo> = listOf())

    val todoReducer: Reducer<TodoState> = { state: TodoState, action: Any ->
        when (action) {
            is AddTodo -> state.copy(todos = state.todos.plus(Todo(action.id, action.text, false)))
            is ToggleTodo -> state.copy(
                todos = state.todos.map {
                    if (it.id == action.id) {
                        it.copy(completed = !it.completed)
                    } else {
                        it
                    }
                }
            )

            else -> state
        }
    }
    val typedTodoReducer: TypedReducer<TodoState, TodoAction> = todoReducer
}
