package org.reduxkotlin.sample.taskflow.platform

import app.cash.sqldelight.db.SqlDriver

/**
 * Per-platform factory for the TaskFlowDb [SqlDriver].
 *
 * One `actual` per source set: file-backed SQLite on android/ios/jvm, a sql.js Web Worker
 * driver on wasmJs. Creation is `suspend` because the wasmJs worker driver applies the schema
 * asynchronously (`Schema.create(driver).await()`); sync drivers apply it inline.
 */
expect class DriverFactory {
    /** Creates and schema-initializes the platform SQLite driver for `taskflow.db`. */
    suspend fun createDriver(): SqlDriver
}
