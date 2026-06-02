package org.reduxkotlin.example.todos

import android.util.Log
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.examples.todos.AppState

/**
 * DEBUG variant: attaches Redux DevTools. The `redux-kotlin-devtools-core` artifact is a
 * `debugImplementation` dependency, so this file (and the devtools classes) exist only in
 * debug builds. The matching `release` source set returns `null`.
 *
 * Records in-process into the `DevToolsHub`. To stream to the external Redux DevTools monitor,
 * add `redux-kotlin-devtools-remote` and start a `RemoteOutput` against this store's session.
 */
internal fun devToolsEnhancer(): StoreEnhancer<AppState>? = devTools(
    DevToolsConfig(
        name = "Todos",
        logger = { Log.d("DevTools", it) },
    ),
)
