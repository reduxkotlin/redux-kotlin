package org.reduxkotlin.example.counter

import org.reduxkotlin.StoreEnhancer

/**
 * RELEASE variant: no DevTools. The `redux-kotlin-devtools-core` artifact is a `debugImplementation`
 * dependency and is absent here, so release builds never reference it.
 */
@Suppress("FunctionOnlyReturningConstant") // intentional no-op half of the debug/release split
internal fun devToolsEnhancer(): StoreEnhancer<Int>? = null
