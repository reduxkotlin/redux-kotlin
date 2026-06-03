package org.reduxkotlin.sample.taskflow.infra.platform

import platform.Foundation.NSUUID

/** Mints a UUID via Foundation's [NSUUID]. */
actual fun newUuid(): String = NSUUID().UUIDString()
