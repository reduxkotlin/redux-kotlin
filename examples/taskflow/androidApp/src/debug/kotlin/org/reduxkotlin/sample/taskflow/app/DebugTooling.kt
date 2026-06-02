package org.reduxkotlin.sample.taskflow.app

import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.sample.taskflow.store.debugStoreEnhancer

/**
 * DEBUG variant: installs Redux DevTools as the per-account store enhancer. The
 * `redux-kotlin-devtools-core` artifact is a `debugImplementation` dependency, so this file (and
 * the devtools classes) exist only in debug builds. The matching `release` source set is a no-op.
 *
 * Records in-process into the `DevToolsHub`; the "TaskFlow" instance appears once an account's
 * board store is created. To stream to the external monitor (`npx @redux-devtools/cli@4 --port
 * 8000`), add `redux-kotlin-devtools-remote` and start a `RemoteOutput` against the session.
 */
internal fun installDebugTooling() {
    debugStoreEnhancer = {
        devTools(
            DevToolsConfig(
                name = "TaskFlow",
                logger = { println("[DevTools] $it") },
            ),
        )
    }
}
