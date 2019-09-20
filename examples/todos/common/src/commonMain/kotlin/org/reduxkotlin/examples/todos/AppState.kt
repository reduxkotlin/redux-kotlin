package org.reduxkotlin.examples.todos


/**
 * Entire state tree for the app.
 */
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

/**
 * A model used in the app state. Where these types of models are located is up to you and your team
 * It will likely make sense to place separate file in a real project.
 */
data class Todo(
    val text: String,
    val completed: Boolean = false
)

enum class VisibilityFilter {
    SHOW_ALL,
    SHOW_COMPLETED,
    SHOW_ACTIVE
}
