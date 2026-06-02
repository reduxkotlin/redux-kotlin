# DevTools Standalone — Plan C: Monitor App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `redux-kotlin-devtools-standalone` — a Compose Multiplatform **application** (Desktop/JVM + Web/wasmJs) that ingests `BridgeOutput` streams from debugged apps and presents them in the IDE-dock monitor UI (multi-store rail, action log, State/Diff/Pipeline, time-travel timeline, search, save/load), reusing `-ui` and the `-bridge` wire protocol.

**Architecture:** Shared `commonMain` holds the **ingestion** (`MonitorIngest`: a stream of `BridgeMessage` → an `InAppModel`, keyed `clientId + storeInstanceId`) feeding a `StoreRegistryModel`, plus the Compose dock UI (reusing `-ui`'s `StateTab`/`DiffTab`/`PipelineTab` + `StoreRegistryModel`; new rail/log/timeline/top-bar from the `monitor/` hi-fi kit). `jvmMain` runs the embedded Ktor **server** (accepts bridge clients, enforces the handshake/token, decodes frames into `MonitorIngest`) and **hosts the web bundle + a browser WS feed**; `wasmJsMain` is the same UI connecting back as a browser WS **client** (same-origin). Save/load is `expect/actual` (JVM filesystem `.jsonl` / Web Blob).

**Tech Stack:** Compose Multiplatform 1.11 (desktop application + wasmJs browser), Ktor server (CIO, JVM), Ktor client (JS, web), kotlinx-serialization-json, kotlinx-coroutines. Reuses `-ui`, `-core`, `-bridge`. **Not published** (an app).

**Source spec:** `docs/superpowers/specs/2026-06-02-redux-kotlin-devtools-standalone-design.md`. **Visual source of truth:** `docs/superpowers/specs/ReduxKotlin Design System Updated/monitor/` + `Standalone DevTools - Desktop Monitor.html`. **Requires:** Plan A (`-ui`, `StoreRegistryModel`) + Plan B (`-bridge` wire protocol) complete.

**Conventions:** pre-commit `detektAll --auto-correct` → re-stage+amend until clean; never `--no-verify`; an app module is excluded from KDoc rules (like `examples/`) — confirm via `convention.control`; explicit `git add <paths>`.

---

## File structure

### New module `redux-kotlin-devtools-standalone` (package `org.reduxkotlin.devtools.monitor`)
- `build.gradle.kts` — CMP **application** (jvm + wasmJs); deps `-ui`, `-core`, `-bridge`, ktor-server (jvmMain), ktor-client (wasmJsMain), compose; `convention.control` (non-published). `compose.desktop.application { mainClass = "org.reduxkotlin.devtools.monitor.MainKt" }`.
- `commonMain/.../monitor/MonitorIngest.kt` — decode `BridgeMessage` stream → `InAppModel` per store; populate a `StoreRegistryModel` with `StoreRef(clientId+storeInstanceId, storeName)` grouped by client.
- `commonMain/.../monitor/MonitorState.kt` — top-level UI state (selection, search query/regex, paused, theme) layered over `StoreRegistryModel`; `ClientGroup` view for the rail.
- `commonMain/.../monitor/Recording.kt` — `expect` save/load (`.jsonl` with versioned header) — `actual` per platform.
- `commonMain/.../monitor/ui/*` — `MonitorTheme.kt`, `TopBar.kt`, `StoreRail.kt`, `ActionLog.kt`, `Timeline.kt`, `Dock.kt` (the shell; State/Diff/Pipeline panels reuse `-ui` tabs).
- `commonMain/.../monitor/App.kt` — the root `@Composable MonitorApp(registry, state)`.
- `jvmMain/.../monitor/MonitorServer.kt` — Ktor WS server + web host.
- `jvmMain/.../monitor/Main.kt` — desktop `application { Window { MonitorApp(...) } }`, starts `MonitorServer`.
- `jvmMain/.../monitor/Recording.jvm.kt` — filesystem save/load.
- `wasmJsMain/.../monitor/Main.kt` — `ComposeViewport { MonitorApp(...) }` + browser WS client.
- `wasmJsMain/.../monitor/Recording.wasmJs.kt` — Blob/File save/load.

### Modified
- `settings.gradle.kts` — add `":redux-kotlin-devtools-standalone"`.
- `examples/taskflow/...` — add a debug `BridgeOutput` (Task 9).
- `docs/devtools.md` — standalone section (Task 9).

---

## Task 1: Scaffold the application module

**Files:** Create `redux-kotlin-devtools-standalone/build.gradle.kts`; modify `settings.gradle.kts`; create source dirs.

- [ ] **Step 1: settings include** — add `":redux-kotlin-devtools-standalone",` to `settings.gradle.kts`.

- [ ] **Step 2: `build.gradle.kts`** (mirror `examples/taskflow/composeApp` for the CMP app shape; monitor targets **jvm + wasmJs only** — no android/ios)

```kotlin
plugins {
    id("convention.control")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":redux-kotlin-devtools-ui"))
                implementation(project(":redux-kotlin-devtools-core"))
                implementation(project(":redux-kotlin-devtools-bridge"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest { dependencies { implementation(libs.kotlinx.coroutines.test) } }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
            }
        }
        named("jvmTest") {
            dependencies {
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
            }
        }
        wasmJsMain { dependencies { implementation(libs.ktor.client.js) } }
    }
}

compose.desktop {
    application {
        mainClass = "org.reduxkotlin.devtools.monitor.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "ReduxKotlinDevTools"
        }
    }
}
```
> **Adapt to actuals:** mirror the EXACT plugin aliases + `compose.desktop`/`wasmJs` DSL from `examples/taskflow/composeApp/build.gradle.kts` (it compiles today). `convention.control` is the repo's non-published app convention (used by examples). If `compose.desktop` packaging DSL imports differ, copy taskflow's. Confirm `libs.plugins.kotlin.multiplatform` alias name.

- [ ] **Step 3: dirs**

```bash
mkdir -p redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/ui \
         redux-kotlin-devtools-standalone/src/commonTest/kotlin/org/reduxkotlin/devtools/monitor \
         redux-kotlin-devtools-standalone/src/jvmMain/kotlin/org/reduxkotlin/devtools/monitor \
         redux-kotlin-devtools-standalone/src/jvmTest/kotlin/org/reduxkotlin/devtools/monitor \
         redux-kotlin-devtools-standalone/src/wasmJsMain/kotlin/org/reduxkotlin/devtools/monitor
```

- [ ] **Step 4: verify + commit**

Run: `./gradlew :redux-kotlin-devtools-standalone:help --console=plain` → BUILD SUCCESSFUL.
```bash
git add settings.gradle.kts redux-kotlin-devtools-standalone/build.gradle.kts
git commit -m "build(devtools-standalone): scaffold Compose Desktop + Web app module"
```

---

## Task 2: `MonitorIngest` — bridge stream → StoreRegistryModel (TDD)

The decode core: given `BridgeMessage`s for a connection, key the store by `clientId + storeInstanceId`, create an `InAppModel`, translate wire messages → `DevToolsEvent` → `model.submit(...)`, and register the store in a shared `StoreRegistryModel`. Pure (no socket), fully testable.

**Files:**
- Create: `redux-kotlin-devtools-standalone/src/commonMain/.../monitor/MonitorIngest.kt`
- Test: `redux-kotlin-devtools-standalone/src/commonTest/.../monitor/MonitorIngestTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools.monitor

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals

class MonitorIngestTest {

    private fun hello(client: String, store: String) = BridgeMessage.Hello(
        protocolVersion = PROTOCOL_VERSION, clientId = client, clientLabel = "$client · test",
        storeInstanceId = store, storeName = store, serializerTier = "toString", token = null,
    )

    private fun action(id: Int, type: String, ts: Long) = BridgeMessage.Action(
        actionId = id, action = buildJsonObject { put("type", type) }, state = buildJsonObject { put("n", id) },
        diff = emptyList(), timestampMillis = ts, isExcess = false,
    )

    @Test
    fun a_connection_registers_a_store_keyed_by_client_and_instance() {
        val ingest = MonitorIngest()
        val conn = ingest.openConnection()
        conn.accept(hello("tf", "TaskFlow-root"))
        conn.accept(BridgeMessage.Init(buildJsonObject { put("n", 0) }))
        conn.accept(action(1, "AddCard", 100))

        val stores = ingest.registry.state.value.stores
        assertEquals(1, stores.size)
        assertEquals("tf::TaskFlow-root", stores.single().ref.id)
        assertEquals("TaskFlow-root", stores.single().ref.name)
        assertEquals(listOf(1), stores.single().state.actions.map { it.actionId })
    }

    @Test
    fun two_stores_from_one_client_group_under_it() {
        val ingest = MonitorIngest()
        ingest.openConnection().apply { accept(hello("tf", "TaskFlow-root")); accept(action(1, "A", 10)) }
        ingest.openConnection().apply { accept(hello("tf", "Account-2")); accept(action(1, "B", 20)) }
        assertEquals(listOf("tf::TaskFlow-root", "tf::Account-2"), ingest.registry.state.value.stores.map { it.ref.id })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-standalone:jvmTest --tests '*MonitorIngestTest*' --console=plain`
Expected: FAIL — `MonitorIngest` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools.monitor

import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.StoreRef
import org.reduxkotlin.devtools.inapp.model.StoreRegistryModel

/**
 * Decodes bridge message streams into per-store [InAppModel]s and registers them in [registry].
 * A store's key is `clientId::storeInstanceId` (stable across reconnects). Pure — no sockets; the
 * server (jvm) and the web client feed [Connection.accept] with decoded [BridgeMessage]s.
 */
public class MonitorIngest {

    /** The aggregate view the UI renders. */
    public val registry: StoreRegistryModel = StoreRegistryModel()

    /** Opens a logical connection (one per bridge client/store). */
    public fun openConnection(): Connection = Connection()

    /** One connection's decode state: the first [BridgeMessage.Hello] binds it to a store. */
    public inner class Connection {
        private var key: String? = null
        private var model: InAppModel? = null

        /** Feed one decoded wire message. The first must be a [BridgeMessage.Hello]. */
        public fun accept(message: BridgeMessage) {
            when (message) {
                is BridgeMessage.Hello -> {
                    val k = "${message.clientId}::${message.storeInstanceId}"
                    key = k
                    val m = InAppModel()
                    model = m
                    registry.put(StoreRef(k, message.storeName), m)
                }
                is BridgeMessage.Init -> emit(DevToolsEvent.Initialized(message.state))
                is BridgeMessage.Action -> emit(
                    DevToolsEvent.ActionRecorded(
                        message.actionId, message.action, message.state, message.diff, message.timestampMillis, message.isExcess,
                    ),
                )
                is BridgeMessage.PipelineRegistered -> emit(DevToolsEvent.PipelineRegistered(message.structure))
                is BridgeMessage.PipelineTraced -> emit(DevToolsEvent.PipelineTraced(message.trace))
                is BridgeMessage.HelloAck -> Unit // monitor never receives an ack
            }
        }

        /** Marks the store frozen (disconnected) — kept read-only in the registry. */
        public fun close() {
            // P0: leave the store registered (frozen); a freeze flag can be added to StoreRef/registry later.
        }

        private fun emit(event: DevToolsEvent) {
            val m = model ?: return // ignore events before Hello
            m.submit(event)
            registry.refresh()
        }
    }
}
```
> The frozen-on-disconnect status is visual polish; P0 keeps the store registered after `close()` (read-only by construction — the monitor never dispatches). A `frozen` flag can be threaded through `StoreRef`/`StoreRegistryState` in a follow-up; the rail's snow icon then keys off it.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-standalone:jvmTest --tests '*MonitorIngestTest*' --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/MonitorIngest.kt \
        redux-kotlin-devtools-standalone/src/commonTest/kotlin/org/reduxkotlin/devtools/monitor/MonitorIngestTest.kt
git commit -m "feat(devtools-standalone): bridge-stream ingestion into StoreRegistryModel"
```

---

## Task 3: `Recording` save/load (expect/actual, TDD on the shared codec)

The serialization codec (events ↔ `.jsonl` with a versioned header) is shared + testable; the file/Blob I/O is `expect/actual`.

**Files:**
- Create: `commonMain/.../monitor/Recording.kt` (codec + `expect`), `jvmMain/.../monitor/Recording.jvm.kt`, `wasmJsMain/.../monitor/Recording.wasmJs.kt`
- Test: `commonTest/.../monitor/RecordingCodecTest.kt`

- [ ] **Step 1: Write the failing test (codec round-trip)**

```kotlin
package org.reduxkotlin.devtools.monitor

import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingCodecTest {

    @Test
    fun encodes_a_versioned_header_then_one_line_per_message() {
        val msgs = listOf<BridgeMessage>(
            BridgeMessage.Init(buildJsonObject { put("n", 0) }),
            BridgeMessage.Action(1, buildJsonObject { put("type", "A") }, buildJsonObject { put("n", 1) }, emptyList(), 10L, false),
        )
        val jsonl = encodeRecording(RecordingHeader(protocolVersion = PROTOCOL_VERSION, serializerTier = "toString", clientId = "c", clientLabel = "C", storeName = "S", storeInstanceId = "s"), msgs)
        val lines = jsonl.trim().split("\n")
        assertEquals(3, lines.size) // header + 2 messages

        val (header, decoded) = decodeRecording(jsonl)
        assertEquals(PROTOCOL_VERSION, header.protocolVersion)
        assertEquals("S", header.storeName)
        assertEquals(2, decoded.size)
        assertEquals(1, (decoded[1] as BridgeMessage.Action).actionId)
    }
}
```

- [ ] **Step 2: Run → FAIL** (`encodeRecording`/`decodeRecording`/`RecordingHeader` unresolved):
`./gradlew :redux-kotlin-devtools-standalone:jvmTest --tests '*RecordingCodecTest*' --console=plain`

- [ ] **Step 3: Write `Recording.kt`**

```kotlin
package org.reduxkotlin.devtools.monitor

import kotlinx.serialization.Serializable
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.bridgeJson

/** First line of a `.jsonl` recording — pins the format + provenance for forward compatibility. */
@Serializable
public data class RecordingHeader(
    /** `"rk-devtools-recording"` discriminator. */ public val kind: String = "rk-devtools-recording",
    /** Bridge protocol version the events use. */ public val protocolVersion: Int,
    /** Serializer tier that produced the JSON. */ public val serializerTier: String,
    /** Source client id. */ public val clientId: String,
    /** Source client label. */ public val clientLabel: String,
    /** Source store name. */ public val storeName: String,
    /** Source store instance id. */ public val storeInstanceId: String,
)

/** Encodes a header + messages to newline-delimited JSON (`.jsonl`). */
public fun encodeRecording(header: RecordingHeader, messages: List<BridgeMessage>): String = buildString {
    appendLine(bridgeJson.encodeToString(RecordingHeader.serializer(), header))
    messages.forEach { appendLine(bridgeJson.encodeToString(BridgeMessage.serializer(), it)) }
}

/** Decodes a `.jsonl` recording into its header + messages (ignores blank lines). */
public fun decodeRecording(text: String): Pair<RecordingHeader, List<BridgeMessage>> {
    val lines = text.split("\n").filter { it.isNotBlank() }
    require(lines.isNotEmpty()) { "empty recording" }
    val header = bridgeJson.decodeFromString(RecordingHeader.serializer(), lines.first())
    val messages = lines.drop(1).map { bridgeJson.decodeFromString(BridgeMessage.serializer(), it) }
    return header to messages
}

/** Writes [text] as a recording file/blob named [suggestedName]; platform-specific. */
public expect fun saveRecording(suggestedName: String, text: String)

/** Prompts for + reads a recording file/blob; calls [onLoaded] with its contents (async on web). */
public expect fun loadRecording(onLoaded: (String) -> Unit)
```

- [ ] **Step 4: `Recording.jvm.kt`** (filesystem)

```kotlin
package org.reduxkotlin.devtools.monitor

import java.io.File

public actual fun saveRecording(suggestedName: String, text: String) {
    // P0: write next to the working dir; a native file dialog can replace this later.
    File(suggestedName).writeText(text)
}

public actual fun loadRecording(onLoaded: (String) -> Unit) {
    val f = File("$" + "recording.jsonl") // placeholder path; wired to a file picker in the desktop UI
    if (f.exists()) onLoaded(f.readText())
}
```
> Desktop file dialogs (AWT `FileDialog`) are wired in the UI; the `actual` above keeps a minimal default so the module compiles + the codec is testable. Replace the bodies with `FileDialog`-driven paths in Task 5's save/load buttons; keep the signatures.

- [ ] **Step 5: `Recording.wasmJs.kt`** (Blob download / File-input upload)

```kotlin
package org.reduxkotlin.devtools.monitor

public actual fun saveRecording(suggestedName: String, text: String) {
    // Browser: trigger a Blob download. Wired via kotlinx-browser / DOM APIs in the web entry.
    org.reduxkotlin.devtools.monitor.downloadBlob(suggestedName, text)
}

public actual fun loadRecording(onLoaded: (String) -> Unit) {
    org.reduxkotlin.devtools.monitor.pickFile(onLoaded)
}
```
> Implement `downloadBlob`/`pickFile` in `wasmJsMain` using the browser DOM (`Blob`, `URL.createObjectURL`, an `<input type=file>`). If the wasmJs DOM bindings (`kotlinx.browser`/`org.w3c.*`) aren't on the classpath, add the minimal dependency or use `external` declarations; keep `saveRecording`/`loadRecording` as the stable seam.

- [ ] **Step 6: Run codec test → PASS; compile jvm + wasmJs**

Run: `./gradlew :redux-kotlin-devtools-standalone:jvmTest --tests '*RecordingCodecTest*' :redux-kotlin-devtools-standalone:compileKotlinWasmJs --console=plain`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/Recording.kt \
        redux-kotlin-devtools-standalone/src/jvmMain/kotlin/org/reduxkotlin/devtools/monitor/Recording.jvm.kt \
        redux-kotlin-devtools-standalone/src/wasmJsMain/kotlin/org/reduxkotlin/devtools/monitor/Recording.wasmJs.kt \
        redux-kotlin-devtools-standalone/src/commonTest/kotlin/org/reduxkotlin/devtools/monitor/RecordingCodecTest.kt
git commit -m "feat(devtools-standalone): recording codec (.jsonl + versioned header) + save/load seam"
```

---

## Task 4: `MonitorServer` (JVM Ktor WS server + handshake/token + web host)

**Files:** Create `jvmMain/.../monitor/MonitorServer.kt`; Test `jvmTest/.../monitor/MonitorServerTest.kt`

- [ ] **Step 1: Write the failing integration test** (real loopback WS round-trip using `-bridge`'s `BridgeOutput` as the client)

```kotlin
package org.reduxkotlin.devtools.monitor

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsSession
import org.reduxkotlin.devtools.bridge.BridgeConfig
import org.reduxkotlin.devtools.bridge.BridgeOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonitorServerTest {

    @Test
    fun a_bridge_client_connection_populates_the_registry() = runTest {
        val ingest = MonitorIngest()
        val server = MonitorServer(ingest, port = 0)          // 0 = ephemeral
        val port = server.start()                              // returns bound port
        try {
            val session = DevToolsSession.create(DevToolsConfig(name = "TaskFlow-root"))
            val out = BridgeOutput(BridgeConfig(host = "127.0.0.1", port = port, clientId = "tf", clientLabel = "TaskFlow"))
            out.start(session)
            session.init(mapOf("n" to 0))
            session.record(Unit, mapOf("n" to 1))
            withTimeout(5_000) {
                while (ingest.registry.state.value.stores.firstOrNull()?.state?.actions?.isEmpty() != false) delay(50)
            }
            out.stop(); session.close()
            val store = ingest.registry.state.value.stores.single()
            assertTrue(store.ref.id.startsWith("tf::"))
            assertEquals(true, store.state.actions.isNotEmpty())
        } finally {
            server.stop()
        }
    }
}
```
> `record(Unit, ...)` just needs a non-null action; adapt the action/state types to whatever serializes cleanly with the default serializer. The test exercises bridge→server→ingest end-to-end on loopback.

- [ ] **Step 2: Run → FAIL** (`MonitorServer` unresolved).

- [ ] **Step 3: Write `MonitorServer.kt`**

```kotlin
package org.reduxkotlin.devtools.monitor

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.bridgeJson

/**
 * The monitor's embedded Ktor WS server. Binds loopback by default; each `/bridge` connection
 * handshakes (validating [token] for non-loopback), then streams decoded [BridgeMessage]s into a
 * fresh [MonitorIngest.Connection]. Defensive: malformed frames are skipped; one client can't crash
 * the server or other sessions.
 *
 * @param ingest the registry-backed ingestion.
 * @param port bind port (`0` = ephemeral, useful for tests); the bound port is returned by [start].
 * @param host bind host (loopback default).
 * @param token required when [host] is non-loopback.
 */
public class MonitorServer(
    private val ingest: MonitorIngest,
    private val port: Int = 9090,
    private val host: String = "127.0.0.1",
    private val token: String? = null,
) {
    private var server: EmbeddedServer<*, *>? = null

    /** Starts the server; returns the actually-bound port. */
    public fun start(): Int {
        val s = embeddedServer(CIO, port = port, host = host) {
            install(WebSockets)
            routing {
                webSocket("/bridge") {
                    val conn = ingest.openConnection()
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val msg = runCatching { bridgeJson.decodeFromString(BridgeMessage.serializer(), frame.readText()) }.getOrNull() ?: continue
                            if (msg is BridgeMessage.Hello) {
                                val loopback = host == "127.0.0.1" || host == "::1" || host == "localhost"
                                val ok = (loopback || (token != null && msg.token == token)) && msg.protocolVersion == PROTOCOL_VERSION
                                send(Frame.Text(bridgeJson.encodeToString(BridgeMessage.serializer(),
                                    BridgeMessage.HelloAck(PROTOCOL_VERSION, accepted = ok, reason = if (ok) null else "refused"))))
                                if (!ok) break
                            }
                            conn.accept(msg)
                        }
                    } finally {
                        conn.close()
                    }
                }
                // Task 6 adds the web-bundle host routes here.
            }
        }
        server = s
        s.start(wait = false)
        return s.engineConfig.connectors.firstOrNull()?.port ?: port // resolve ephemeral port
    }

    /** Stops the server. */
    public fun stop() {
        server?.stop(500, 1000)
        server = null
    }
}
```
> **Adapt to actuals:** the exact Ktor-server API (`embeddedServer(CIO, ...)`, resolving the bound ephemeral port, `EmbeddedServer` type) varies by Ktor version — verify against the Ktor version in `libs.versions.toml` and `-remote`'s jvmTest server usage. Resolving the ephemeral port may need `s.engine.resolvedConnectors()` (a suspend call) instead of `engineConfig.connectors`; use whatever this version exposes. Keep `start(): Int` returning the bound port.

- [ ] **Step 4: Run → PASS** (the round-trip populates the registry). Iterate on Ktor API names until green.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-standalone/src/jvmMain/kotlin/org/reduxkotlin/devtools/monitor/MonitorServer.kt \
        redux-kotlin-devtools-standalone/src/jvmTest/kotlin/org/reduxkotlin/devtools/monitor/MonitorServerTest.kt
git commit -m "feat(devtools-standalone): embedded Ktor WS server (handshake, token, decode → ingest)"
```

---

## Task 5: The dock UI (theme + shell, reusing `-ui` tabs)

Build the IDE-dock matching the `monitor/` hi-fi kit. Reuse `-ui`'s `StateTab(JsonElement?)`, `DiffTab(List<DiffEntry>)`, `PipelineTab(structure, trace)` for the center/right panels; build the rail, action log, timeline, and top bar fresh from the kit. Drive everything from `MonitorIngest.registry` (a `StoreRegistryModel`) + a small `MonitorState`.

> This task is UI-heavy; the `monitor/` kit (`app.jsx`, `chrome.jsx`, `inspector.jsx`, `colors_and_type.css`) is the exact spec for structure, spacing, and tokens. Read those files and translate to Compose. Sub-steps:

- [ ] **Step 1: `MonitorTheme.kt`** — a `MaterialTheme` from the kit's `--dt-*` tokens (dark + light), with a `theme` toggle. Reuse `org.reduxkotlin.devtools.inapp.theme.RkTokens` where colors match; add monitor-specific surfaces (`#0e1726` bg, `#0b1320` panel, rail `#0a111d`). Provide both palettes and a `@Composable MonitorTheme(dark: Boolean, content)`.

- [ ] **Step 2: `MonitorState.kt`** — UI state over the registry: `query`, `regex`, `paused`, `dark`, plus selection helpers delegating to `StoreRegistryModel` (`focus`/`selectAll`/`select`). A `clientGroups(StoreRegistryState): List<ClientGroup>` derives the rail's Client→Store grouping by splitting `StoreRef.id` on `"::"` (clientId prefix) — matching `data.jsx`'s `clientsOf`.

- [ ] **Step 3: `ui/TopBar.kt`** — logo + "Redux DevTools" + gradient "MONITOR" badge; a store-picker dropdown (Client→Store, status dots) calling `focus`; centered search field (placeholder "Search actions, payloads, serialized state…", regex `.*` toggle, match count); status `● N clients · ws://127.0.0.1:9090`; controls pause/reconnect/save/clear/theme (icon buttons). Mirror `app.jsx`'s `TopBar`/`StorePicker`.

- [ ] **Step 4: `ui/StoreRail.kt`** — "Clients & stores" header; an "All stores" row (calls `selectAll`); per-client groups with each store row showing a checkbox (toggle membership via `select(...)`), an accent dot or frozen snow, the store name, and the action count; footer stats. Mirror `chrome.jsx`'s `StoreRail`.

- [ ] **Step 5: `ui/ActionLog.kt`** — the merged/solo log: header with count + "merged by time"/"read-only"; rows = id · store chip (when merged) · type · payload preview · timestamp · "NΔ" badge; selection drives the inspector. Reuse `org.reduxkotlin.devtools.inapp.model.actionType`/`payloadPreview` logic (or port the kit's `payloadPreview`). Filter by `query`/`regex`. Mirror `chrome.jsx`'s `ActionLog`.

- [ ] **Step 6: `ui/Timeline.kt`** — bottom time-travel bar: prev/next, `#NN / #NN`, a clickable/draggable track with ticks (orange+glow where diff>0, blue traversed, faint future), gradient playhead, "time-travel · read-only" label. Scrub selects the recorded action. Mirror `chrome.jsx`'s `Timeline`.

- [ ] **Step 7: `ui/Dock.kt` + `App.kt`** — assemble: `winbar` (desktop traffic-light chrome) → `TopBar` → a row of [resizable `StoreRail` | `ActionLog` | center(State over Diff, resizable) | `Pipeline` docked] → `Timeline`. Center State uses `-ui` `StateTab(selected.state)`, Diff uses `DiffTab(selected.diff)`, Pipeline uses `PipelineTab(structure, traceForSelected)`. Wire splitters (pointer-drag width/height) per `app.jsx`'s `Splitter`. `@Composable fun MonitorApp(ingest: MonitorIngest, state: MonitorState)` is the root.

- [ ] **Step 8: Compile**

Run: `./gradlew :redux-kotlin-devtools-standalone:compileKotlinJvm --console=plain` → BUILD SUCCESSFUL. Resolve CMP symbol specifics (BoxWithConstraints, pointerInput drag, `compose.foundation` lazy lists) against `-inapp`/`-ui` which already use them.

- [ ] **Step 9: Commit**

```bash
git add redux-kotlin-devtools-standalone/src/commonMain/kotlin/org/reduxkotlin/devtools/monitor/
git commit -m "feat(devtools-standalone): IDE-dock UI (theme, rail, log, timeline, dock) reusing -ui tabs"
```

---

## Task 6: Desktop + Web entry points; wire P0 features end-to-end

**Files:** Create `jvmMain/.../monitor/Main.kt`, `wasmJsMain/.../monitor/Main.kt` (+ the `downloadBlob`/`pickFile` wasm helpers); extend `MonitorServer` web-host routes.

- [ ] **Step 1: Desktop `Main.kt`**

```kotlin
package org.reduxkotlin.devtools.monitor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

public fun main() {
    val ingest = MonitorIngest()
    val server = MonitorServer(ingest)        // binds 127.0.0.1:9090
    server.start()
    application {
        Window(onCloseRequest = { server.stop(); exitApplication() }, title = "Redux DevTools Monitor") {
            val state = rememberMonitorState(ingest)
            MonitorApp(ingest, state)
        }
    }
}
```
Add `rememberMonitorState(ingest)` to `MonitorState.kt` (collects `registry.state` + holds query/theme/etc.).

- [ ] **Step 2: Web host route** — in `MonitorServer`, add routes serving the compiled wasmJs bundle (`static`/`resources`) at `/` and the browser WS feed at `/bridge` (the same endpoint; the web client connects same-origin). The web client uses `window.location.host` to build the WS URL.

- [ ] **Step 3: Web `Main.kt`** — `ComposeViewport(document.body!!) { MonitorApp(ingest, state) }` where a wasmJs `MonitorIngest` is fed by a browser `WebSocket` to `ws://${location.host}/bridge` decoding `BridgeMessage` via `bridgeJson`. Implement `downloadBlob`/`pickFile` (DOM `Blob`/`<input type=file>`).
> The web build serving + same-origin WS is the most environment-specific piece; if the wasmJs resource-bundling/serving needs extra Gradle wiring, mirror `examples/taskflow/composeApp`'s wasmJs setup. Keep the web variant behind its own entry so the desktop app is independently runnable.

- [ ] **Step 4: Wire save/load + search + timeline to the UI** — connect the TopBar save button → `saveRecording("${storeName}.jsonl", encodeRecording(header, capturedMessages))`; a load affordance → `loadRecording { decodeRecording(it) … replay into a fresh MonitorIngest.Connection }`; search already filters in `ActionLog`; timeline scrub already selects. (Capture the raw `BridgeMessage`s per store during ingest so save has them — add a bounded `recent: List<BridgeMessage>` to `MonitorIngest.Connection` or reconstruct from the model.)

- [ ] **Step 5: Compile both targets**

Run: `./gradlew :redux-kotlin-devtools-standalone:compileKotlinJvm :redux-kotlin-devtools-standalone:compileKotlinWasmJs --console=plain` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-devtools-standalone/src/jvmMain redux-kotlin-devtools-standalone/src/wasmJsMain \
        redux-kotlin-devtools-standalone/src/commonMain
git commit -m "feat(devtools-standalone): desktop + web entry points; wire save/load/search/timeline"
```

---

## Task 7: Compose desktop smoke test

**Files:** Test `jvmTest/.../monitor/MonitorAppTest.kt`

- [ ] **Step 1: Write the test** (seed the registry directly, render, assert the dock shows)

```kotlin
package org.reduxkotlin.devtools.monitor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class MonitorAppTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renders_the_dock_with_a_seeded_store() = runComposeUiTest {
        val ingest = MonitorIngest()
        ingest.openConnection().apply {
            accept(BridgeMessage.Hello(PROTOCOL_VERSION, "tf", "TaskFlow", "TaskFlow-root", "TaskFlow-root", "toString", null))
            accept(BridgeMessage.Action(1, buildJsonObject { put("type", "AddCard") }, buildJsonObject { put("n", 1) }, emptyList(), 10L, false))
        }
        setContent {
            val state = rememberMonitorState(ingest)
            MonitorApp(ingest, state)
        }
        waitForIdle()
        onNodeWithText("AddCard").assertIsDisplayed()        // the action row rendered
        onNodeWithText("TaskFlow-root").assertIsDisplayed()  // the store in the rail/picker
    }
}
```
> Adapt the asserted texts to the actual labels rendered. Keep the assertions meaningful (an action row + the store appear). If `runComposeUiTest` needs a different entry on this CMP version, mirror `-inapp`'s `ReduxDevToolsHostTest`.

- [ ] **Step 2: Run → PASS**

Run: `./gradlew :redux-kotlin-devtools-standalone:jvmTest --tests '*MonitorAppTest*' --console=plain`

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-devtools-standalone/src/jvmTest/kotlin/org/reduxkotlin/devtools/monitor/MonitorAppTest.kt
git commit -m "test(devtools-standalone): desktop dock renders a seeded store"
```

---

## Task 8: Wire taskflow's bridge → the monitor (sample)

**Files:** Modify `examples/taskflow/composeApp/.../store/AppStore.kt` + `AccountStore.kt`; `examples/taskflow/composeApp/build.gradle.kts`.

- [ ] **Step 1: Add the bridge dep + register `BridgeOutput`**

In taskflow's `composeApp/build.gradle.kts` commonMain, add `implementation(project(":redux-kotlin-devtools-bridge"))`. In `AppStore`/`AccountStore`, after creating each store + its `DevToolsSession` (via the hub), register a `BridgeOutput(BridgeConfig(clientId = "taskflow", clientLabel = "TaskFlow · ${platform}"))` against the store's session **and start it** — guarded so it's debug/dev-only (the sample can start it unconditionally since it already depends on devtools directly per the in-app integration; keep it `startEnabled`-style behind a simple `if`). Resolve the session via `DevToolsHub.session(cfg.name)`.
> The exact wiring mirrors how the in-app integration already attaches `devTools(cfg)` to the taskflow stores; add the `BridgeOutput` start alongside. Distinct `storeInstanceId` per store (the config `name` already differs: "TaskFlow"/"TaskFlow-root").

- [ ] **Step 2: Compile taskflow jvm**

Run: `./gradlew :examples:taskflow:composeApp:compileKotlinJvm --console=plain` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add examples/taskflow
git commit -m "feat(taskflow): stream to the standalone monitor via BridgeOutput"
```

---

## Task 9: Docs + final gate

**Files:** Modify `docs/devtools.md`.

- [ ] **Step 1: Document the standalone monitor** in `docs/devtools.md`: a "Standalone monitor (desktop + web)" section — run the desktop app (`./gradlew :redux-kotlin-devtools-standalone:run`), add `BridgeOutput(BridgeConfig())` to your store (debug-only), it connects to `ws://127.0.0.1:9090`; open the web UI at `http://127.0.0.1:9090`; the headless/native angle (linuxX64 apps that can't show the in-app drawer can still stream to the monitor); the security note (localhost default; token for non-loopback).

- [ ] **Step 2: Build + test the module**

Run: `./gradlew :redux-kotlin-devtools-standalone:jvmTest :redux-kotlin-devtools-standalone:compileKotlinWasmJs --console=plain` → all pass.

- [ ] **Step 3: Confirm the desktop app assembles/runs headlessly enough to package**

Run: `./gradlew :redux-kotlin-devtools-standalone:compileKotlinJvm --console=plain` (and optionally `:packageDistributionForCurrentOS` if the host supports it — skip if it needs GUI/signing). Report what you ran.

- [ ] **Step 4: Whole-tree lint + clean tree**

Run: `./gradlew detektAll --console=plain` → SUCCESSFUL. `git status --short` → empty.

- [ ] **Step 5: Commit docs**

```bash
git add docs/devtools.md
git commit -m "docs(devtools): standalone monitor — run, connect, security"
```

---

## Self-Review (against the spec)

**Spec coverage (Plan C = rollout steps 4–6):**
- "embedded Ktor server → per-store InAppModel keyed clientId+storeInstanceId, grouped via StoreRegistryModel" → Tasks 2, 4. ✔
- "handshake enforcement + token for non-loopback; defensive decode" → Task 4. ✔
- "IDE-dock UI (rail with All/select/filter + badges, log merged-by-time, State+Diff, Pipeline, timeline scrub)" → Task 5 (reusing `-ui` tabs + `StoreRegistryModel`; structure from the `monitor/` kit). ✔
- "global search; time-travel read-only scrub; save/load (.jsonl desktop / Blob web, versioned header)" → Tasks 3, 5, 6. ✔
- "web variant: serve bundle + same-origin WS client; native app doubles as server + host" → Tasks 4, 6. ✔
- "native desktop = JVM Compose Desktop, packaged installer" → Task 1 (`compose.desktop.application`). ✔
- "docs + taskflow sample" → Tasks 8, 9. ✔
- read-only by construction (monitor never dispatches) → ingestion only calls `model.submit`; no path back to the app. ✔

**Placeholder scan:** the UI sub-steps in Task 5 and the web-serving in Task 6 are intentionally structural (the `monitor/` kit is the precise visual spec to translate; CMP/Ktor exact symbols are confirmed against the already-compiling `-inapp`/`-remote`/taskflow). The `Recording.{jvm,wasmJs}.kt` `actual`s carry concrete minimal bodies + a named seam for the file-dialog/Blob wiring — not TBDs. No "implement later" left as the sole content of a code step.

**Type consistency:** `MonitorIngest.{registry,openConnection}` + `Connection.{accept,close}`, `StoreRef("clientId::storeInstanceId", storeName)`, `StoreRegistryModel` (from Plan A), `BridgeMessage`/`bridgeJson`/`PROTOCOL_VERSION` (from Plan B), `encodeRecording`/`decodeRecording`/`RecordingHeader`, `saveRecording`/`loadRecording`, `MonitorServer(ingest,port,host,token).{start,stop}`, `MonitorApp(ingest,state)`, `rememberMonitorState(ingest)` used consistently across Tasks 2–8.
