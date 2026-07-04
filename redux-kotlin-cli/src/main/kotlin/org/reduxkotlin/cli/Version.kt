package org.reduxkotlin.cli

import java.util.Properties

/** The `rk` version, read from the `rk-version.properties` resource stamped at build time. */
internal val RK_VERSION: String =
    RkCommand::class.java.getResourceAsStream("/rk-version.properties")
        ?.use { stream -> Properties().apply { load(stream) }.getProperty("version") }
        ?: "unknown"
