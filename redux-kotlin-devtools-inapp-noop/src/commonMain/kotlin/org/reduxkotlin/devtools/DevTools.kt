// Mirrors core's DevTools.kt — file name kept identical so the JVM file facade (`DevToolsKt`)
// matches the debug artifact's.
package org.reduxkotlin.devtools

import org.reduxkotlin.StoreEnhancer

/** No-op enhancer: returns an identity store enhancer (no recording, no hub). */
@Suppress("UnusedParameter")
public fun <State> devTools(config: DevToolsConfig = DevToolsConfig()): StoreEnhancer<State> =
    { storeCreator -> storeCreator }
