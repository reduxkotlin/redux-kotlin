package org.reduxkotlin.utils

/**
 * Returns the name of the current thread.
 *
 * Kotlin/Wasm-JS is single-threaded (no Worker shared-state); the main
 * task is the only execution context. Mirrors the Kotlin/JS actual.
 */
public actual fun getThreadName(): String = "main"
