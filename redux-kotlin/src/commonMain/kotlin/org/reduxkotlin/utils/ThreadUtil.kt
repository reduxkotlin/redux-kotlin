package org.reduxkotlin.utils

const val UNKNOWN_THREAD_NAME = "UNKNOWN_THREAD_NAME"

/**
 * Returns the name of the current thread.
 */
expect fun getThreadName(): String

/**
 * Thread name may have '@coroutine#n' appended to it.
 * This strips the suffix so we can compare threads.
 */
fun stripCoroutineName(threadName: String): String {
    val lastIndex = threadName.lastIndexOf('@')
    return if (lastIndex < 0) threadName
    else threadName.substring(0, lastIndex)
}
