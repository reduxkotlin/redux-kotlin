package org.reduxkotlin.sample.taskflow.app

import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.sample.taskflow.store.debugStoreEnhancer

/**
 * DEBUG variant: installs Redux DevTools as the per-account store enhancer. The
 * `redux-kotlin-devtools` artifact is a `debugImplementation` dependency, so this file (and the
 * devtools classes) exist only in debug builds. The matching `release` source set is a no-op.
 *
 * Run `npx @redux-devtools/cli@4 --port 8000`; on an emulator the host is `10.0.2.2`, on a USB
 * device run `adb reverse tcp:8000 tcp:8000` (host = `localhost`). The "TaskFlow" instance appears
 * once an account's board store is created.
 */
internal fun installDebugTooling() {
    debugStoreEnhancer = {
        devTools(
            DevToolsConfig(
                name = "TaskFlow",
                host = "localhost",
                logger = { println("[DevTools] $it") },
            ),
        )
    }
}
