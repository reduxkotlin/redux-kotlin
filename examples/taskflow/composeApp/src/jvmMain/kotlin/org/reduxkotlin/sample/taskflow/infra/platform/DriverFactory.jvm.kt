package org.reduxkotlin.sample.taskflow.infra.platform

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import java.io.File

/** JVM/desktop driver factory backed by [JdbcSqliteDriver] over a per-OS file-backed `taskflow.db`. */
actual class DriverFactory {
    /**
     * Creates a file-backed JDBC SQLite driver, creating the schema once (guarded by the SQLite
     * `user_version` pragma so re-launches don't re-run `CREATE TABLE`).
     */
    actual suspend fun createDriver(): SqlDriver {
        val dbFile = File(appDataDir(), "taskflow.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        val schema = TaskFlowDb.Schema.synchronous()
        if (currentSchemaVersion(driver) < schema.version) {
            schema.create(driver)
            setSchemaVersion(driver, schema.version)
        }
        return driver
    }

    private fun currentSchemaVersion(driver: SqlDriver): Long = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        parameters = 0,
    ).value

    private fun setSchemaVersion(driver: SqlDriver, version: Long) {
        driver.execute(identifier = null, sql = "PRAGMA user_version = $version", parameters = 0)
    }

    private fun appDataDir(): File {
        val home = System.getProperty("user.home") ?: "."
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        val base = when {
            os.contains("mac") -> {
                File(home, "Library/Application Support/TaskFlow")
            }

            os.contains("win") -> {
                File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path, "TaskFlow")
            }

            else -> {
                File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "taskflow")
            }
        }
        if (!base.exists()) base.mkdirs()
        return base
    }
}
