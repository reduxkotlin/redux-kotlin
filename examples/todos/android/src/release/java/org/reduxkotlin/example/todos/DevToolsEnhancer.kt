package org.reduxkotlin.example.todos

import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.examples.todos.AppState

/**
 * RELEASE variant: no DevTools. The `redux-kotlin-devtools` artifact is a `debugImplementation`
 * dependency and is absent here, so release builds never reference it.
 */
@Suppress("FunctionOnlyReturningConstant") // intentional no-op half of the debug/release split
internal fun devToolsEnhancer(): StoreEnhancer<AppState>? = null
