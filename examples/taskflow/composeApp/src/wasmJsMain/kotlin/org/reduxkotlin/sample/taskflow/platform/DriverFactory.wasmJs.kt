package org.reduxkotlin.sample.taskflow.platform

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.w3c.dom.Worker

/** wasmJs driver factory backed by a [WebWorkerDriver] over the sql.js Web Worker. */
actual class DriverFactory {
    /**
     * Creates the sql.js Web Worker driver and applies the schema asynchronously
     * (`Schema.create(driver).await()`, required by `generateAsync = true`).
     */
    actual suspend fun createDriver(): SqlDriver {
        val driver = WebWorkerDriver(createSqlJsWorker())
        TaskFlowDb.Schema.create(driver).await()
        return driver
    }
}

// `js(code)` must be a single top-level expression, so the worker is built in its own function.
private fun createSqlJsWorker(): Worker =
    js("""new Worker(new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url))""")
