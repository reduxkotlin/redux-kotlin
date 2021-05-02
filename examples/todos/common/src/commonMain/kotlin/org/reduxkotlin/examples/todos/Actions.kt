package org.reduxkotlin.examples.todos

import kotlin.js.JsExport

@JsExport
data class AddTodo(val text: String, val completed: Boolean = false)
@JsExport
data class ToggleTodo(val index: Int)
@JsExport
data class SetVisibilityFilter(val visibilityFilter: VisibilityFilter)
