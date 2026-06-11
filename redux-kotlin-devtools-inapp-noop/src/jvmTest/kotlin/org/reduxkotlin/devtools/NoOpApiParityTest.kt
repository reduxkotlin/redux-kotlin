package org.reduxkotlin.devtools

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Automated debug/release API-parity gate.
 *
 * Reads the committed JVM API dumps (`api/jvm/<module>.api`) of this no-op module and of the debug
 * artifacts it mirrors (`redux-kotlin-devtools-core`, `-inapp`, and `-ui`, which hosts the
 * `DevToolsTab`/`DevToolsThemeMode` enums re-exported through `-inapp`) and asserts that every
 * public class/facade declared in the no-op dump has an identical declaration in one of the debug
 * dumps. If core/inapp/ui change a mirrored public API without the matching no-op change, this
 * test fails — regenerate with `./gradlew apiDump` and update the no-op mirror.
 *
 * Normalization rules:
 * - Compose's `$stable` static fields are dropped before comparing: this module compiles every
 *   class with the Compose compiler (which injects the field), while core compiles the same
 *   classes as plain Kotlin.
 * - Members are compared as unordered sets; the class header line must match exactly.
 * - The check is one-directional (no-op -> debug). Debug-only classes (hub, session, events,
 *   drawer internals, `ComposableSingletons$*`) are intentionally not mirrored and not checked.
 *
 * The repo root is located by walking up from `user.dir` (the module dir under Gradle); override
 * with `-Dredux.repo.root=<path>` if the test is ever run from elsewhere.
 */
class NoOpApiParityTest {

    private data class ApiClass(val header: String, val members: Set<String>)

    private val debugModules = listOf(
        "redux-kotlin-devtools-core",
        "redux-kotlin-devtools-inapp",
        "redux-kotlin-devtools-ui",
    )

    @Test
    fun every_mirrored_declaration_matches_the_debug_dumps() {
        val root = findRepoRoot()
        val noop = parseDump(dumpFile(root, "redux-kotlin-devtools-inapp-noop"))
        val debug = buildMap {
            for (module in debugModules) {
                for ((name, block) in parseDump(dumpFile(root, module))) put(name, block to module)
            }
        }

        val problems = mutableListOf<String>()
        for ((name, block) in noop) {
            val (counterpart, module) = debug[name]
                ?: run {
                    problems += "$name exists in the no-op dump but in none of the core/inapp/ui dumps"
                    null to null
                }
            if (counterpart == null) continue
            if (block.header != counterpart.header) {
                problems +=
                    "$name header differs from $module:\n  noop:  ${block.header}\n  debug: ${counterpart.header}"
            }
            val missing = counterpart.members - block.members
            val extra = block.members - counterpart.members
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                problems += buildString {
                    appendLine("$name members differ from $module:")
                    missing.forEach { appendLine("  missing in noop: $it") }
                    extra.forEach { appendLine("  extra in noop:   $it") }
                }.trimEnd()
            }
        }
        if (problems.isNotEmpty()) {
            fail(
                "no-op artifact has drifted from the debug API dumps " +
                    "(run ./gradlew apiDump and update the mirror):\n" +
                    problems.joinToString("\n"),
            )
        }
    }

    private fun dumpFile(root: File, module: String): File {
        val file = root.resolve("$module/api/jvm/$module.api")
        if (!file.isFile) fail("missing api dump: $file")
        return file
    }

    /** Parses a JVM api dump into class-name -> (header, member-lines), applying normalization. */
    private fun parseDump(file: File): Map<String, ApiClass> {
        val result = LinkedHashMap<String, ApiClass>()
        var header: String? = null
        val members = mutableListOf<String>()
        for (raw in file.readLines()) {
            val line = raw.trim()
            when {
                header == null && line.endsWith("{") -> {
                    header = line.removeSuffix("{").trim()
                    members.clear()
                }

                line == "}" && header != null -> {
                    val h = header
                    result[classNameOf(h)] = ApiClass(h, members.filterNot(::isStableField).toSet())
                    header = null
                }

                header != null && line.isNotEmpty() -> members.add(line)
            }
        }
        return result
    }

    /** Compose's injected stability field; present only on the compose-compiled side. */
    private fun isStableField(line: String): Boolean = line == "public static final field \$stable I"

    private fun classNameOf(header: String): String =
        requireNotNull(Regex("""class ([\w/$]+)""").find(header)?.groupValues?.get(1)) {
            "unparseable dump header: $header"
        }

    private fun findRepoRoot(): File {
        System.getProperty("redux.repo.root")?.let { return File(it) }
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (dir.resolve("redux-kotlin-devtools-core/api").isDirectory) return dir
            dir = dir.parentFile
        }
        fail("could not locate the repo root above ${System.getProperty("user.dir")}")
    }
}
