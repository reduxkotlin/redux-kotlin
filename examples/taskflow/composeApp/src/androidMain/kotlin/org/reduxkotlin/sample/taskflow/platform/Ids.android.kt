package org.reduxkotlin.sample.taskflow.platform

import java.util.UUID

/** Mints a UUID via [java.util.UUID]. */
actual fun newUuid(): String = UUID.randomUUID().toString()
