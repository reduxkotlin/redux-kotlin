package org.reduxkotlin.examples.todos

data class AddTodo(val text: String, val completed: Boolean = false)
data class ToggleTodo(val index: Int)
data class SetVisibilityFilter(val visibilityFilter: VisibilityFilter)
