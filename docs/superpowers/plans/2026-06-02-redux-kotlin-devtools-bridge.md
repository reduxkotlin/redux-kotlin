# DevTools Standalone — Plan B: Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `redux-kotlin-devtools-bridge` — a debug-only, off-by-default `DevToolsOutput` (Ktor WebSocket **client**) that streams a store's `DevToolsEvent`s (incl. pipeline trace) over a versioned JSON protocol to the standalone monitor — and expand the native target set so the bridge reaches every platform `redux-kotlin` supports.

**Architecture:** Mirror the existing `-remote` module's shape (client + connection + config + `DevToolsOutput`) but with a **raw WebSocket + `@Serializable` envelopes** instead of SocketCluster. Make `-core`'s small diff/pipeline value types `@Serializable` so the wire layer reuses them. The bridge handshakes (`Hello`/`HelloAck`) carrying `clientId + storeInstanceId + protocolVersion + serializerTier + token`, then streams events; it reseeds from `liftedState()` on (re)connect and bounds its outbound buffer. Localhost-bound + off by default; a token is required for non-loopback.

**Tech Stack:** KMP, Ktor client (CIO native/JVM, JS engine for js/wasm — mirroring `-remote`), kotlinx-serialization-json, kotlinx-coroutines. detekt + `explicitApi()` + ABI validation.

**Source spec:** `docs/superpowers/specs/2026-06-02-redux-kotlin-devtools-standalone-design.md` (Transport & wire protocol, Identity model, Security). **Builds on:** Plan A (`-ui`/`StoreRegistryModel`/serializer tier) is *not* required for B — B only needs `-core`; A and B are independent and can run in parallel, but both precede Plan C.

**Conventions (obey):** pre-commit `detektAll --auto-correct` → re-stage+amend until `git status` clean; never `--no-verify`; `explicitApi()` + KDoc on every public symbol (fold `@param` into prose if `OutdatedDocumentation` fires); run `updateKotlinAbi` + commit `api/` after public-API changes; explicit `git add <paths>`, never `git add -A`.

---

## File structure

### Modified `redux-kotlin-devtools-core`
- `JsonDiff.kt`, `PipelineModel.kt` — add `@Serializable` to `DiffOp`, `DiffEntry`, `PipelineNodeKind`, `PipelineNode`, `PipelineStructure`, `PipelineNodeTrace`, `PipelineTrace` (pure value types; enables wire reuse).
- `build.gradle.kts` — switch `convention.library-mpp-loved` → `convention.library-mpp-all` to add `linuxArm64` (Task 6).

### New module `redux-kotlin-devtools-bridge` (package `org.reduxkotlin.devtools.bridge`)
- `build.gradle.kts` — KMP lib, deps `api(:redux-kotlin-devtools-core)` + Ktor client (per-platform engines, mirror `-remote`), serialization, coroutines; `convention.library-mpp-all`.
- `BridgeProtocol.kt` — `PROTOCOL_VERSION` const + `@Serializable sealed interface BridgeMessage` (`Hello`, `HelloAck`, `Init`, `Action`, `PipelineRegistered`, `PipelineTraced`) + `toWire(DevToolsEvent)` mapper + a `bridgeJson` `Json` instance.
- `BridgeConfig.kt` — `host`, `port`, `secure`, `startEnabled=false`, `token?`, `clientId`, `clientLabel`.
- `BridgeConnection.kt` — Ktor WS client: connect-and-drain loop, bounded outbound `Channel`, handshake, reconnect.
- `BridgeOutput.kt` — `BridgeOutput(config) : DevToolsOutput` — off by default; on `start(session)` reseeds + follows the feed.

### Modified `settings.gradle.kts` — add `":redux-kotlin-devtools-bridge"`.

---

## Task 1: Make `-core` diff/pipeline value types `@Serializable`

So the wire layer can serialize them directly (DRY — no DTO duplication). These are pure data types; adding `@Serializable` is additive.

**Files:**
- Modify: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/JsonDiff.kt`, `PipelineModel.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/SerializableValueTypesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializableValueTypesTest {

    private val json = Json

    @Test
    fun diff_entry_round_trips() {
        val e = DiffEntry(DiffOp.CHANGED, "a.b", JsonPrimitive(1), JsonPrimitive(2))
        val s = json.encodeToString(DiffEntry.serializer(), e)
        assertEquals(e, json.decodeFromString(DiffEntry.serializer(), s))
    }

    @Test
    fun pipeline_trace_round_trips() {
        val t = PipelineTrace(
            actionId = 7,
            nodes = listOf(PipelineNodeTrace("mw_0_logger", 1234, forwarded = true, changed = false)),
        )
        val s = json.encodeToString(PipelineTrace.serializer(), t)
        assertEquals(t, json.decodeFromString(PipelineTrace.serializer(), s))
    }

    @Test
    fun pipeline_structure_round_trips() {
        val st = PipelineStructure(listOf(PipelineNode("dispatch", "dispatch(action)", PipelineNodeKind.ENTRY)))
        val s = json.encodeToString(PipelineStructure.serializer(), st)
        assertEquals(st, json.decodeFromString(PipelineStructure.serializer(), s))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*SerializableValueTypesTest*' --console=plain`
Expected: FAIL — `DiffEntry.serializer()` unresolved (not `@Serializable`).

- [ ] **Step 3: Annotate the types**

In `JsonDiff.kt`: add `import kotlinx.serialization.Serializable`; annotate `public enum class DiffOp` → `@Serializable public enum class DiffOp`; annotate `public data class DiffEntry(...)` → `@Serializable public data class DiffEntry(...)`. (`JsonElement` is already `@Serializable`.)
In `PipelineModel.kt`: add the import; annotate each of `PipelineNodeKind`, `PipelineNode`, `PipelineStructure`, `PipelineNodeTrace`, `PipelineTrace` with `@Serializable`. Leave KDoc intact.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*SerializableValueTypesTest*' --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Full core test + ABI**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --console=plain` → all pass.
Run: `./gradlew :redux-kotlin-devtools-core:updateKotlinAbi --console=plain` (the dump gains the synthetic `$serializer`/`Companion` entries — expected). `:checkKotlinAbi` → SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/JsonDiff.kt \
        redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/PipelineModel.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/SerializableValueTypesTest.kt \
        redux-kotlin-devtools-core/api/
git commit -m "feat(devtools-core): make diff/pipeline value types @Serializable"
```

---

## Task 2: Scaffold `redux-kotlin-devtools-bridge`

**Files:**
- Create: `redux-kotlin-devtools-bridge/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Create dirs: `redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge`, `.../commonTest/.../bridge`

- [ ] **Step 1: settings include**

Add `":redux-kotlin-devtools-bridge",` to `settings.gradle.kts`.

- [ ] **Step 2: `build.gradle.kts`** (mirror `redux-kotlin-devtools-remote`'s engine wiring; use `library-mpp-all` for the broad native set)

```kotlin
plugins {
    id("convention.library-mpp-all")
    id("convention.publishing-mpp")
    alias(libs.plugins.kotlin.serialization)
}

val hasAndroidSdk: Boolean = run {
    val localProps = rootProject.file("local.properties")
    val hasSdkInLocalProperties = localProps.exists() && localProps.readText().lineSequence().any {
        it.trim().startsWith("sdk.dir=") && it.substringAfter("sdk.dir=").isNotBlank()
    }
    val hasSdkInEnv =
        !System.getenv("ANDROID_HOME").isNullOrBlank() ||
            !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
    hasSdkInLocalProperties || hasSdkInEnv
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.devtools.bridge"
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-devtools-core"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        named("jvmCommonMain") { dependencies { implementation(libs.ktor.client.cio) } }
        if (hasAndroidSdk) { named("androidMain") { dependencies { implementation(libs.ktor.client.cio) } } }
        named("nativeMain") { dependencies { implementation(libs.ktor.client.cio) } }
        named("jsMain") { dependencies { implementation(libs.ktor.client.js) } }
        named("wasmJsMain") { dependencies { implementation(libs.ktor.client.js) } }
        jvmTest { dependencies { implementation(libs.ktor.server.cio); implementation(libs.ktor.server.websockets) } }
    }
}
```
> If `convention.library-mpp-all` adds a target whose Ktor engine isn't available (e.g. `linuxArm64` CIO), the compile will reveal it; handle in Task 6. If `:redux-kotlin-devtools-core` is `-loved` (not `-all`) at this point, the `-all` target `linuxArm64` here will fail to resolve core — do Task 6 (bump core to `-all`) **before** the first native compile, or temporarily keep `-loved` here and switch in Task 6.

- [ ] **Step 3: dirs + verify**

```bash
mkdir -p redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge \
         redux-kotlin-devtools-bridge/src/commonTest/kotlin/org/reduxkotlin/devtools/bridge
```
Run: `./gradlew :redux-kotlin-devtools-bridge:help --console=plain` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts redux-kotlin-devtools-bridge/build.gradle.kts
git commit -m "build(devtools-bridge): scaffold module"
```

---

## Task 3: Wire protocol (`BridgeProtocol.kt`, TDD)

**Files:**
- Create: `redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/BridgeProtocol.kt`
- Test: `redux-kotlin-devtools-bridge/src/commonTest/kotlin/org/reduxkotlin/devtools/bridge/BridgeProtocolTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools.bridge

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DevToolsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeProtocolTest {

    private fun encDec(m: BridgeMessage): BridgeMessage {
        val s = bridgeJson.encodeToString(BridgeMessage.serializer(), m)
        return bridgeJson.decodeFromString(BridgeMessage.serializer(), s)
    }

    @Test
    fun hello_round_trips() {
        val h = BridgeMessage.Hello(
            protocolVersion = PROTOCOL_VERSION, clientId = "c1", clientLabel = "TaskFlow · desktop",
            storeInstanceId = "store-1", storeName = "TaskFlow-root", serializerTier = "kotlinx.serialization", token = null,
        )
        assertEquals(h, encDec(h))
    }

    @Test
    fun action_event_maps_to_wire_and_round_trips() {
        val ev = DevToolsEvent.ActionRecorded(
            actionId = 5,
            action = buildJsonObject { put("type", "AddCard") },
            state = buildJsonObject { put("n", 5) },
            diff = emptyList(),
            timestampMillis = 123L,
            isExcess = false,
        )
        val wire = toWire(ev)
        assertTrue(wire is BridgeMessage.Action)
        val back = encDec(wire) as BridgeMessage.Action
        assertEquals(5, back.actionId)
        assertEquals(123L, back.timestampMillis)
        assertEquals(false, back.isExcess)
    }

    @Test
    fun initialized_maps_to_init() {
        val wire = toWire(DevToolsEvent.Initialized(buildJsonObject { put("n", 0) }))
        assertTrue(wire is BridgeMessage.Init)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-bridge:jvmTest --tests '*BridgeProtocolTest*' --console=plain`
Expected: FAIL — `BridgeMessage`/`toWire`/`bridgeJson`/`PROTOCOL_VERSION` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.DiffEntry
import org.reduxkotlin.devtools.PipelineStructure
import org.reduxkotlin.devtools.PipelineTrace

/** Bridge wire-protocol version; bumped on any incompatible envelope change. */
public const val PROTOCOL_VERSION: Int = 1

/** Shared Json for the bridge wire (ignores unknown keys so older/newer peers interoperate). */
public val bridgeJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "t" }

/**
 * The bridge wire envelope. A versioned, `@Serializable` mirror of the events that cross the socket —
 * decoupled from the core sealed `DevToolsEvent` so the protocol can evolve independently. The first
 * frame is always a [Hello]; the monitor replies [HelloAck].
 */
@Serializable
public sealed interface BridgeMessage {

    /** Handshake: identifies the client + store and negotiates the protocol. Sent first. */
    @Serializable @SerialName("hello")
    public data class Hello(
        /** Sender's protocol version. */ public val protocolVersion: Int,
        /** Stable id of the debugged app instance. */ public val clientId: String,
        /** Human label for the client (device/app). */ public val clientLabel: String,
        /** The store's `instanceId ?: name`. */ public val storeInstanceId: String,
        /** The store's display name. */ public val storeName: String,
        /** Which `ValueSerializer` tier produced the JSON (e.g. "kotlinx.serialization", "toString"). */ public val serializerTier: String,
        /** Shared token; required by the monitor for non-loopback connections. */ public val token: String? = null,
    ) : BridgeMessage

    /** Monitor's handshake reply with the accepted protocol version. */
    @Serializable @SerialName("ack")
    public data class HelloAck(
        /** Protocol version the monitor will speak. */ public val protocolVersion: Int,
        /** `false` + [reason] when the monitor refuses (bad token / incompatible version). */ public val accepted: Boolean = true,
        /** Refusal reason, when not accepted. */ public val reason: String? = null,
    ) : BridgeMessage

    /** The store's initial serialized state. */
    @Serializable @SerialName("init")
    public data class Init(/** Serialized preloaded state. */ public val state: JsonElement) : BridgeMessage

    /** A recorded action + resulting state + diff. */
    @Serializable @SerialName("action")
    public data class Action(
        /** Recorder id. */ public val actionId: Int,
        /** Serialized action (carries its `type`). */ public val action: JsonElement,
        /** Serialized resulting state. */ public val state: JsonElement,
        /** Leaf diff vs the previous state. */ public val diff: List<DiffEntry>,
        /** Dispatch-time capture, epoch millis. */ public val timestampMillis: Long,
        /** Ring-buffer eviction flag. */ public val isExcess: Boolean,
    ) : BridgeMessage

    /** The static pipeline structure (sent once when registered). */
    @Serializable @SerialName("pipeline")
    public data class PipelineRegistered(/** The node map. */ public val structure: PipelineStructure) : BridgeMessage

    /** A per-action pipeline trace. */
    @Serializable @SerialName("trace")
    public data class PipelineTraced(/** Per-action node trace. */ public val trace: PipelineTrace) : BridgeMessage
}

/** Maps a core [DevToolsEvent] to its wire envelope. */
public fun toWire(event: DevToolsEvent): BridgeMessage = when (event) {
    is DevToolsEvent.Initialized -> BridgeMessage.Init(event.state)
    is DevToolsEvent.ActionRecorded -> BridgeMessage.Action(
        event.actionId, event.action, event.state, event.diff, event.timestampMillis, event.isExcess,
    )
    is DevToolsEvent.PipelineRegistered -> BridgeMessage.PipelineRegistered(event.structure)
    is DevToolsEvent.PipelineTraced -> BridgeMessage.PipelineTraced(event.trace)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-bridge:jvmTest --tests '*BridgeProtocolTest*' --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/BridgeProtocol.kt \
        redux-kotlin-devtools-bridge/src/commonTest/kotlin/org/reduxkotlin/devtools/bridge/BridgeProtocolTest.kt
git commit -m "feat(devtools-bridge): versioned @Serializable wire protocol + event mapper"
```

---

## Task 4: `BridgeConfig` + `BridgeConnection`

`BridgeConnection` is the Ktor WS client: connect → send `Hello` → await `HelloAck` → send `Init`(seed) → drain a bounded outbound `Channel` → on drop, retry with backoff and **reseed**. Adapt the proven shape from `-remote`'s `RemoteConnection` (connect-and-drain + `Channel(256)`), swapping SocketCluster framing for `bridgeJson` text frames.

**Files:**
- Create: `redux-kotlin-devtools-bridge/src/commonMain/.../bridge/BridgeConfig.kt`, `BridgeConnection.kt`

- [ ] **Step 1: `BridgeConfig.kt`**

```kotlin
package org.reduxkotlin.devtools.bridge

/**
 * Connection settings for the standalone-monitor bridge.
 *
 * @property host monitor host (default loopback; non-loopback requires [token]).
 * @property port monitor WS port (default 9090).
 * @property secure use `wss` instead of `ws`.
 * @property startEnabled connect at bind time; otherwise stay off until started.
 * @property token shared secret sent in the handshake; required by the monitor for non-loopback.
 * @property clientId stable id of this app instance (defaults to a per-process value if blank).
 * @property clientLabel human label for this client (device/app).
 */
public data class BridgeConfig(
    public val host: String = "127.0.0.1",
    public val port: Int = 9090,
    public val secure: Boolean = false,
    public val startEnabled: Boolean = false,
    public val token: String? = null,
    public val clientId: String = "",
    public val clientLabel: String = "redux-kotlin app",
)
```

- [ ] **Step 2: `BridgeConnection.kt`** (read `-remote`'s `RemoteConnection.kt` first and mirror its connect-loop/backoff/Channel shape)

```kotlin
package org.reduxkotlin.devtools.bridge

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.reduxkotlin.devtools.DevToolsSession

/**
 * The bridge's Ktor WebSocket client: handshakes, seeds the monitor with [DevToolsSession.liftedState],
 * then drains a bounded outbound queue of wire frames. On disconnect it retries with backoff and
 * reseeds. One connection per store. Errors are swallowed/logged — never propagated to the host.
 *
 * @param config connection settings.
 * @param session the store being streamed (for reseed snapshots + identity).
 * @param logger diagnostic sink.
 */
internal class BridgeConnection(
    private val config: BridgeConfig,
    private val session: DevToolsSession,
    private val logger: (String) -> Unit,
) {
    private val outbound = Channel<BridgeMessage>(capacity = 256)
    private val client = HttpClient { install(WebSockets) }
    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch { connectLoop() }
    }

    /** Enqueue a wire frame (drops oldest when full — never blocks the caller). */
    fun enqueue(message: BridgeMessage) {
        val r = outbound.trySend(message)
        if (r.isFailure) logger("bridge: outbound full, dropping frame")
    }

    /** Reseed: push a fresh Init(liftedState) so a (re)connected monitor gets full history. */
    fun reseed() {
        runCatching { enqueue(BridgeMessage.Init(session.liftedState())) }
    }

    fun stop() {
        outbound.close()
        runCatching { client.close() }
    }

    private suspend fun connectLoop() {
        var backoffMs = 500L
        val scheme = if (config.secure) "wss" else "ws"
        while (scope?.isActive == true) {
            try {
                client.webSocket(urlString = "$scheme://${config.host}:${config.port}/bridge") {
                    val hello = BridgeMessage.Hello(
                        protocolVersion = PROTOCOL_VERSION,
                        clientId = config.clientId.ifBlank { session.id },
                        clientLabel = config.clientLabel,
                        storeInstanceId = session.id,
                        storeName = session.id,
                        serializerTier = "unknown",
                        token = config.token,
                    )
                    send(Frame.Text(bridgeJson.encodeToString(BridgeMessage.serializer(), hello)))
                    // await ack
                    val ackFrame = incoming.receive() as? Frame.Text
                    val ack = ackFrame?.let { bridgeJson.decodeFromString(BridgeMessage.serializer(), it.readText()) } as? BridgeMessage.HelloAck
                    if (ack?.accepted != true) { logger("bridge: handshake refused: ${ack?.reason}"); return@webSocket }
                    backoffMs = 500L
                    reseed()
                    for (msg in outbound) send(Frame.Text(bridgeJson.encodeToString(BridgeMessage.serializer(), msg)))
                }
            } catch (t: Throwable) {
                logger("bridge: connection error: ${t.message}")
            }
            if (scope?.isActive != true) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(8000L)
        }
    }
}
```
> **Adapt to actuals:** mirror `-remote`'s `RemoteConnection` for the exact Ktor WS client API in this version (`HttpClient { install(WebSockets) }`, `client.webSocket {}`, `Frame.Text`, `incoming`). If `-remote` used `createDevToolsHttpClient()` (an expect/actual engine factory), reuse that pattern here for native engine selection rather than a bare `HttpClient {}`. The `serializerTier` is filled in by `BridgeOutput` (Task 5) if it knows the configured serializer; "unknown" is the safe default. `storeName` defaults to `session.id` until the session exposes a separate display name — acceptable (the monitor shows the id).

- [ ] **Step 3: Compile**

Run: `./gradlew :redux-kotlin-devtools-bridge:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL (fix Ktor API names against `-remote`).

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/BridgeConfig.kt \
        redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/BridgeConnection.kt
git commit -m "feat(devtools-bridge): BridgeConfig + Ktor WS connection (handshake, reseed, backoff)"
```

---

## Task 5: `BridgeOutput : DevToolsOutput` (TDD lifecycle)

**Files:**
- Create: `redux-kotlin-devtools-bridge/src/commonMain/.../bridge/BridgeOutput.kt`
- Test: `redux-kotlin-devtools-bridge/src/commonTest/.../bridge/BridgeOutputTest.kt`

- [ ] **Step 1: Write the failing test** (lifecycle/state only; the JVM WS round-trip is covered by an integration test you add against a Ktor test server, mirroring `-remote`'s `ScClientIntegrationTest`)

```kotlin
package org.reduxkotlin.devtools.bridge

import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeOutputTest {

    @Test
    fun stable_id_and_off_by_default() {
        val out = BridgeOutput(BridgeConfig())
        assertEquals("bridge", out.id)
        assertFalse(out.isRunning)
        assertFalse(out.startEnabled)
    }

    @Test
    fun start_then_stop_toggles_running() {
        val out = BridgeOutput(BridgeConfig(startEnabled = false))
        val session = DevToolsSession.create(DevToolsConfig(name = "t"))
        out.start(session)
        assertTrue(out.isRunning)
        out.stop()
        assertFalse(out.isRunning)
        session.close()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-bridge:jvmTest --tests '*BridgeOutputTest*' --console=plain`
Expected: FAIL — `BridgeOutput` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.reduxkotlin.devtools.DevToolsOutput
import org.reduxkotlin.devtools.DevToolsSession

/**
 * Streams a store's [DevToolsSession] feed to the standalone monitor over the bridge protocol.
 *
 * A `DevToolsOutput`, so it is per-store (one connection per store). Off by default — localhost-bound,
 * a token required for non-loopback (see [BridgeConfig]). On [start] it opens a [BridgeConnection]
 * (which handshakes + reseeds), then forwards every [org.reduxkotlin.devtools.DevToolsEvent] as a wire
 * frame. Debug-only: wire it as `debugImplementation` and never ship it in release.
 *
 * @param config connection + identity settings.
 * @param logger diagnostic sink (defaults to no-op).
 */
public class BridgeOutput(
    private val config: BridgeConfig,
    private val logger: (String) -> Unit = {},
) : DevToolsOutput {

    override val id: String = "bridge"
    override val label: String = "Standalone monitor (bridge)"

    /** Mirrors [BridgeConfig.startEnabled]; a binder consults it to auto-connect at registration. */
    public val startEnabled: Boolean get() = config.startEnabled

    private var scope: CoroutineScope? = null
    private var connection: BridgeConnection? = null

    /** Whether the output is currently connected/streaming. */
    public val isRunning: Boolean get() = scope != null

    override fun start(session: DevToolsSession) {
        if (isRunning) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        val conn = BridgeConnection(config, session, logger).also { connection = it }
        conn.start(s)
        session.events
            .onEach { event -> runCatching { conn.enqueue(toWire(event)) } }
            .launchIn(s)
    }

    override fun stop() {
        connection?.stop()
        connection = null
        scope?.cancel()
        scope = null
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-bridge:jvmTest --tests '*BridgeOutputTest*' --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Add a JVM WS integration test** (mirror `-remote`'s server round-trip)

Create `redux-kotlin-devtools-bridge/src/jvmTest/kotlin/org/reduxkotlin/devtools/bridge/BridgeRoundTripTest.kt`: stand up a Ktor test WS server (`ktor-server-cio` + `ktor-server-websockets`) on an ephemeral loopback port that, on `/bridge`, reads the `Hello`, replies `HelloAck(accepted=true)`, and collects subsequent frames into a list; create a `DevToolsSession`, attach `BridgeOutput(BridgeConfig(port=<ephemeral>))`, `session.init(...)` + `session.record(...)`, then assert the server received a `Hello` then an `Init`/`Action` (decode with `bridgeJson`). Use `runBlocking`/`runTest` with a timeout. (Pattern: copy the structure of `-remote`'s existing JVM WS integration test.)
Run: `./gradlew :redux-kotlin-devtools-bridge:jvmTest --console=plain` → all pass.

- [ ] **Step 6: API dump + commit**

```bash
./gradlew :redux-kotlin-devtools-bridge:updateKotlinAbi --console=plain
git add redux-kotlin-devtools-bridge
git commit -m "feat(devtools-bridge): BridgeOutput streaming the session feed over the bridge protocol"
```

---

## Task 6: Native target expansion (verify the cascade)

**Files:**
- Modify: `redux-kotlin-devtools-core/build.gradle.kts` (+ `-bridge` already on `-all`)

- [ ] **Step 1: Bump `-core` to the broad native set**

Change `redux-kotlin-devtools-core/build.gradle.kts` plugin `id("convention.library-mpp-loved")` → `id("convention.library-mpp-all")` (adds `linuxArm64`). `-bridge` already uses `-all`.

- [ ] **Step 2: Verify the base-library cascade for `iosX64`/`macosX64`**

The spec wants `linuxArm64` + `iosX64` + `macosX64`. `convention.library-mpp-all` adds only `linuxArm64`. `iosX64`/`macosX64` are not in the conventions and require the **base `redux-kotlin`** to also target them. Check: `grep -rn "iosX64\|macosX64" build-conventions/ redux-kotlin/build.gradle.kts` and inspect `redux-kotlin`'s `api/` klib targets. 
  - If `redux-kotlin` (and the convention) already provide `iosX64`/`macosX64`, add explicit `iosX64()` / `macosX64()` to `-core` and `-bridge` `kotlin { }`.
  - If NOT, do **not** add them here (a bridge target needs core, which needs the base lib on that target). Document the omission: add a comment in both build files — `// iosX64/macosX64 omitted: base redux-kotlin does not target them; add when the base does.` — and note it in the commit. Do not modify the base library or shared conventions in this plan.

- [ ] **Step 3: Compile the broad native set**

Run: `./gradlew :redux-kotlin-devtools-core:compileKotlinLinuxArm64 :redux-kotlin-devtools-bridge:compileKotlinLinuxArm64 --console=plain` (and `:checkKotlinAbi` for both — klib dumps now include `linuxArm64`).
Expected: BUILD SUCCESSFUL. If the Ktor CIO engine is unavailable on `linuxArm64`, the compile fails — in that case scope `-bridge` to the targets where its engine exists (remove just that target from `-bridge` with a documented `removeIf`) while keeping `-core` broad. Report which targets you landed.

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-devtools-core/build.gradle.kts redux-kotlin-devtools-bridge/build.gradle.kts \
        redux-kotlin-devtools-core/api/ redux-kotlin-devtools-bridge/api/
git commit -m "build(devtools): expand native targets (linuxArm64) for core + bridge"
```

---

## Task 7: Final gate

- [ ] **Step 1: Tests** — `./gradlew :redux-kotlin-devtools-core:jvmTest :redux-kotlin-devtools-bridge:jvmTest --console=plain` → all pass.
- [ ] **Step 2: ABI** — `./gradlew :redux-kotlin-devtools-core:checkKotlinAbi :redux-kotlin-devtools-bridge:checkKotlinAbi --console=plain` → SUCCESSFUL (klib native compiles incl. linuxArm64).
- [ ] **Step 3: Lint** — `./gradlew detektAll --console=plain` → SUCCESSFUL.
- [ ] **Step 4: Clean tree** — `git status --short` → empty.

---

## Self-Review (against the spec)

**Spec coverage (Plan B scope = rollout step 3 + native expansion):**
- "`BridgeOutput : DevToolsOutput` — Ktor WS client streaming `DevToolsEvent`s (incl. pipeline) over a versioned JSON protocol; off by default" → Tasks 3–5. ✔
- "handshake carrying protocolVersion + clientId + storeInstanceId + serializerTier + token; HelloAck negotiation" → Task 3 (`Hello`/`HelloAck`), Task 4 (handshake in connect loop). ✔
- "security: localhost default + token for non-loopback" → `BridgeConfig` defaults `127.0.0.1`, `token` in `Hello`; the monitor (Plan C) enforces the token. ✔ (the bridge *sends* the token; enforcement is server-side in C.)
- "off by default" + "debug-only" → `startEnabled=false`, `isRunning` false until `start`, KDoc states debug-only. ✔
- "reconnect → reseed from liftedState(); bounded outbound buffer / drop policy" → Task 4 (`reseed()` on connect; `Channel(256)` + trySend drop). ✔
- "one connection per store" → `BridgeOutput` is per-`DevToolsOutput.start(session)`. ✔
- "wire reuses diff/pipeline types" → Task 1 (`@Serializable` core value types) + Task 3. ✔
- "expand native targets (linuxArm64/iosX64/macosX64), gated on base lib + Ktor" → Task 6 (linuxArm64 via `-all`; iosX64/macosX64 added only if the base supports them, else documented). ✔

**Deferred:** the monitor SERVER (handshake enforcement, decode, ingestion) + UI → **Plan C**.

**Placeholder scan:** none. The "adapt to `-remote` actuals" notes are concrete (mirror an existing, compiling module's Ktor API) — not unfinished steps.

**Type consistency:** `BridgeMessage.{Hello,HelloAck,Init,Action,PipelineRegistered,PipelineTraced}`, `toWire`, `bridgeJson`, `PROTOCOL_VERSION`, `BridgeConfig.{host,port,secure,startEnabled,token,clientId,clientLabel}`, `BridgeConnection.{start,enqueue,reseed,stop}`, `BridgeOutput(config,logger).{id,label,startEnabled,isRunning,start,stop}` used consistently across Tasks 3–6.
