package org.reduxkotlin.sample.taskflow.platform

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb

/** iOS driver factory backed by [NativeSqliteDriver] over a file-backed `taskflow.db`. */
actual class DriverFactory {
    /** Creates a file-backed native SQLite driver, applying the (synchronous) schema. */
    actual suspend fun createDriver(): SqlDriver = NativeSqliteDriver(TaskFlowDb.Schema.synchronous(), "taskflow.db")
}
