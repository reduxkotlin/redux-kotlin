package org.reduxkotlin.utils

/**
 * Returns the name of the current thread.
 */
public actual fun getThreadName(): String = Thread.currentThread().name
