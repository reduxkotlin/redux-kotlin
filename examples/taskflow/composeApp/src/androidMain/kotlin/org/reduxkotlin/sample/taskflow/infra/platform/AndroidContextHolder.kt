package org.reduxkotlin.sample.taskflow.infra.platform

import android.content.Context

/**
 * Holds the application [Context] for the Android platform shims (SQLite driver path, dynamic
 * color). Set once from the host's `Activity`/`Application` (see `MainActivity.onCreate`) before
 * any shim is used.
 */
object AndroidContextHolder {
    /** The application context, assigned by the Android host before first shim use. */
    lateinit var appContext: Context
}
