// Installs project-managed Git hooks.
//
// Replaces the abandoned `com.github.jakemarsden.git-hooks` plugin, which
// assumed `.git` is a directory and failed inside `git worktree` checkouts
// (there `.git` is a gitlink file pointing at `.git/worktrees/<name>`). We
// resolve the real hooks directory with `git rev-parse --git-path hooks`, which
// returns the correct per-worktree or common location regardless of layout.

val hooks = mapOf(
    "pre-commit" to "detektAll --auto-correct",
    "pre-push" to "detektAll"
)

fun hookBody(gradleArgs: String): String = """
    |#!/usr/bin/env sh
    |# Managed by convention.git-hooks. Do not edit by hand.
    |exec "${'$'}(git rev-parse --show-toplevel)/gradlew" $gradleArgs
    |""".trimMargin()

fun resolveHooksDir(): java.io.File? {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--git-path", "hooks")
            .directory(layout.projectDirectory.asFile)
            .redirectErrorStream(true)
            .start()
        val rel = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() != 0 || rel.isEmpty()) null
        else layout.projectDirectory.asFile.resolve(rel)
    } catch (_: Exception) {
        null
    }
}

fun writeHooks(dir: java.io.File) {
    dir.mkdirs()
    hooks.forEach { (name, gradleArgs) ->
        val file = dir.resolve(name)
        val body = hookBody(gradleArgs)
        if (!file.exists() || file.readText() != body) {
            file.writeText(body)
            file.setExecutable(true)
        }
    }
}

tasks.register("installGitHooks") {
    group = "build setup"
    description = "Writes Git hooks that delegate to Gradle tasks."
    doLast {
        val dir = resolveHooksDir()
            ?: error("Could not resolve Git hooks directory — is this a Git checkout?")
        writeHooks(dir)
    }
}

// Auto-install at configuration time so contributors don't need an extra
// command. Skip with GIT_HOOKS_SKIP_INSTALL=1 (CI / sandboxed builds).
if (System.getenv("GIT_HOOKS_SKIP_INSTALL") != "1") {
    resolveHooksDir()?.let(::writeHooks)
}
