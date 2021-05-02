package org.reduxkotlin.examples.todos

import kotlin.js.JsExport

@JsExport
fun visibilityFilterReducer(state: VisibilityFilter, action: Any) =
    when (action) {
        is SetVisibilityFilter -> action.visibilityFilter
        else -> state
    }
