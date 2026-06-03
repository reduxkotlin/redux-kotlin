package org.reduxkotlin.sample.taskflow.infra.platform

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb

/** Android driver factory backed by [AndroidSqliteDriver] over a file-backed `taskflow.db`. */
actual class DriverFactory {
    /** Creates a file-backed Android SQLite driver, applying the (synchronous) schema. */
    actual suspend fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = TaskFlowDb.Schema.synchronous(),
        context = AndroidContextHolder.appContext,
        name = "taskflow.db",
    )
}
