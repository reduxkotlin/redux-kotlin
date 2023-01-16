package org.reduxkotlin.utils

internal const val UNKNOWN_THREAD_NAME: String = "UNKNOWN_THREAD_NAME"

/**
 * Returns the name of the current thread.
 */
public expect fun getThreadName(): String

/**
 * Thread name may have '@coroutine#n' appended to it.
 * This strips the suffix so we can compare threads.
 */
public fun stripCoroutineName(threadName: String): String {
    val lastIndex = threadName.lastIndexOf('@')
    return if (lastIndex < 0) {
        threadName
    } else {
        threadName.substring(0, lastIndex)
    }
}
