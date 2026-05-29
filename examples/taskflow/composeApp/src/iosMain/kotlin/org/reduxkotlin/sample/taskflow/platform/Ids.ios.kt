package org.reduxkotlin.sample.taskflow.platform

import platform.Foundation.NSUUID

/** Mints a UUID via Foundation's [NSUUID]. */
actual fun newUuid(): String = NSUUID().UUIDString()
