# Redux-Kotlin DevTools CLI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a JVM CLI (`rk-devtools`) that hosts the DevTools bridge receiver, writes a per-store `.jsonl` capture, optionally launches the bundled GUI, and exposes Compose-free query subcommands (`actions`/`diff`/`state`/`tail`/`stores`) over the captured action+state+diff log.

**Architecture:** New `redux-kotlin-devtools-cli` JVM module, three internal layers: a pure Compose-free `capture/` query library (reads the `.jsonl` recording format), a `server/` writer that snapshots the reused standalone `MonitorIngest` to disk, and a `command/` clikt frontend. `serve` reuses `redux-kotlin-devtools-standalone`'s `MonitorServer`/`MonitorIngest`/`MonitorApp` (Compose, runs once); query subcommands touch only `capture/`. The pure `.jsonl` codec is first moved from `-standalone` down into `-devtools-bridge` so `capture/` can share it Compose-free.

**Tech Stack:** Kotlin/JVM, Clikt (arg parsing), Ktor CIO server + websockets (via the reused standalone server), kotlinx.serialization, kotlinx.coroutines, kotlin-test.

**Reference spec:** `docs/superpowers/specs/2026-06-04-redux-kotlin-devtools-cli-design.md`

---

## Conventions for this module

- The CLI is **not a published library**, so it does not apply `explicitApi()`. But `detektAll` still scans it and `UndocumentedPublicClass/Function/Property` are active (excludes only `*Test`, `examples`, `build-conventions`). **Therefore: declare every class/function `internal` except `fun main`.** Internal symbols are not flagged; only `main` needs a KDoc line.
- Tests live in `src/test/kotlin` (this is a `kotlin("jvm")` module, not multiplatform). Framework: `kotlin.test`.
- Run the module's tests with `./gradlew :redux-kotlin-devtools-cli:test`. Lint with `./gradlew detektAll`. Never `--no-verify`.
- Store key on the wire is `"${clientId}::${storeInstanceId}"` (formed in `MonitorIngest.Connection.accept`). Capture filenames sanitize it.

---

## File Structure

**Refactor (existing modules):**
- Move from `redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/Recording.kt` → new `redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/Recording.kt`: `RecordingHeader`, `encodeRecording`, `decodeRecording`. The `expect/actual saveRecording`/`loadRecording` stay in `-standalone`.

**New module `redux-kotlin-devtools-cli`:**
- `build.gradle.kts` — module build.
- `src/main/kotlin/org/reduxkotlin/devtools/cli/Main.kt` — `fun main(args)`.
- `capture/CaptureReader.kt` — read one capture file → `(RecordingHeader, List<BridgeMessage>)`; extract `Action`s.
- `capture/CaptureStore.kt` — `safeKey()`, discover store files in a directory, read each header → `StoreRef`.
- `capture/CaptureQuery.kt` — `QuerySpec` + `apply()` filter over `List<BridgeMessage.Action>`.
- `capture/CaptureFormatter.kt` — `Format` enum + render records to JSON-lines / pretty.
- `server/CaptureWriter.kt` — atomically write `(header, messages)` to a per-store `.jsonl`.
- `server/CaptureFlusher.kt` — collect `ingest.registry.state`, flush each store via `CaptureWriter`.
- `command/QueryOptions.kt` — shared clikt option group.
- `command/{Root,Serve,Stores,Actions,Diff,State,Tail}Command.kt` — clikt commands.
- `src/test/kotlin/.../capture/*Test.kt`, `.../server/*Test.kt` — tests.
- `settings.gradle.kts` — add `:redux-kotlin-devtools-cli`.
- `gradle/libs.versions.toml` — add `clikt`.

---

## Task 0: Move the recording codec from `-standalone` to `-devtools-bridge`

**Files:**
- Read: `redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/Recording.kt`
- Create: `redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/Recording.kt`
- Modify: `redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/Recording.kt` (keep only the expect functions), and any importer of `RecordingHeader`/`encodeRecording`/`decodeRecording` in `-standalone`.

- [ ] **Step 1: Create the codec in bridge**

Create `redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/Recording.kt`:

```kotlin
package org.reduxkotlin.devtools.bridge

import kotlinx.serialization.Serializable

/** Header line of a `.jsonl` DevTools recording — identifies the single store the recording captures. */
@Serializable
public data class RecordingHeader(
    /** Discriminator constant identifying the file kind. */
    public val kind: String = "rk-devtools-recording",
    /** Bridge protocol version the recording was produced with. */
    public val protocolVersion: Int,
    /** Serializer tier that produced the JSON values. */
    public val serializerTier: String,
    /** Stable client (app instance) id. */
    public val clientId: String,
    /** Human label for the client. */
    public val clientLabel: String,
    /** Store display name. */
    public val storeName: String,
    /** Store instance id (unique within a client). */
    public val storeInstanceId: String,
)

/** Encode a recording: header line followed by one [BridgeMessage] JSON per line. */
public fun encodeRecording(header: RecordingHeader, messages: List<BridgeMessage>): String = buildString {
    appendLine(bridgeJson.encodeToString(RecordingHeader.serializer(), header))
    messages.forEach { appendLine(bridgeJson.encodeToString(BridgeMessage.serializer(), it)) }
}

/** Decode a recording produced by [encodeRecording] back into its header and messages. */
public fun decodeRecording(text: String): Pair<RecordingHeader, List<BridgeMessage>> {
    val lines = text.split("\n").filter { it.isNotBlank() }
    require(lines.isNotEmpty()) { "empty recording" }
    val header = bridgeJson.decodeFromString(RecordingHeader.serializer(), lines.first())
    val messages = lines.drop(1).map { bridgeJson.decodeFromString(BridgeMessage.serializer(), it) }
    return header to messages
}
```

- [ ] **Step 2: Reduce the standalone Recording.kt to the platform pickers**

Replace the body of `redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/Recording.kt` so it keeps only the expect functions and re-imports the codec from bridge:

```kotlin
package org.reduxkotlin.devtools.monitor

// RecordingHeader / encodeRecording / decodeRecording now live in :redux-kotlin-devtools-bridge
// (org.reduxkotlin.devtools.bridge). Platform file pickers remain here.

/** Persist [text] (an encoded recording) via the platform's save mechanism. */
public expect fun saveRecording(suggestedName: String, text: String)

/** Load a recording's text via the platform's open mechanism and pass it to [onLoaded]. */
public expect fun loadRecording(onLoaded: (String) -> Unit)
```

- [ ] **Step 3: Fix imports in standalone**

Run: `./gradlew :redux-kotlin-devtools-standalone:compileKotlinJvm`
Expected: FAILs with unresolved `RecordingHeader`/`encodeRecording`/`decodeRecording` in files like `MonitorIngest.kt` and `Recording.jvm.kt`.
For each failing file, add `import org.reduxkotlin.devtools.bridge.RecordingHeader` (and `encodeRecording`/`decodeRecording` where used). `-standalone` already depends on `:redux-kotlin-devtools-bridge`.

- [ ] **Step 4: Recompile both modules**

Run: `./gradlew :redux-kotlin-devtools-bridge:compileKotlinJvm :redux-kotlin-devtools-standalone:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Regenerate API dumps (public surface moved between modules)**

Run: `./gradlew :redux-kotlin-devtools-bridge:apiDump :redux-kotlin-devtools-standalone:apiDump`
Expected: `redux-kotlin-devtools-bridge/api/*.api` gains `RecordingHeader`/`encodeRecording`/`decodeRecording`; `redux-kotlin-devtools-standalone/api/*.api` loses them.

- [ ] **Step 6: Run standalone tests + lint**

Run: `./gradlew :redux-kotlin-devtools-standalone:jvmTest :redux-kotlin-devtools-bridge:jvmTest detektAll`
Expected: BUILD SUCCESSFUL (the move is behavior-preserving).

- [ ] **Step 7: Commit**

```bash
git add redux-kotlin-devtools-bridge redux-kotlin-devtools-standalone
git commit -m "refactor(devtools): move .jsonl recording codec from standalone to bridge"
```

---

## Task 1: Scaffold the `redux-kotlin-devtools-cli` module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `redux-kotlin-devtools-cli/build.gradle.kts`
- Create: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/Main.kt`

- [ ] **Step 1: Add Clikt to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add `clikt = "4.4.0"`, and under `[libraries]` add:

```toml
clikt = { module = "com.github.ajalt.clikt:clikt", version.ref = "clikt" }
```

- [ ] **Step 2: Include the module**

In `settings.gradle.kts`, add `":redux-kotlin-devtools-cli",` to the `include(...)` list immediately after `":redux-kotlin-devtools-standalone",`.

- [ ] **Step 3: Create the build file**

Create `redux-kotlin-devtools-cli/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("convention.common")
    kotlin("jvm")
    application
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "rk-devtools"
    mainClass.set("org.reduxkotlin.devtools.cli.MainKt")
}

dependencies {
    implementation(project(":redux-kotlin-devtools-core"))
    implementation(project(":redux-kotlin-devtools-bridge"))
    implementation(project(":redux-kotlin-devtools-standalone"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 4: Create a minimal Main**

Create `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/Main.kt`:

```kotlin
package org.reduxkotlin.devtools.cli

/** CLI entry point for the redux-kotlin DevTools tool. */
public fun main(args: Array<String>) {
    println("rk-devtools (args: ${args.joinToString(" ")})")
}
```

- [ ] **Step 5: Verify the module assembles and is wired**

Run: `./gradlew :redux-kotlin-devtools-cli:compileKotlin`
Expected: BUILD SUCCESSFUL.
Run: `./gradlew :redux-kotlin-devtools-cli:run --args="hello"`
Expected: prints `rk-devtools (args: hello)`.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml redux-kotlin-devtools-cli
git commit -m "build(devtools-cli): scaffold JVM CLI module"
```

---

## Task 2: `capture/CaptureStore` — store key sanitization + discovery

**Files:**
- Create: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureStore.kt`
- Test: `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureStoreTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CaptureStoreTest.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureStoreTest {
    @Test
    fun safeKey_sanitizes_separator_and_unsafe_chars() {
        assertEquals("taskflow__TaskFlow-root", safeKey("taskflow::TaskFlow-root"))
        assertEquals("a_b__c_d", safeKey("a/b::c d"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureStoreTest"`
Expected: FAIL — `safeKey` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `CaptureStore.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.capture

import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.decodeRecording
import java.io.File

/** A store discovered in a capture directory. */
internal data class StoreRef(val key: String, val name: String, val file: File)

/** Convert a wire store key (`clientId::storeInstanceId`) into a filesystem-safe base name. */
internal fun safeKey(storeKey: String): String =
    storeKey.replace("::", "__").replace(Regex("[^A-Za-z0-9_.-]"), "_")

/** Capture file name for a store key. */
internal fun captureFileName(storeKey: String): String = "${safeKey(storeKey)}.jsonl"

/** List the store recordings present in [dir] by reading each file's header. */
internal fun discoverStores(dir: File): List<StoreRef> {
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }
        ?.mapNotNull { f ->
            runCatching {
                val header: RecordingHeader = decodeRecording(f.readText()).first
                StoreRef(key = "${header.clientId}::${header.storeInstanceId}", name = header.storeName, file = f)
            }.getOrNull()
        }
        ?.sortedBy { it.key }
        .orEmpty()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureStoreTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "feat(devtools-cli): capture store key sanitization + discovery"
```

---

## Task 3: `capture/CaptureReader` — read a file → Action records

**Files:**
- Create: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureReader.kt`
- Test: `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureReaderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CaptureReaderTest.kt`. It builds a recording with the real codec, including a trailing partial line, and asserts the reader returns the actions and tolerates the partial line:

```kotlin
package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.encodeRecording
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureReaderTest {
    private fun header() = RecordingHeader(
        protocolVersion = PROTOCOL_VERSION, serializerTier = "json",
        clientId = "taskflow", clientLabel = "TaskFlow", storeName = "TaskFlow", storeInstanceId = "root",
    )

    private fun action(id: Int, type: String) = BridgeMessage.Action(
        actionId = id,
        action = buildJsonObject { put("type", JsonPrimitive(type)) },
        state = buildJsonObject { put("n", JsonPrimitive(id)) },
        diff = emptyList(),
        timestampMillis = id.toLong(),
        isExcess = false,
    )

    @Test
    fun reads_actions_and_tolerates_trailing_partial_line() {
        val text = encodeRecording(header(), listOf(action(1, "A"), action(2, "B"))) + "{\"t\":\"acti"  // partial
        val (h, actions) = parseCapture(text)
        assertEquals("TaskFlow", h.storeName)
        assertEquals(listOf(1, 2), actions.map { it.actionId })
        assertEquals(listOf("A", "B"), actions.map { actionType(it.action) })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureReaderTest"`
Expected: FAIL — `parseCapture` / `actionType` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `CaptureReader.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.bridgeJson
import java.io.File

/** Parse capture [text]: first line is the header, subsequent lines are messages; a trailing partial line is skipped. */
internal fun parseCapture(text: String): Pair<RecordingHeader, List<BridgeMessage.Action>> {
    val lines = text.split("\n").filter { it.isNotBlank() }
    require(lines.isNotEmpty()) { "empty capture" }
    val header = bridgeJson.decodeFromString(RecordingHeader.serializer(), lines.first())
    val actions = lines.drop(1).mapNotNull { line ->
        runCatching { bridgeJson.decodeFromString(BridgeMessage.serializer(), line) }
            .getOrNull() as? BridgeMessage.Action
    }
    return header to actions
}

/** Read a capture [file] from disk. */
internal fun readCapture(file: File): Pair<RecordingHeader, List<BridgeMessage.Action>> = parseCapture(file.readText())

/** Render the `type` discriminant of a serialized action for display/filtering. */
internal fun actionType(action: JsonElement): String =
    ((action as? JsonObject)?.get("type") as? JsonPrimitive)?.content ?: "?"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureReaderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "feat(devtools-cli): capture reader (header + actions, partial-line tolerant)"
```

---

## Task 4: `capture/CaptureQuery` — filtering

**Files:**
- Create: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureQuery.kt`
- Test: `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureQueryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CaptureQueryTest.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.reduxkotlin.devtools.bridge.BridgeMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureQueryTest {
    private fun a(id: Int, type: String, ts: Long = id.toLong()) = BridgeMessage.Action(
        actionId = id, action = buildJsonObject { put("type", JsonPrimitive(type)) },
        state = buildJsonObject {}, diff = emptyList(), timestampMillis = ts, isExcess = false,
    )

    private val data = listOf(a(1, "AddCard"), a(2, "MoveCard"), a(3, "AddColumn"), a(4, "CardOpFailed"))

    @Test
    fun filters_by_type_glob() {
        val out = QuerySpec(type = "*Card*").apply(data)
        assertEquals(listOf(1, 2), out.map { it.actionId })
    }

    @Test
    fun filters_by_since_until_id_and_last() {
        assertEquals(listOf(2, 3), QuerySpec(sinceId = 2, untilId = 3).apply(data).map { it.actionId })
        assertEquals(listOf(3, 4), QuerySpec(last = 2).apply(data).map { it.actionId })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureQueryTest"`
Expected: FAIL — `QuerySpec` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `CaptureQuery.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.capture

import org.reduxkotlin.devtools.bridge.BridgeMessage

/** A filter over captured actions. `type` is a glob (`*` wildcard); id/time bounds are inclusive. */
internal data class QuerySpec(
    val type: String? = null,
    val sinceId: Int? = null,
    val untilId: Int? = null,
    val sinceTs: Long? = null,
    val untilTs: Long? = null,
    val last: Int? = null,
) {
    /** Apply the filter, preserving capture order; `last` truncates to the final N after filtering. */
    fun apply(actions: List<BridgeMessage.Action>): List<BridgeMessage.Action> {
        val rx = type?.let { Regex("^" + Regex.escape(it).replace("\\*", ".*") + "$") }
        val filtered = actions.filter { a ->
            (rx == null || rx.matches(actionType(a.action))) &&
                (sinceId == null || a.actionId >= sinceId) &&
                (untilId == null || a.actionId <= untilId) &&
                (sinceTs == null || a.timestampMillis >= sinceTs) &&
                (untilTs == null || a.timestampMillis <= untilTs)
        }
        return if (last != null && last < filtered.size) filtered.takeLast(last) else filtered
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureQueryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "feat(devtools-cli): capture query filtering (type glob, id/ts bounds, last-N)"
```

---

## Task 5: `capture/CaptureFormatter` — the `--format` tiers

**Files:**
- Create: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureFormatter.kt`
- Test: `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureFormatterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CaptureFormatterTest.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.core.DiffEntry
import org.reduxkotlin.devtools.core.DiffOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureFormatterTest {
    private val action = BridgeMessage.Action(
        actionId = 7,
        action = buildJsonObject { put("type", JsonPrimitive("AddCard")) },
        state = buildJsonObject { put("count", JsonPrimitive(1)) },
        diff = listOf(DiffEntry(DiffOp.CHANGED, "count", JsonPrimitive(0), JsonPrimitive(1))),
        timestampMillis = 123L,
        isExcess = false,
    )

    @Test
    fun actions_tier_omits_diff_and_state() {
        val line = formatRecord(action, Format.ACTIONS, store = "taskflow::root")
        val obj = Json.parseToJsonElement(line).jsonObject
        assertEquals(setOf("actionId", "type", "store", "ts"), obj.keys)
    }

    @Test
    fun diff_tier_adds_diff_only() {
        val obj = Json.parseToJsonElement(formatRecord(action, Format.DIFF, "s")).jsonObject
        assertTrue("diff" in obj.keys && "state" !in obj.keys)
    }

    @Test
    fun full_tier_adds_state() {
        val obj = Json.parseToJsonElement(formatRecord(action, Format.FULL, "s")).jsonObject
        assertTrue("diff" in obj.keys && "state" in obj.keys)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureFormatterTest"`
Expected: FAIL — `Format`/`formatRecord` unresolved.

> Note: confirm the `DiffEntry`/`DiffOp` package is `org.reduxkotlin.devtools.core` by checking `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/JsonDiff.kt`'s `package` line; adjust the import if it differs.

- [ ] **Step 3: Write minimal implementation**

Create `CaptureFormatter.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.bridgeJson

/** Output granularity tiers; each is a strict superset of the previous. */
internal enum class Format { ACTIONS, DIFF, FULL }

private val prettyJson = Json(bridgeJson) { prettyPrint = true }

/** Render one action record to a single JSON object string at the requested [format] tier. */
internal fun formatRecord(action: BridgeMessage.Action, format: Format, store: String, pretty: Boolean = false): String {
    val obj: JsonObject = buildJsonObject {
        put("actionId", JsonPrimitive(action.actionId))
        put("type", JsonPrimitive(actionType(action.action)))
        put("store", JsonPrimitive(store))
        put("ts", JsonPrimitive(action.timestampMillis))
        if (action.isExcess) put("isExcess", JsonPrimitive(true))
        if (format == Format.DIFF || format == Format.FULL) {
            put("diff", bridgeJson.encodeToJsonElement(action.diff) as JsonElement)
        }
        if (format == Format.FULL) put("state", action.state)
    }
    val j = if (pretty) prettyJson else bridgeJson
    return j.encodeToString(JsonObject.serializer(), obj)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureFormatterTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "feat(devtools-cli): capture formatter (actions/diff/full tiers, json-lines/pretty)"
```

---

## Task 6: `server/CaptureWriter` — atomic per-store file write

**Files:**
- Create: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/server/CaptureWriter.kt`
- Test: `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/server/CaptureWriterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CaptureWriterTest.kt` (round-trips through the real reader):

```kotlin
package org.reduxkotlin.devtools.cli.server

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.cli.capture.readCapture
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureWriterTest {
    @Test
    fun writes_a_gui_loadable_recording_for_a_store() {
        val dir = Files.createTempDirectory("rkcap").toFile()
        val header = RecordingHeader(
            protocolVersion = PROTOCOL_VERSION, serializerTier = "json",
            clientId = "taskflow", clientLabel = "TaskFlow", storeName = "TaskFlow", storeInstanceId = "root",
        )
        val msgs = listOf(
            BridgeMessage.Action(1, buildJsonObject { put("type", JsonPrimitive("A")) }, buildJsonObject {}, emptyList(), 1L, false),
        )
        val file: File = writeStoreCapture(dir, "taskflow::root", header, msgs)
        assertTrue(file.name.endsWith(".jsonl"))
        assertEquals(listOf(1), readCapture(file).second.map { it.actionId })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureWriterTest"`
Expected: FAIL — `writeStoreCapture` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `CaptureWriter.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.server

import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.encodeRecording
import org.reduxkotlin.devtools.cli.capture.captureFileName
import java.io.File

/** Atomically write a store's recording into [dir] as `<safeKey>.jsonl`; returns the file. */
internal fun writeStoreCapture(
    dir: File,
    storeKey: String,
    header: RecordingHeader,
    messages: List<BridgeMessage>,
): File {
    dir.mkdirs()
    val target = File(dir, captureFileName(storeKey))
    val tmp = File(dir, target.name + ".tmp")
    tmp.writeText(encodeRecording(header, messages))
    tmp.copyTo(target, overwrite = true)
    tmp.delete()
    return target
}
```

> `copyTo` + delete is used rather than `renameTo` so an in-progress reader on Windows never observes a missing target. The reader (Task 3) already tolerates a partial trailing line.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureWriterTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "feat(devtools-cli): atomic per-store capture writer"
```

---

## Task 7: `server/CaptureFlusher` — snapshot the ingest to disk

**Files:**
- Create: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/server/CaptureFlusher.kt`
- Test: `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/server/CaptureFlusherTest.kt`

This wraps the reused `MonitorIngest`: it feeds wire messages into a `MonitorIngest.Connection`, then flushes every known store via `ingest.recordingFor(storeId)` → `writeStoreCapture`.

- [ ] **Step 1: Write the failing test**

Create `CaptureFlusherTest.kt`. It drives a real `MonitorIngest` with a Hello + an Action, flushes, and asserts a capture file exists with the action:

```kotlin
package org.reduxkotlin.devtools.cli.server

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.cli.capture.discoverStores
import org.reduxkotlin.devtools.cli.capture.readCapture
import org.reduxkotlin.devtools.monitor.MonitorIngest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureFlusherTest {
    @Test
    fun flushes_each_store_to_a_capture_file() {
        val dir = Files.createTempDirectory("rkflush").toFile()
        val ingest = MonitorIngest()
        val conn = ingest.openConnection()
        conn.accept(
            BridgeMessage.Hello(
                protocolVersion = PROTOCOL_VERSION, clientId = "taskflow", clientLabel = "TaskFlow",
                storeInstanceId = "root", storeName = "TaskFlow", serializerTier = "json",
            ),
        )
        conn.accept(
            BridgeMessage.Action(1, buildJsonObject { put("type", JsonPrimitive("A")) }, buildJsonObject {}, emptyList(), 1L, false),
        )

        flushAll(ingest, dir)

        val stores = discoverStores(dir)
        assertEquals(listOf("taskflow::root"), stores.map { it.key })
        assertEquals(listOf(1), readCapture(stores.first().file).second.map { it.actionId })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureFlusherTest"`
Expected: FAIL — `flushAll` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `CaptureFlusher.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.reduxkotlin.devtools.monitor.MonitorIngest
import java.io.File

/** Write every store currently known to [ingest] into [dir] as a `.jsonl` recording. */
internal fun flushAll(ingest: MonitorIngest, dir: File) {
    ingest.registry.state.value.stores.forEach { entry ->
        val rec = ingest.recordingFor(entry.ref.id) ?: return@forEach
        writeStoreCapture(dir, entry.ref.id, rec.first, rec.second)
    }
}

/** Continuously flush captures to [dir] whenever the ingest registry changes. Launches in [scope]. */
internal fun startFlushing(scope: CoroutineScope, ingest: MonitorIngest, dir: File) {
    scope.launch {
        ingest.registry.state.collectLatest { flushAll(ingest, dir) }
    }
}
```

> `entry.ref.id` is the store key `clientId::storeInstanceId` (see `StoreRegistryModel`/`MonitorIngest`). Confirm `StoreEntry.ref.id` carries that exact key by reading `StoreRegistryModel.put` call sites in `MonitorIngest`; if the registry id differs from the recording key, pass the `recordingFor` key through instead.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*CaptureFlusherTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "feat(devtools-cli): flush reused MonitorIngest snapshots to per-store captures"
```

---

## Task 8: `command/QueryOptions` + `Stores`/`Actions`/`Diff`/`State` commands

**Files:**
- Create: `redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/command/QueryOptions.kt`
- Create: `.../command/StoresCommand.kt`, `ActionsCommand.kt`, `DiffCommand.kt`, `StateCommand.kt`
- Test: `.../src/test/kotlin/org/reduxkotlin/devtools/cli/command/QueryResolveTest.kt`

The query commands are thin: resolve the capture dir + target store file, read, filter, format, print. Put the resolve/select logic in a testable helper; the clikt classes are wiring.

- [ ] **Step 1: Write the failing test for store resolution**

Create `QueryResolveTest.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.command

import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.cli.server.writeStoreCapture
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QueryResolveTest {
    private fun seed(dir: java.io.File, key: String) {
        val (c, s) = key.split("::")
        writeStoreCapture(dir, key, RecordingHeader(
            protocolVersion = PROTOCOL_VERSION, serializerTier = "json",
            clientId = c, clientLabel = c, storeName = c, storeInstanceId = s,
        ), listOf(BridgeMessage.Init(buildJsonObject {})))
    }

    @Test
    fun single_store_resolves_without_flag() {
        val dir = Files.createTempDirectory("rkq").toFile(); seed(dir, "app::root")
        assertEquals("app::root", resolveStore(dir, null).key)
    }

    @Test
    fun multiple_stores_require_the_flag() {
        val dir = Files.createTempDirectory("rkq").toFile(); seed(dir, "app::root"); seed(dir, "app::acct")
        assertFailsWith<IllegalStateException> { resolveStore(dir, null) }
        assertEquals("app::acct", resolveStore(dir, "app::acct").key)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*QueryResolveTest"`
Expected: FAIL — `resolveStore` unresolved.

- [ ] **Step 3: Write the shared query options + resolver**

Create `QueryOptions.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.reduxkotlin.devtools.cli.capture.Format
import org.reduxkotlin.devtools.cli.capture.QuerySpec
import org.reduxkotlin.devtools.cli.capture.StoreRef
import org.reduxkotlin.devtools.cli.capture.discoverStores
import java.io.File

/** Default capture directory under the current working dir. */
internal fun defaultCaptureDir(): File = File(".rk-devtools")

/** Pick the target store: the only one if unambiguous, or the one matching [key]. */
internal fun resolveStore(dir: File, key: String?): StoreRef {
    val stores = discoverStores(dir)
    check(stores.isNotEmpty()) { "no captures found in ${dir.path} (is `rk-devtools serve` running?)" }
    if (key != null) return stores.firstOrNull { it.key == key }
        ?: error("store '$key' not found; available: ${stores.joinToString { it.key }}")
    check(stores.size == 1) { "multiple stores present; pass --store <key>: ${stores.joinToString { it.key }}" }
    return stores.first()
}

/** Shared filter/format flags for query subcommands. */
internal class QueryOptions : OptionGroup() {
    val out by option("--out", help = "capture directory").default(".rk-devtools")
    val store by option("--store", help = "store key (clientId::storeInstanceId)")
    val type by option("--type", help = "action type glob, e.g. '*Card*'")
    val sinceId by option("--since", help = "min actionId").int()
    val untilId by option("--until", help = "max actionId").int()
    val last by option("--last", help = "keep only the final N").int()
    val format by option("--format").enum<Format>().default(Format.ACTIONS)
    val pretty by option("--pretty", help = "pretty-print JSON").default("false")

    fun spec() = QuerySpec(type = type, sinceId = sinceId, untilId = untilId, last = last)
    fun dir() = File(out)
    fun prettyEnabled() = pretty == "true" || pretty == ""
}
```

> `enum<Format>()` matches on lowercase via clikt's `--format actions|diff|full`; verify clikt's enum option is case-insensitive in 4.4.0, else add `.enum<Format>(ignoreCase = true)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-cli:test --tests "*QueryResolveTest"`
Expected: PASS.

- [ ] **Step 5: Write the four query commands**

Create `ActionsCommand.kt` (the `diff`/`state` variants reuse the same pieces):

```kotlin
package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import org.reduxkotlin.devtools.cli.capture.Format
import org.reduxkotlin.devtools.cli.capture.formatRecord
import org.reduxkotlin.devtools.cli.capture.readCapture

/** `actions` — list captured actions at the chosen --format tier. */
internal class ActionsCommand(private val forced: Format? = null) :
    CliktCommand(name = if (forced == Format.DIFF) "diff" else "actions") {
    private val q by QueryOptions()
    override fun run() {
        val ref = resolveStore(q.dir(), q.store)
        val actions = q.spec().apply(readCapture(ref.file).second)
        val fmt = forced ?: q.format
        actions.forEach { echo(formatRecord(it, fmt, ref.key, q.prettyEnabled())) }
    }
}
```

Create `DiffCommand.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.command

import org.reduxkotlin.devtools.cli.capture.Format

/** `diff` — alias for `actions --format diff`. */
internal class DiffCommand : ActionsCommand(forced = Format.DIFF)
```

Create `StateCommand.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.reduxkotlin.devtools.bridge.bridgeJson
import org.reduxkotlin.devtools.cli.capture.readCapture
import kotlinx.serialization.json.JsonElement

/** `state` — print the full post-state after the latest action (or --at <actionId>). */
internal class StateCommand : CliktCommand(name = "state") {
    private val out by option("--out").default(".rk-devtools")
    private val store by option("--store")
    private val at by option("--at").int()
    override fun run() {
        val ref = resolveStore(java.io.File(out), store)
        val actions = readCapture(ref.file).second
        val target = if (at != null) actions.firstOrNull { it.actionId == at } else actions.lastOrNull()
        if (target == null) { echo("no matching action", err = true); return }
        echo(bridgeJson.encodeToString(JsonElement.serializer(), target.state))
    }
}
```

> `StateCommand` needs `import com.github.ajalt.clikt.parameters.options.default`.

Create `StoresCommand.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import org.reduxkotlin.devtools.cli.capture.discoverStores

/** `stores` — list the store keys present in the capture directory. */
internal class StoresCommand : CliktCommand(name = "stores") {
    private val out by option("--out").default(".rk-devtools")
    override fun run() = discoverStores(java.io.File(out)).forEach { echo("${it.key}\t${it.name}") }
}
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :redux-kotlin-devtools-cli:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "feat(devtools-cli): query subcommands (stores/actions/diff/state) + shared options"
```

---

## Task 9: `command/ServeCommand` + `TailCommand` + `RootCommand` wiring

**Files:**
- Create: `.../command/ServeCommand.kt`, `TailCommand.kt`, `RootCommand.kt`
- Modify: `.../Main.kt`

- [ ] **Step 1: Write the serve command**

Create `ServeCommand.kt`. It reuses the standalone server + ingest, starts flushing, and either renders the GUI (`--ui`) or blocks headless:

```kotlin
package org.reduxkotlin.devtools.cli.command

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.reduxkotlin.devtools.cli.server.startFlushing
import org.reduxkotlin.devtools.monitor.MonitorIngest
import org.reduxkotlin.devtools.monitor.MonitorServer
import org.reduxkotlin.devtools.monitor.rememberMonitorState
import org.reduxkotlin.devtools.monitor.ui.MonitorApp
import java.io.File

/** `serve` — host the bridge receiver, write per-store captures, optionally launch the GUI. */
internal class ServeCommand : CliktCommand(name = "serve") {
    private val port by option("--port").int().default(9090)
    private val host by option("--host").default("127.0.0.1")
    private val token by option("--token")
    private val out by option("--out").default(".rk-devtools")
    private val ui by option("--ui", help = "also launch the GUI monitor").flag()

    override fun run() {
        val dir = File(out).apply { mkdirs() }
        val ingest = MonitorIngest()
        val server = MonitorServer(ingest, port = port, host = host, token = token)
        val bound = server.start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startFlushing(scope, ingest, dir)
        echo("serving bridge on $host:$bound  → captures in ${dir.path}")
        if (ui) {
            application {
                Window(onCloseRequest = { server.stop(); exitApplication() }, title = "Redux DevTools Monitor") {
                    MonitorApp(ingest, rememberMonitorState(ingest))
                }
            }
        } else {
            runBlocking { kotlinx.coroutines.awaitCancellation() }
        }
    }
}
```

> Verify the exact package/signature of `MonitorApp` and `rememberMonitorState` against `redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/Main.kt` (it uses `org.reduxkotlin.devtools.monitor.ui.MonitorApp` and `rememberMonitorState(ingest)`); adjust imports to match.

- [ ] **Step 2: Write the tail command**

Create `TailCommand.kt` (one-shot prints recent; `--follow` polls the file):

```kotlin
package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.core.CliktCommand
import org.reduxkotlin.devtools.cli.capture.formatRecord
import org.reduxkotlin.devtools.cli.capture.readCapture

/** `tail` — print recent actions; with --follow, poll the capture for new ones. */
internal class TailCommand : CliktCommand(name = "tail") {
    private val q by QueryOptions()
    private val follow by com.github.ajalt.clikt.parameters.options.option("--follow")
        .flag()

    override fun run() {
        val ref = resolveStore(q.dir(), q.store)
        var lastId = -1
        fun pump() {
            readCapture(ref.file).second
                .filter { it.actionId > lastId }
                .let { q.spec().apply(it) }
                .forEach { lastId = maxOf(lastId, it.actionId); echo(formatRecord(it, q.format, ref.key, q.prettyEnabled())) }
        }
        pump()
        while (follow) { Thread.sleep(POLL_MS); runCatching { pump() } }
    }

    private companion object { const val POLL_MS = 300L }
}
```

> Needs `import com.github.ajalt.clikt.parameters.options.flag`.

- [ ] **Step 3: Write the root command and wire Main**

Create `RootCommand.kt`:

```kotlin
package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

/** Root `rk-devtools` command; dispatches to subcommands. */
internal class RootCommand : CliktCommand(name = "rk-devtools") {
    override fun run() = Unit
}

/** Build the configured command tree. */
internal fun rootCommand(): CliktCommand = RootCommand().subcommands(
    ServeCommand(), StoresCommand(), ActionsCommand(), DiffCommand(), StateCommand(), TailCommand(),
)
```

Replace `Main.kt`:

```kotlin
package org.reduxkotlin.devtools.cli

import org.reduxkotlin.devtools.cli.command.rootCommand

/** CLI entry point for the redux-kotlin DevTools tool. */
public fun main(args: Array<String>) {
    rootCommand().main(args)
}
```

- [ ] **Step 4: Verify the command tree builds and prints help**

Run: `./gradlew :redux-kotlin-devtools-cli:run --args="--help"`
Expected: lists subcommands `serve`, `stores`, `actions`, `diff`, `state`, `tail`.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "feat(devtools-cli): serve (reused server + optional GUI), tail, root command"
```

---

## Task 10: End-to-end smoke test + distribution

**Files:**
- Test: `redux-kotlin-devtools-cli/src/test/kotlin/org/reduxkotlin/devtools/cli/EndToEndTest.kt`

- [ ] **Step 1: Write an end-to-end test (server → capture → query helpers)**

This drives the real `MonitorServer` over a loopback WebSocket using the existing `BridgeOutput` client path is heavy; instead assert the headless pipeline: feed `MonitorIngest`, flush, then run the query resolver + formatter end to end.

Create `EndToEndTest.kt`:

```kotlin
package org.reduxkotlin.devtools.cli

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.cli.capture.Format
import org.reduxkotlin.devtools.cli.capture.formatRecord
import org.reduxkotlin.devtools.cli.capture.readCapture
import org.reduxkotlin.devtools.cli.command.resolveStore
import org.reduxkotlin.devtools.cli.server.flushAll
import org.reduxkotlin.devtools.monitor.MonitorIngest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndToEndTest {
    @Test
    fun ingest_to_capture_to_query() {
        val dir = Files.createTempDirectory("rke2e").toFile()
        val ingest = MonitorIngest()
        val conn = ingest.openConnection()
        conn.accept(BridgeMessage.Hello(PROTOCOL_VERSION, "taskflow", "TaskFlow", "root", "TaskFlow", "json"))
        conn.accept(BridgeMessage.Action(1, buildJsonObject { put("type", JsonPrimitive("AddCard")) }, buildJsonObject {}, emptyList(), 1L, false))

        flushAll(ingest, dir)

        val ref = resolveStore(dir, null)
        val actions = readCapture(ref.file).second
        assertEquals(listOf("AddCard"), actions.map { org.reduxkotlin.devtools.cli.capture.actionType(it.action) })
        assertTrue(formatRecord(actions.first(), Format.ACTIONS, ref.key).contains("\"type\":\"AddCard\""))
    }
}
```

- [ ] **Step 2: Run the full module test suite**

Run: `./gradlew :redux-kotlin-devtools-cli:test`
Expected: PASS (all capture/server/command/e2e tests).

- [ ] **Step 3: Verify the distributable installs**

Run: `./gradlew :redux-kotlin-devtools-cli:installDist`
Expected: BUILD SUCCESSFUL; a launcher appears at `redux-kotlin-devtools-cli/build/install/rk-devtools/bin/rk-devtools`.

- [ ] **Step 4: Full gate**

Run: `./gradlew :redux-kotlin-devtools-cli:test detektAll apiCheck`
Expected: BUILD SUCCESSFUL. (No `apiDump` for the CLI module — it is not a published library and applies no `convention.publishing`.)

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-cli
git commit -m "test(devtools-cli): end-to-end ingest→capture→query + installDist"
```

---

## Task 11: Document the opt-in in the agent reference set

**Files:**
- Modify: `docs/agent/references/effects-sync.md` or create a short `docs/agent/references/devtools.md` (follow the reference-set format + anchor conventions in `docs/agent/references/_template.md`).

- [ ] **Step 1: Add the integrator wiring + agent usage**

Document the app-side opt-in (the `BridgeOutput(BridgeConfig(startEnabled = true))` block from the spec §8) and the agent debugging loop (`rk-devtools serve` in the background, then `rk-devtools diff --store <k> --type '*Card*' --last 10`). Cite real anchors (`redux-kotlin-devtools-bridge/.../BridgeOutput.kt → BridgeOutput`, etc.) so `scripts/check-agent-refs.sh` passes.

- [ ] **Step 2: Validate anchors + commit**

Run: `bash scripts/check-agent-refs.sh`
Expected: ANCHOR CHECK OK.

```bash
git add docs/agent
git commit -m "docs(agent): document DevTools CLI debugging loop + bridge opt-in"
```

---

## Self-review notes (resolved during planning)

- **Spec §4 dependency claim adjusted:** the reusable server (`MonitorServer`/`MonitorIngest`) lives in the Compose `-standalone` module, so `serve` depends on it (Compose pulled into the once-run daemon only). Query subcommands depend solely on the Compose-free `capture/` lib. The `.jsonl` codec is moved to `-devtools-bridge` (Task 0) so `capture/` shares it without Compose. Approved by the user.
- **Spec §5 "one file" refined to one file *per store*:** the recording header is single-store, so a multiplexed single file would break GUI-loadability. Per-store files preserve interop and make `--store` a file selection. Captured in Tasks 2/6/7.
- **`isExcess` preserved** in `formatRecord` (Task 5) so eviction is visible, per spec §5.
- **Deferred items remain out:** no `digest`, no pipeline-timing in output, no dispatch-back, no MCP, no rotation (the `capture/` lib is the MCP seam; per-store snapshot bounded by `MonitorIngest` MAX_CAPTURED=5000 — note in Task 7 if larger histories are needed later).

## Open verification points for the implementer (cheap to confirm, flagged at use site)

1. `DiffEntry`/`DiffOp` package (`org.reduxkotlin.devtools.core`?) — Task 5 Step 2.
2. clikt 4.4.0 enum-option case sensitivity — Task 8 Step 3.
3. `StoreEntry.ref.id` == recording store key — Task 7 Step 3.
4. `MonitorApp`/`rememberMonitorState` import paths — Task 9 Step 1.
