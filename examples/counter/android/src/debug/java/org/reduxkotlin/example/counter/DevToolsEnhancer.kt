package org.reduxkotlin.example.counter

import android.util.Log
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools

/**
 * DEBUG variant: attaches Redux DevTools. The `redux-kotlin-devtools` artifact is a
 * `debugImplementation` dependency, so this file (and the devtools classes) exist only in
 * debug builds. The matching `release` source set returns `null`.
 */
internal fun devToolsEnhancer(): StoreEnhancer<Int>? = devTools(
    DevToolsConfig(
        name = "Counter",
        host = "localhost",
        logger = { Log.d("DevTools", it) },
    ),
)
