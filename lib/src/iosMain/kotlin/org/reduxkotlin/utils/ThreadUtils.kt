package org.reduxkotlin.utils
import platform.Foundation.NSThread.Companion.currentThread

actual fun getThreadName(): String = currentThread.name ?: "Unknown"
