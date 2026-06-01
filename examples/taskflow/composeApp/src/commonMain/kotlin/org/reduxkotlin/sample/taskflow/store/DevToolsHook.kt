package org.reduxkotlin.sample.taskflow.store

import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.multimodel.ModelState

/**
 * Optional store-enhancer provider installed by a build that wants Redux DevTools attached to each
 * per-account store. It is `null` by default, so production/release builds link no devtools code.
 *
 * A debug build installs it before the first account store is created — see the androidApp's
 * `src/debug` `installDebugTooling()`. Kept as a plain settable hook (rather than a `commonMain`
 * dependency on `redux-kotlin-devtools`) so the devtools artifact stays a `debugImplementation`
 * concern of the platform application module, not of this shared module.
 */
public var debugStoreEnhancer: (() -> StoreEnhancer<ModelState>?)? = null
