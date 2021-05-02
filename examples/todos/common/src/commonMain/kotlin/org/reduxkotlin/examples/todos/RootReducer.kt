package org.reduxkotlin.examples.todos

import kotlin.js.JsExport

/**
 * The root reducer of the app.  This is the reducer passed to `createStore()`.
 * Notice that sub-states are delegated to other reducers.
 */
@JsExport
fun rootReducer(state: AppState, action: Any) = AppState(
    todos = todosReducer(state.todos, action),
    visibilityFilter = visibilityFilterReducer(state.visibilityFilter, action)
)
