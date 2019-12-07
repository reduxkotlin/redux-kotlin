package org.reduxkotlin.utils

/**
 * Fallback for platforms that have not been implemented yet.
 * This will allow usage of ReduxKotlin, but not allow
 * thread enforcement.
 * Linux, Win, WASM
 */
actual fun getThreadName(): String = UNKNOWN_THREAD_NAME
