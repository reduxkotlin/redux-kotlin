# Unified `rk` CLI — Phase 1 (unification) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the two CLI tools (`rk-devtools`, `rk-snapshot`) into a single `rk` binary with grouped subcommands (`rk devtools …`, `rk snapshot …`), retiring the two old binaries.

**Architecture:** Convert `redux-kotlin-devtools-cli` and `redux-kotlin-snapshot` from `application` modules into JVM libraries that each expose a public `CliktCommand` builder. A new `:redux-kotlin-cli` `application` module (binary `rk`) wires both builders under one root clikt command and is the only entry point.

**Tech Stack:** Kotlin/JVM, Gradle (`application` plugin, `convention.common`), clikt 4.4.0, Compose Desktop (transitive, for snapshot rendering + devtools `--ui`).

## Global Constraints

- Package for the new module: `org.reduxkotlin.cli` (project convention `org.reduxkotlin.<feature>`).
- Binary name: `rk`. Command groups: `devtools`, `snapshot`.
- JDK 17 toolchain (`jvmToolchain(17)`); bytecode JVM 17. Pinned per existing tool modules.
- These three modules are **repo tools** — NOT published to Maven, NOT in `redux-kotlin-bom`, NOT using a publishing convention. They use `convention.common` (+ `application` for the aggregator).
- `convention.common` does **not** enable `explicitApi()`, but detekt scans these modules — give every new `public` declaration a KDoc comment (matches existing style; avoids `UndocumentedPublic*`).
- clikt imports used: `com.github.ajalt.clikt.core.CliktCommand`, `com.github.ajalt.clikt.core.subcommands`, `com.github.ajalt.clikt.parameters.options.versionOption`, test helper `com.github.ajalt.clikt.testing.test`.
- Never bypass git hooks with `--no-verify`; the pre-commit runs `detektAll --auto-correct` (may re-stage formatting — re-add and re-commit if so).
- Skiko/Compose leave non-daemon threads alive → any `main` that runs a command must `exitProcess(0)` after it returns.

---

### Task 1: Convert `redux-kotlin-devtools-cli` to a library exposing `devToolsCommand()`

**Files:**
- Modify: `redux-kotlin-devtools-cli/build.gradle.kts` (drop `application`)
- Modify: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/command/RootCommand.kt`
- Delete: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/Main.kt`
- Test: `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/command/DevToolsCommandTest.kt`

**Interfaces:**
- Produces: `public fun devToolsCommand(): com.github.ajalt.clikt.core.CliktCommand` (a group named `devtools` with subcommands serve/stores/actions/diff/state/tail).

- [ ] **Step 1: Write the failing test**

Create `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/command/DevToolsCommandTest.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevToolsCommandTest {
    @Test fun group_is_named_devtools() {
        assertEquals("devtools", devToolsCommand().commandName)
    }

    @Test fun help_lists_subcommands() {
        val out = devToolsCommand().test("--help").output
        listOf("serve", "stores", "actions", "diff", "state", "tail").forEach {
            assertTrue(it in out, "devtools --help missing subcommand: $it")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests '*DevToolsCommandTest*'`
Expected: FAIL — `devToolsCommand` unresolved.

- [ ] **Step 3: Rename the group and expose the public builder**

Replace the body of `RootCommand.kt` with:

```kotlin
package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

/** The `devtools` command group; dispatches to its subcommands. */
internal class DevToolsRootCommand : CliktCommand(name = "devtools") {
    override fun run() = Unit
}

/** Builds the `devtools` command group used by the unified `rk` CLI. */
public fun devToolsCommand(): CliktCommand = DevToolsRootCommand().subcommands(
    ServeCommand(),
    StoresCommand(),
    ActionsCommand(),
    DiffCommand(),
    StateCommand(),
    TailCommand(),
)
```

- [ ] **Step 4: Delete the old entry point**

Run: `git rm redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/Main.kt`

- [ ] **Step 5: Drop the `application` plugin from the module build**

In `redux-kotlin-devtools-cli/build.gradle.kts`: remove the `application` line from the `plugins {}` block, the entire `application { … }` block, and the entire `tasks.startScripts { … }` block (the Java-17 launcher guard moves to the new `:redux-kotlin-cli` module in Task 3). Leave everything else (the `kotlin("jvm")`, compose plugins, `jvmToolchain(17)`, and all `dependencies`) unchanged.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests '*DevToolsCommandTest*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add redux-kotlin-devtools-cli/
git commit -m "refactor(devtools-cli): library exposing devToolsCommand() group"
```

---

### Task 2: Convert `redux-kotlin-snapshot` to a library exposing `snapshotCommand()`

**Files:**
- Modify: `redux-kotlin-snapshot/build.gradle.kts` (drop `application`)
- Modify: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Cli.kt`
- Delete: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Main.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/CliNameTest.kt`

**Interfaces:**
- Consumes: `public val org.reduxkotlin.snapshot.demoSnapshots: SnapshotApp` (already public).
- Produces: `public fun snapshotCommand(app: SnapshotApp): CliktCommand` (a command named `snapshot`).

- [ ] **Step 1: Write the failing test**

Create `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/CliNameTest.kt`:

```kotlin
package org.reduxkotlin.snapshot

import com.github.ajalt.clikt.testing.test
import org.reduxkotlin.snapshot.cli.snapshotCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliNameTest {
    @Test fun command_is_named_snapshot() {
        assertEquals("snapshot", snapshotCommand(demoSnapshots).commandName)
    }

    @Test fun list_still_works() {
        val r = snapshotCommand(demoSnapshots).test("--list")
        assertTrue("scenes" in r.output)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests '*CliNameTest*'`
Expected: FAIL — `commandName` is still `rk-snapshot` (assertEquals fails).

- [ ] **Step 3: Rename the command and make the builder public**

In `Cli.kt`:
- Change the builder signature/visibility and KDoc:

```kotlin
/**
 * Builds the `snapshot` command for [app]. Exit codes: 0 ok, 1 render/verify failure, 2 usage.
 * Public so the unified `rk` CLI can mount it as a subcommand; in-project callers should prefer
 * [runCli], which keeps the Clikt dependency off this module's public API.
 */
public fun snapshotCommand(app: SnapshotApp): CliktCommand = SnapshotCommand(app)
```

- Change the private command's name from `"rk-snapshot"` to `"snapshot"`:

```kotlin
private class SnapshotCommand(private val app: SnapshotApp) : CliktCommand(name = "snapshot") {
```

Leave `SnapshotApp.runCli` and the rest of the file unchanged.

- [ ] **Step 4: Delete the old entry point**

Run: `git rm redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Main.kt`

(In-project consumers keep using `demoSnapshots.runCli(args)` / `snapshotCommand(registry)`; the standalone `main` rendering the demo registry is retired in favor of `rk snapshot`.)

- [ ] **Step 5: Drop the `application` plugin from the module build**

In `redux-kotlin-snapshot/build.gradle.kts`: remove the `application` line from `plugins {}` and the entire `application { … }` block. Leave the compose plugins, `jvmToolchain(17)`, and all `dependencies` unchanged.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :redux-kotlin-snapshot:test --tests '*CliNameTest*' --tests '*CliTest*'`
Expected: PASS (existing `CliTest` does not assert the command name, so it still passes).

- [ ] **Step 7: Commit**

```bash
git add redux-kotlin-snapshot/
git commit -m "refactor(snapshot): library exposing public snapshotCommand() named 'snapshot'"
```

---

### Task 3: Create the `:redux-kotlin-cli` aggregator (binary `rk`)

**Files:**
- Create: `redux-kotlin-cli/build.gradle.kts`
- Create: `redux-kotlin-cli/src/main/kotlin/org/reduxkotlin/cli/RkCommand.kt`
- Create: `redux-kotlin-cli/src/main/kotlin/org/reduxkotlin/cli/Version.kt`
- Create: `redux-kotlin-cli/src/main/kotlin/org/reduxkotlin/cli/Main.kt`
- Create: `redux-kotlin-cli/src/main/resources/rk-version.properties`
- Create: `redux-kotlin-cli/README.md`
- Modify: `settings.gradle.kts` (add the module include)
- Test: `redux-kotlin-cli/src/test/kotlin/org/reduxkotlin/cli/RkCommandTest.kt`

**Interfaces:**
- Consumes: `devToolsCommand()` (Task 1), `snapshotCommand(SnapshotApp)` + `demoSnapshots` (Task 2).
- Produces: binary `rk`; root builder `internal fun rkCommand(): CliktCommand`; `public fun main(args: Array<String>)`.

- [ ] **Step 1: Register the module in settings**

In `settings.gradle.kts`, add `":redux-kotlin-cli",` to the `include(...)` list next to the other tool modules (near `":redux-kotlin-devtools-cli"`, `":redux-kotlin-snapshot"`).

- [ ] **Step 2: Create the module build file**

Create `redux-kotlin-cli/build.gradle.kts`:

```kotlin
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("convention.common")
    kotlin("jvm")
    application
}

// Pin the compile JDK so installDist is deterministic regardless of the developer's default Java.
kotlin {
    jvmToolchain(17)
}

application {
    applicationName = "rk"
    mainClass.set("org.reduxkotlin.cli.MainKt")
}

// Stamp the project version into a resource that Version.kt reads at runtime for `rk --version`.
tasks.named<ProcessResources>("processResources") {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("rk-version.properties") {
        expand(mapOf("version" to v))
    }
}

// The installDist launcher resolves its runtime Java from JAVA_HOME/PATH, which may be older than
// the required 17. Guard the generated unix launcher so it fails with a clear message instead of a
// cryptic UnsupportedClassVersionError (thrown at class-load, before main() can react).
tasks.startScripts {
    doLast {
        val q = "\""
        val d = "\$"
        val guard = listOf(
            "",
            "# rk requires Java 17+ (see redux-kotlin-cli/README.md).",
            "RK_JV=$d($q${d}JAVACMD$q -version 2>&1 \\",
            "  | awk -F'[$q.]' '/version/{print (${d}2==${q}1$q?${d}3:${d}2);exit}')",
            "if [ -n $q${d}RK_JV$q ] && [ $q${d}RK_JV$q -lt 17 ] 2>/dev/null; then",
            "    echo ${q}rk requires Java 17+, but '${d}JAVACMD' is Java ${d}RK_JV.$q >&2",
            "    echo ${q}Point JAVA_HOME at a JDK 17 or newer and re-run.$q >&2",
            "    exit 1",
            "fi",
            "",
            "",
        ).joinToString("\n")
        val execLine = "exec $q${d}JAVACMD$q $q$d@$q"
        unixScript.writeText(unixScript.readText().replace(execLine, guard + execLine))
    }
}

dependencies {
    implementation(project(":redux-kotlin-devtools-cli"))
    implementation(project(":redux-kotlin-snapshot"))
    implementation(libs.clikt)
    testImplementation(kotlin("test"))
}
```

- [ ] **Step 3: Create the version resource + reader**

Create `redux-kotlin-cli/src/main/resources/rk-version.properties`:

```
version=${version}
```

Create `redux-kotlin-cli/src/main/kotlin/org/reduxkotlin/cli/Version.kt`:

```kotlin
package org.reduxkotlin.cli

import java.util.Properties

/** The `rk` version, read from the `rk-version.properties` resource stamped at build time. */
internal val RK_VERSION: String =
    RkCommand::class.java.getResourceAsStream("/rk-version.properties")
        ?.use { stream -> Properties().apply { load(stream) }.getProperty("version") }
        ?: "unknown"
```

- [ ] **Step 4: Write the failing test**

Create `redux-kotlin-cli/src/test/kotlin/org/reduxkotlin/cli/RkCommandTest.kt`:

```kotlin
package org.reduxkotlin.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RkCommandTest {
    @Test fun root_is_named_rk() {
        assertEquals("rk", rkCommand().commandName)
    }

    @Test fun help_lists_both_groups() {
        val out = rkCommand().test("--help").output
        assertTrue("devtools" in out, "rk --help missing 'devtools'")
        assertTrue("snapshot" in out, "rk --help missing 'snapshot'")
    }

    @Test fun version_option_prints_a_version() {
        val out = rkCommand().test("--version").output
        assertTrue("rk version" in out, "rk --version output: $out")
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-cli:test --tests '*RkCommandTest*'`
Expected: FAIL — `rkCommand` unresolved.

- [ ] **Step 6: Create the root command + entry point**

Create `redux-kotlin-cli/src/main/kotlin/org/reduxkotlin/cli/RkCommand.kt`:

```kotlin
package org.reduxkotlin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import org.reduxkotlin.devtools.cli.command.devToolsCommand
import org.reduxkotlin.snapshot.cli.snapshotCommand
import org.reduxkotlin.snapshot.demoSnapshots

/** Root `rk` command; groups the devtools and snapshot toolsets. */
internal class RkCommand : CliktCommand(name = "rk") {
    override fun run() = Unit
}

/** Builds the full `rk` command tree: `rk devtools …` and `rk snapshot …`. */
internal fun rkCommand(): CliktCommand =
    RkCommand()
        .versionOption(RK_VERSION, names = setOf("--version"), message = { "rk version $it" })
        .subcommands(
            devToolsCommand(),
            snapshotCommand(demoSnapshots),
        )
```

Create `redux-kotlin-cli/src/main/kotlin/org/reduxkotlin/cli/Main.kt`:

```kotlin
package org.reduxkotlin.cli

import kotlin.system.exitProcess

/** Entry point for the unified `rk` CLI. */
public fun main(args: Array<String>) {
    rkCommand().main(args)
    // Skiko/Compose desktop keep non-daemon threads alive; force a clean exit after the command.
    exitProcess(0)
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :redux-kotlin-cli:test --tests '*RkCommandTest*'`
Expected: PASS.

- [ ] **Step 8: Create the module README**

Create `redux-kotlin-cli/README.md`:

```markdown
# redux-kotlin-cli

`rk` — the unified redux-kotlin command-line tool. One binary, two groups:

- `rk devtools …` — inspect a running redux-kotlin app (action log, JSON diffs,
  per-store `.jsonl` captures) over the devtools bridge.
- `rk snapshot …` — render built-in / manifest Compose scenes to PNG with golden
  diffing. (Rendering *your own* app's screens stays a library use — depend on
  `redux-kotlin-snapshot` and call `yourRegistry.runCli(args)`.)

**Unpublished today** — build from this repo (needs **JDK 17+**):

```
./gradlew :redux-kotlin-cli:installDist
# binary: redux-kotlin-cli/build/install/rk/bin/rk
rk --help
rk --version
```

Add that `bin/` directory to your `PATH`, or symlink the binary. Phase 2 will
publish self-contained per-OS builds via Homebrew/Scoop (`brew install
reduxkotlin/tap/rk`) with a bundled JRE — no Java required.
```

- [ ] **Step 9: Commit**

```bash
git add redux-kotlin-cli/ settings.gradle.kts
git commit -m "feat(cli): unified rk binary wiring devtools + snapshot groups"
```

---

### Task 4: Update CI smoke test + docs to the unified `rk`

**Files:**
- Modify: `.github/workflows/ci.yml:78-92` (the "Smoke-test the assembled rk-devtools CLI" step)
- Modify: `redux-kotlin-devtools-cli/README.md`, `redux-kotlin-snapshot/README.md`
- Modify: `docs/agent/api-map.md`, `docs/agent/references/devtools.md`, `docs/agent/AGENTS-external.md`
- Modify: `README.md`, `website/docs/advanced/DevTools.md`

**Interfaces:** none (docs/CI only).

- [ ] **Step 1: Update the CI smoke test**

In `.github/workflows/ci.yml`, replace the smoke-test step body so it builds and runs the unified binary. Set the install task to `:redux-kotlin-cli:installDist`, the binary path to `redux-kotlin-cli/build/install/rk/bin/rk`, assert `rk --help` contains both `devtools` and `snapshot`, and assert `rk devtools --help` contains `serve` and `actions`. Rename the step to "Smoke-test the assembled rk CLI". Example body:

```yaml
      - name: Smoke-test the assembled rk CLI
        run: |
          ./gradlew --no-daemon --stacktrace :redux-kotlin-cli:installDist
          bin=redux-kotlin-cli/build/install/rk/bin/rk
          out=$("$bin" --help)
          for grp in devtools snapshot; do
            echo "$out" | grep -q "$grp" || { echo "rk --help is missing group: $grp"; exit 1; }
          done
          dout=$("$bin" devtools --help)
          for cmd in serve actions; do
            echo "$dout" | grep -q "$cmd" || { echo "rk devtools --help missing: $cmd"; exit 1; }
          done
```

- [ ] **Step 2: Find every stale CLI reference**

Run:

```bash
grep -rnE 'rk-devtools|rk-snapshot|redux-kotlin-devtools-cli:installDist|redux-kotlin-snapshot:installDist|install/rk-devtools|install/rk-snapshot' \
  README.md website/docs redux-kotlin-devtools-cli/README.md redux-kotlin-snapshot/README.md \
  docs/agent | grep -v node_modules
```

Expected: a list of doc lines using the old binary names / install tasks.

- [ ] **Step 3: Rewrite each reference to the unified `rk`**

For every hit from Step 2, apply these substitutions (preserving surrounding prose):
- `rk-devtools <cmd>` → `rk devtools <cmd>` (e.g. `rk-devtools serve` → `rk devtools serve`).
- `rk-snapshot <args>` → `rk snapshot <args>`.
- `./gradlew :redux-kotlin-devtools-cli:installDist` and `./gradlew :redux-kotlin-snapshot:installDist` → `./gradlew :redux-kotlin-cli:installDist`.
- Binary path `…/build/install/rk-devtools/bin/rk-devtools` (and the `rk-snapshot` equivalent) → `redux-kotlin-cli/build/install/rk/bin/rk`.
- In `website/docs/advanced/DevTools.md`, the artifacts-overview table: change the `redux-kotlin-devtools-cli` row's "Role" to describe it as the **library** behind `rk devtools` (`devToolsCommand()`), and add/keep a note that the installable tool is `rk` from `:redux-kotlin-cli`. Update the "## The `rk-devtools` CLI" heading to "## The `rk` CLI" and its install block to `:redux-kotlin-cli:installDist`.
- In `README.md`, the "Dev tools (in-repo, unpublished)" table row: replace the `rk-snapshot` / `redux-kotlin-devtools-cli` wording with a single `redux-kotlin-cli` (`rk`) entry that bundles `rk devtools` + `rk snapshot`.
- In `docs/agent/AGENTS-external.md`: if the "DevTools CLI — `rk-devtools`" section exists (it lands via PR #387), retitle it to "DevTools CLI — `rk`", change the build command to `:redux-kotlin-cli:installDist` and the binary path to `redux-kotlin-cli/build/install/rk/bin/rk`, and change command examples to `rk devtools …`. If the section is absent on this branch, add the equivalent section pointing at `rk`.

- [ ] **Step 4: Re-assemble agent knowledge (AGENTS-external feeds the website page)**

Run: `bash scripts/assemble-agent-knowledge.sh && bash scripts/assemble-agent-knowledge.sh --check`
Expected: `ASSEMBLED` then `ASSEMBLE CHECK OK`.

- [ ] **Step 5: Verify no stale references remain**

Run the Step 2 grep again.
Expected: no remaining `rk-devtools <cmd>` / `rk-snapshot <args>` command usages and no old `installDist` task references. (The word `devtools`/`snapshot` as group/module names is fine.)

- [ ] **Step 6: Build the website to confirm docs still render**

Run: `cd website && YARN_IGNORE_ENGINES=true yarn build`
Expected: `[SUCCESS] Generated static files in "build"`. Pre-existing broken-anchor warnings on `/faq`, `/glossary`, `/api` are unrelated.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/ci.yml README.md website/docs docs/agent \
        redux-kotlin-devtools-cli/README.md redux-kotlin-snapshot/README.md AGENTS.md
git commit -m "docs(cli): point CI smoke test + docs at the unified rk binary"
```

---

### Task 5: Full-build verification + `rk` install smoke test

**Files:** none (verification only).

- [ ] **Step 1: Build the three affected modules with tests + lint**

Run:
```bash
./gradlew :redux-kotlin-devtools-cli:build :redux-kotlin-snapshot:build :redux-kotlin-cli:build detektAll
```
Expected: `BUILD SUCCESSFUL`. (If detekt auto-corrects formatting, re-stage and amend the relevant commit.)

- [ ] **Step 2: Install and smoke-test the unified binary**

Run:
```bash
./gradlew :redux-kotlin-cli:installDist
bin=redux-kotlin-cli/build/install/rk/bin/rk
"$bin" --help
"$bin" --version
"$bin" devtools --help
"$bin" snapshot --list
```
Expected: `--help` lists `devtools` and `snapshot`; `--version` prints `rk version <project version>`; `devtools --help` lists serve/stores/actions/diff/state/tail; `snapshot --list` prints a JSON `scenes` array.

- [ ] **Step 3: Confirm the old binaries are gone**

Run:
```bash
ls redux-kotlin-devtools-cli/build/install 2>/dev/null || echo "no devtools-cli dist (expected)"
ls redux-kotlin-snapshot/build/install 2>/dev/null || echo "no snapshot dist (expected)"
test ! -e redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/Main.kt && echo "devtools Main.kt removed (expected)"
test ! -e redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Main.kt && echo "snapshot Main.kt removed (expected)"
```
Expected: no `installDist` output dirs for the two old modules; both `Main.kt` files removed.

- [ ] **Step 4: Commit (if any verification fixups were needed; otherwise skip)**

```bash
git add -A
git commit -m "test(cli): verify unified rk build + install"
```

---

## Out of scope (Phase 2 — separate plan)

Per-OS packaging with a bundled JRE (Compose `nativeDistributions` jpackage), the
per-OS CI build matrix, and JReleaser distribution to a Homebrew tap + Scoop
bucket. Blocked on maintainer creating `reduxkotlin/homebrew-tap` +
`reduxkotlin/scoop-bucket` and a release PAT secret. Written after Phase 1 merges.
