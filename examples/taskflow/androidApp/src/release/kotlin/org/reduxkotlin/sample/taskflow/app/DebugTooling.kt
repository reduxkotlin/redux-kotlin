package org.reduxkotlin.sample.taskflow.app

/**
 * RELEASE variant: no DevTools. The `redux-kotlin-devtools-core` artifact is a `debugImplementation`
 * dependency and is absent here, so release builds never reference it.
 */
internal fun installDebugTooling() {
    // No-op: DevTools is debug-only.
}
