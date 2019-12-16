package org.reduxkotlin.utils

const val UNKNOWN_THREAD_NAME = "UNKNOWN_THREAD_NAME"

/**
 * Returns the name of the current thread.
 */
expect fun getThreadName(): String