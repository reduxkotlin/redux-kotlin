# redux-kotlin DevTools Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the headless, transport-agnostic DevTools engine — a `redux-kotlin-devtools-core` module (one `devTools()` enhancer recording into a process-global hub that publishes a `Flow`) plus a reshaped `redux-kotlin-devtools-remote` module (the existing WebSocket transport, now a toggleable `DevToolsOutput`).

**Architecture:** The single `devTools()` enhancer owns no transport. On every dispatch it captures the action + the (immutable) state reference cheaply on the dispatch thread, then a per-session background coroutine serializes, diffs, records lifted state, and emits a `DevToolsEvent` on a `SharedFlow`. `DevToolsOutput`s subscribe to that flow. The in-process hub keys sessions by `instanceId` (multi-store falls out for free). The WebSocket sink becomes a `RemoteOutput : DevToolsOutput` that is **off by default** and self-registers when on the classpath.

**Tech Stack:** Kotlin Multiplatform (`convention.library-mpp-loved`), kotlinx-coroutines, kotlinx-serialization-json, kotlinx-atomicfu (hub locking, mirrors `redux-kotlin-registry`), Ktor client (remote only). detekt 2.0.0-alpha.3 with `explicitApi()`; public-API ABI validation via `apiDump`/`apiCheck`.

**Scope note:** This plan delivers the engine only. Pipeline combinators (`devToolsMiddleware`/`devToolsCombineReducers`/`PipelineModel`) are **Plan 2**; the Compose `-inapp` UI + `-inapp-noop` are **Plan 3**. `DevToolsEvent` is sealed so Plan 2 can add pipeline events without breaking subscribers. Source spec: `docs/superpowers/specs/2026-06-01-redux-kotlin-inapp-devtools-design.md`.

**Hard preconditions & invariants (read before coding):**
- **State & actions must be deeply immutable.** This engine captures the state reference on the dispatch thread and serializes it *later* on a background coroutine. redux-kotlin does **not** enforce immutability — a reducer that mutates state in place would let the background thread serialize a torn/newer snapshot and produce a wrong diff. The synchronous predecessor was immune; we trade that for never blocking dispatch. Document this in the module README and KDoc; recommend immutable `data class` state.
- **Recorder is single-threaded by design.** `LiftedStateRecorder` mutates an `ArrayDeque` with no locking and publishes a `@Volatile snapshot`. All `init`/`record` calls MUST happen on the one session consumer coroutine (never concurrently). `liftedState()` reads the volatile snapshot and is safe from any thread **after** the first `init` (its `?: buildSnapshot()` fallback touches `staged`, so never call it before init).
- **Composition order.** Compose as `compose(devTools(config), applyMiddleware(...))` so async middleware (thunk) is innermost and devTools records the *resolved* plain actions its inner re-dispatches produce. `applyMiddleware`'s own KDoc says it "should be the first store enhancer in the composition chain" (innermost). Recording happens *after* `base(action)` returns, i.e. post-reducer state. The Task 7 test pins behavior, not a specific order — keep it that way.

**Conventions to obey (repo CLAUDE.md):**
- Every `public` declaration needs an explicit `public` modifier **and** a KDoc comment (detekt `UndocumentedPublic*`, `warningsAsErrors: true`). Document as you write — KDoc does not auto-correct.
- After any public-API change run `./gradlew apiDump` and commit the `*.api` files. `apiCheck` runs in `build`.
- Pre-commit hook = `detektAll --auto-correct` (may rewrite formatting — re-stage and commit again). **Never** `--no-verify`.
- New module mirrors `redux-kotlin-registry`: `build.gradle.kts` applies `convention.library-mpp-loved` + `convention.publishing-mpp`, `commonMain` deps `api(project(":redux-kotlin"))`, package `org.reduxkotlin.<feature>`.
- Tests use `kotlin.test.*`; default home is `commonTest`. JVM-only concurrency stress goes in `jvmTest`.

---

## File Structure

### New module: `redux-kotlin-devtools-core` (package `org.reduxkotlin.devtools`)
- `build.gradle.kts` — KMP library, no Ktor, no Compose.
- `commonMain/.../Clock.kt` — `EpochMillis` (moved verbatim from `redux-kotlin-devtools`).
- `commonMain/.../Serialization.kt` — `ValueSerializer`, `ToStringValueSerializer`, `expect platformDefaultSerializer()` (moved).
- `<platform>Main/.../Serialization.<platform>.kt` — the existing platform actuals (moved).
- `commonMain/.../LiftedStateRecorder.kt` — `LiftedStateRecorder`, `RecordedAction` (moved verbatim).
- `commonMain/.../DevToolsConfig.kt` — reshaped: drops `host`/`port`/`secure` (those move to `RemoteConfig`).
- `commonMain/.../JsonDiff.kt` — **new**: `DiffOp`, `DiffEntry`, `diffJson(before, after)`.
- `commonMain/.../DevToolsEvent.kt` — **new**: sealed `DevToolsEvent` (`Initialized`, `ActionRecorded`).
- `commonMain/.../DevToolsOutput.kt` — **new**: `interface DevToolsOutput` subscriber contract.
- `commonMain/.../DevToolsSession.kt` — **reshaped (no Ktor)**: recorder + capture channel + background serialize/diff + `SharedFlow<DevToolsEvent>` + `liftedState()`.
- `commonMain/.../DevToolsHub.kt` — **new**: process-global, atomicfu-locked registry of sessions + outputs.
- `commonMain/.../DevTools.kt` — **reshaped**: transport-agnostic `devTools()` enhancer (records + publishes only).

### New module: `redux-kotlin-devtools-remote` (package `org.reduxkotlin.devtools.remote`)
- `build.gradle.kts` — KMP library, deps `api(project(":redux-kotlin-devtools-core"))` + Ktor.
- `commonMain/.../RemoteConfig.kt` — **new**: `host`, `port`, `secure`, `startEnabled = false`.
- `commonMain/.../RemoteOutput.kt` — **new**: `DevToolsOutput` that streams the session flow over WS.
- `commonMain/.../RemoteConnection.kt` — the old Ktor `DevToolsSession` renamed/relocated (connection loop only).
- `commonMain/.../socketcluster/ScClient.kt`, `ScFrame.kt` — moved verbatim.
- `commonMain/.../wire/Messages.kt` — moved verbatim.
- `commonMain/.../WebSocketEngine.kt` + `<platform>Main` actuals — moved verbatim.

### Modified
- `settings.gradle.kts` — replace `":redux-kotlin-devtools"` with the two new module includes.
- `redux-kotlin-bom/build.gradle.kts` — if it enumerates modules, swap the devtools entry (verify in Task 11).
- Delete the old `redux-kotlin-devtools/` module directory.

---

## Task 1: Scaffold `redux-kotlin-devtools-core` module

**Files:**
- Create: `redux-kotlin-devtools-core/build.gradle.kts`
- Modify: `settings.gradle.kts` (the `include(...)` block)
- Create (dirs): `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/`, `.../src/commonTest/kotlin/org/reduxkotlin/devtools/`

- [ ] **Step 1: Add the module to `settings.gradle.kts`**

In the `include(...)` list, replace the line `":redux-kotlin-devtools",` with:

```kotlin
    ":redux-kotlin-devtools-core",
    ":redux-kotlin-devtools-remote",
```

(The `-remote` module is wired up in Task 9; declaring it now keeps one settings edit. It will not configure until its `build.gradle.kts` exists — do Task 9 before running a full build, or comment the `-remote` line until then. To stay green between tasks, add only `":redux-kotlin-devtools-core",` here and add `":redux-kotlin-devtools-remote",` in Task 9.)

- [ ] **Step 2: Create `redux-kotlin-devtools-core/build.gradle.kts`**

```kotlin
plugins {
    id("convention.library-mpp-loved")
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
            namespace = "org.reduxkotlin.devtools"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        named("jvmCommonMain") {
            dependencies {
                implementation(libs.kotlin.reflect)
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                dependencies {
                    implementation(libs.kotlin.reflect)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create the package directories**

Run:
```bash
mkdir -p redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools \
         redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools
```

- [ ] **Step 4: Verify Gradle sees the module**

Run: `./gradlew :redux-kotlin-devtools-core:tasks --console=plain | head -5`
Expected: task list prints, no configuration error. (Empty source sets are fine.)

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts redux-kotlin-devtools-core/build.gradle.kts
git commit -m "build(devtools): scaffold redux-kotlin-devtools-core module"
```

---

## Task 2: Move reusable, transport-free code into `-core`

These files have no Ktor coupling and move verbatim. Use `git mv` to preserve history. The package stays `org.reduxkotlin.devtools`, so no import edits are needed inside them.

**Files:**
- Move: `Clock.kt`, `Serialization.kt` (+ `jsMain`/`jvmCommonMain`/`nativeMain`/`wasmJsMain` actuals), `LiftedStateRecorder.kt`
- Modify: `DevToolsConfig.kt` (reshape, then move)

- [ ] **Step 1: Move the no-change files**

```bash
SRC=redux-kotlin-devtools/src
DST=redux-kotlin-devtools-core/src
git mv $SRC/commonMain/kotlin/org/reduxkotlin/devtools/Clock.kt              $DST/commonMain/kotlin/org/reduxkotlin/devtools/Clock.kt
git mv $SRC/commonMain/kotlin/org/reduxkotlin/devtools/Serialization.kt      $DST/commonMain/kotlin/org/reduxkotlin/devtools/Serialization.kt
git mv $SRC/commonMain/kotlin/org/reduxkotlin/devtools/LiftedStateRecorder.kt $DST/commonMain/kotlin/org/reduxkotlin/devtools/LiftedStateRecorder.kt
for P in jsMain jvmCommonMain nativeMain wasmJsMain; do
  mkdir -p "$DST/$P/kotlin/org/reduxkotlin/devtools"
  git mv "$SRC/$P/kotlin/org/reduxkotlin/devtools/Serialization."* "$DST/$P/kotlin/org/reduxkotlin/devtools/" 2>/dev/null || true
done
```

Then verify the platform actuals landed:
```bash
find redux-kotlin-devtools-core/src -name 'Serialization*.kt'
```
Expected: the common `Serialization.kt` plus one actual per platform source set that had one.

- [ ] **Step 2: Reshape `DevToolsConfig` and move it**

Create `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsConfig.kt` with the transport fields removed (`host`/`port`/`secure` move to `RemoteConfig` in Task 9):

```kotlin
package org.reduxkotlin.devtools

/**
 * Transport-agnostic configuration for the [devTools] enhancer and its [DevToolsSession].
 *
 * Connection settings (host/port/secure) are **not** here — they belong to a specific output
 * (see `RemoteConfig` in `redux-kotlin-devtools-remote`). This config only governs recording.
 *
 * @property name display name of this store instance; also the default [instanceId].
 * @property instanceId stable id used to key the session in [DevToolsHub]; defaults to [name].
 * @property maxAge maximum number of actions retained in the lifted-state ring buffer.
 * @property allowlist if non-empty, only actions whose name matches one of these regexes are recorded.
 * @property denylist actions whose name matches any of these regexes are never recorded.
 * @property serializer override for action/state serialization; defaults to the platform tier.
 * @property logger sink for diagnostic messages; instrumentation never throws into the host store.
 */
public data class DevToolsConfig(
    public val name: String = "redux-kotlin",
    public val instanceId: String? = null,
    public val maxAge: Int = 50,
    public val allowlist: List<String> = emptyList(),
    public val denylist: List<String> = emptyList(),
    public val serializer: ValueSerializer? = null,
    public val logger: (String) -> Unit = {},
)
```

Then remove the old file:
```bash
git rm redux-kotlin-devtools/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsConfig.kt
```

- [ ] **Step 3: Compile core**

Run: `./gradlew :redux-kotlin-devtools-core:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL. (`LiftedStateRecorder` + `Serialization` compile against `redux-kotlin` + serialization. If a moved file referenced a now-removed `DevToolsConfig` field, fix that reference — none of the three moved files should.)

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(devtools): move transport-free recorder/serialization/config into -core"
```

---

## Task 3: JSON diff (`JsonDiff.kt`)

Computes added/removed/changed leaf paths between two serialized states. Used by the session to attach a per-action diff to each event (the Diff tab in Plan 3 renders it).

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/JsonDiff.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/JsonDiffTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonDiffTest {

    @Test
    fun changed_leaf_is_reported_with_before_and_after() {
        val before = buildJsonObject { put("filter", "ALL"); put("count", 1) }
        val after = buildJsonObject { put("filter", "DONE"); put("count", 1) }

        val diff = diffJson(before, after)

        assertEquals(1, diff.size)
        val e = diff.single()
        assertEquals(DiffOp.CHANGED, e.op)
        assertEquals("filter", e.path)
        assertEquals(JsonPrimitive("ALL"), e.before)
        assertEquals(JsonPrimitive("DONE"), e.after)
    }

    @Test
    fun added_and_removed_keys_are_reported() {
        val before = buildJsonObject { put("a", 1) }
        val after = buildJsonObject { put("b", 2) }

        val ops = diffJson(before, after).associate { it.path to it.op }

        assertEquals(DiffOp.REMOVED, ops["a"])
        assertEquals(DiffOp.ADDED, ops["b"])
    }

    @Test
    fun nested_object_paths_are_dotted() {
        val before = buildJsonObject { put("user", buildJsonObject { put("name", "ann") }) }
        val after = buildJsonObject { put("user", buildJsonObject { put("name", "bob") }) }

        assertEquals("user.name", diffJson(before, after).single().path)
    }

    @Test
    fun identical_states_produce_no_diff() {
        val s = buildJsonObject { put("x", 1) }
        assertEquals(emptyList(), diffJson(s, s))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*JsonDiffTest*' --console=plain`
Expected: FAIL — `diffJson` / `DiffOp` / `DiffEntry` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The kind of change a [DiffEntry] represents between two states. */
public enum class DiffOp {
    /** A path present in the new state but absent in the previous one. */
    ADDED,

    /** A path present in the previous state but absent in the new one. */
    REMOVED,

    /** A path present in both states whose leaf value differs. */
    CHANGED,
}

/**
 * A single difference between two serialized states at a dotted [path].
 *
 * @property op the kind of change.
 * @property path dotted path to the leaf (object keys and array indices joined by `.`).
 * @property before the previous value, or `null` for [DiffOp.ADDED].
 * @property after the new value, or `null` for [DiffOp.REMOVED].
 */
public data class DiffEntry(
    public val op: DiffOp,
    public val path: String,
    public val before: JsonElement?,
    public val after: JsonElement?,
)

/**
 * Computes the leaf-level differences between two serialized states.
 *
 * Objects and arrays are walked recursively; arrays are compared by index. The result is a flat,
 * order-stable list of added/removed/changed leaves suitable for rendering a per-action diff.
 */
public fun diffJson(before: JsonElement, after: JsonElement): List<DiffEntry> {
    val out = ArrayList<DiffEntry>()
    diffInto(before, after, "", out)
    return out
}

private fun childrenOf(element: JsonElement): Map<String, JsonElement>? = when (element) {
    is JsonObject -> element
    is JsonArray -> element.mapIndexed { i, v -> i.toString() to v }.toMap()
    else -> null
}

private fun join(prefix: String, key: String): String = if (prefix.isEmpty()) key else "$prefix.$key"

private fun diffInto(before: JsonElement, after: JsonElement, prefix: String, out: MutableList<DiffEntry>) {
    val b = childrenOf(before)
    val a = childrenOf(after)
    if (b == null || a == null) {
        if (before != after) out.add(DiffEntry(DiffOp.CHANGED, prefix, before, after))
        return
    }
    for ((key, bv) in b) {
        val path = join(prefix, key)
        val av = a[key]
        if (av == null) out.add(DiffEntry(DiffOp.REMOVED, path, bv, null)) else diffInto(bv, av, path, out)
    }
    for ((key, av) in a) {
        if (key !in b) out.add(DiffEntry(DiffOp.ADDED, join(prefix, key), null, av))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*JsonDiffTest*' --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/JsonDiff.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/JsonDiffTest.kt
git commit -m "feat(devtools-core): add JSON state diff"
```

---

## Task 4: `DevToolsEvent` and `DevToolsOutput`

The event published on the session flow, and the subscriber contract. `DevToolsEvent` is sealed so Plan 2 (pipeline) can add cases without breaking outputs.

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsEvent.kt`
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsOutput.kt`

- [ ] **Step 1: Create `DevToolsEvent.kt`**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonElement

/**
 * An event emitted by a [DevToolsSession] on its [DevToolsSession.events] flow. Subscribers
 * ([DevToolsOutput]s) render or stream these. New cases may be added (e.g. pipeline traces in a
 * later module), so consumers must handle the sealed hierarchy exhaustively with an `else` or by
 * ignoring unknown cases.
 */
public sealed interface DevToolsEvent {

    /**
     * The session's initial state, emitted once when the store is created.
     *
     * @property state the serialized preloaded state.
     */
    public data class Initialized(public val state: JsonElement) : DevToolsEvent

    /**
     * A recorded dispatched action and the state it produced.
     *
     * @property actionId monotonic id assigned by the recorder (1-based).
     * @property action the serialized action.
     * @property state the serialized state after the action.
     * @property diff leaf-level changes versus the previous state.
     * @property timestampMillis epoch-millis capture time (dispatch-thread capture, not serialize time).
     * @property isExcess `true` if this action pushed the history past `maxAge` and the oldest was
     *   committed/evicted; the remote wire protocol forwards this flag to the monitor.
     */
    public data class ActionRecorded(
        public val actionId: Int,
        public val action: JsonElement,
        public val state: JsonElement,
        public val diff: List<DiffEntry>,
        public val timestampMillis: Long,
        public val isExcess: Boolean,
    ) : DevToolsEvent
}
```

- [ ] **Step 2: Create `DevToolsOutput.kt`**

```kotlin
package org.reduxkotlin.devtools

/**
 * A toggleable consumer of a [DevToolsSession]'s feed. The in-app drawer and the remote WebSocket
 * sink are both outputs. Implementations subscribe to [DevToolsSession.events] in [start] and
 * release resources in [stop]; they must never throw into the hub.
 */
public interface DevToolsOutput {

    /** Stable identifier (e.g. `"remote"`, `"inapp"`); used by the UI to list and toggle outputs. */
    public val id: String

    /** Human-readable label shown in the Outputs list. */
    public val label: String

    /** Begin consuming [session]. Called when the output is enabled. Must not block. */
    public fun start(session: DevToolsSession)

    /** Stop consuming and release resources. Called when the output is disabled. Must be idempotent. */
    public fun stop()
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :redux-kotlin-devtools-core:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsEvent.kt \
        redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsOutput.kt
git commit -m "feat(devtools-core): add DevToolsEvent and DevToolsOutput contracts"
```

---

## Task 5: `DevToolsSession` (recorder + off-thread serialize/diff + flow)

The session captures `(action, state)` references cheaply, then serializes/diffs/records on a background coroutine and emits events. State is immutable by redux contract, so capturing the reference on the dispatch thread is safe.

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsSession.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsSessionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevToolsSessionTest {

    private data class St(val n: Int)

    @Test
    fun init_then_record_emits_initialized_then_action_with_diff() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "s"), dispatcher)

        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }

        session.init(St(0))
        session.record("Inc", St(1))
        testScheduler.advanceUntilIdle()
        session.close()
        job.cancel()

        assertTrue(received[0] is DevToolsEvent.Initialized)
        val rec = received[1] as DevToolsEvent.ActionRecorded
        assertEquals(1, rec.actionId)
        // ToStringValueSerializer renders the state; the only changed leaf is n: 0 -> 1.
        assertEquals(DiffOp.CHANGED, rec.diff.single().op)
    }

    @Test
    fun denylisted_actions_are_not_recorded() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(denylist = listOf("Noise")), dispatcher)

        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }

        session.init(St(0))
        session.record("Noise", St(0))
        testScheduler.advanceUntilIdle()
        session.close()
        job.cancel()

        assertTrue(received.none { it is DevToolsEvent.ActionRecorded })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsSessionTest*' --console=plain`
Expected: FAIL — `DevToolsSession.create` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One observed store. Captures `(action, state)` cheaply on the dispatch thread and serializes,
 * diffs, and records lifted state on a single background coroutine, emitting [DevToolsEvent]s on
 * [events]. Construct via [create]; the [DevToolsHub] owns sessions in normal use.
 *
 * Concurrency contract: only the consumer coroutine touches [recorder] and [lastStateJson], so the
 * recorder's single-threaded invariant holds without locks. Producers (`init`/`record`) hand off via
 * a non-blocking [Channel.trySend] from the dispatch thread — recording never blocks dispatch.
 */
public class DevToolsSession private constructor(
    /** Stable id for this session (the config's `instanceId` or `name`). */
    public val id: String,
    private val config: DevToolsConfig,
    private val serializer: ValueSerializer,
    private val recorder: LiftedStateRecorder,
    private val scope: CoroutineScope,
) {
    private val denyRegex = config.denylist.map(::Regex)
    private val allowRegex = config.allowlist.map(::Regex)

    // DROP_OLDEST: under sustained burst we drop the oldest pending capture rather than block
    // dispatch. We count drops and warn (throttled) so silent history gaps are visible.
    private val captures = Channel<Capture>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var dropped = 0L
    private val recentLock = kotlinx.atomicfu.locks.SynchronizedObject()
    private val recent = ArrayDeque<DevToolsEvent.ActionRecorded>()

    private val _events = MutableSharedFlow<DevToolsEvent>(
        replay = 1,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Hot stream of recorder events. Replays only the most recent event to new subscribers — a late
     * subscriber MUST seed from [history] / [liftedState] first to backfill (see the backfill note in
     * the plan). Do not rely on `replay` for full history.
     */
    public val events: SharedFlow<DevToolsEvent> = _events

    // Consumer-coroutine-confined: only [process] reads/writes it. No @Volatile needed.
    private var lastStateJson: JsonElement? = null

    private sealed interface Capture {
        data class Init(val state: Any?) : Capture
        data class Action(val action: Any, val state: Any?, val timestampMillis: Long) : Capture
    }

    init {
        scope.launch {
            for (capture in captures) {
                runCatching { process(capture) }.onFailure { config.logger("devtools process: ${it.message}") }
            }
        }
    }

    /** Records the store's initial state. Call once at store creation, before any reader. */
    public fun init(state: Any?) {
        captures.trySend(Capture.Init(state))
    }

    /** Records a dispatched [action] and the resulting [state]. Cheap; heavy work runs off-thread. */
    public fun record(action: Any, state: Any?) {
        if (!shouldSend(action, denyRegex, allowRegex)) return
        // systemClock() is the dispatch-time capture timestamp (more accurate than the recorder's
        // own clock read on the background thread); the small skew between the two is acceptable.
        val result = captures.trySend(Capture.Action(action, state, systemClock()))
        if (result.isFailure) {
            dropped++
            if (dropped == 1L || dropped % 100L == 0L) config.logger("devtools: dropped $dropped captures (dispatch outpacing recorder)")
        }
    }

    /** The current lifted-state snapshot (Redux DevTools shape). Safe from any thread after [init]. */
    public fun liftedState(): JsonObject = recorder.liftedState()

    /**
     * A snapshot of recently recorded actions (bounded by `maxAge`), newest last. A subscriber should
     * call this immediately after subscribing to [events], then dedupe by [DevToolsEvent.ActionRecorded.actionId]
     * to backfill the actions it missed before subscribing.
     */
    public fun history(): List<DevToolsEvent.ActionRecorded> =
        kotlinx.atomicfu.locks.synchronized(recentLock) { recent.toList() }

    /** Stops background processing. Idempotent. */
    public fun close() {
        captures.close()
        scope.cancel()
    }

    private fun process(capture: Capture) {
        when (capture) {
            is Capture.Init -> {
                val stateJson = serializer.toJson(capture.state)
                recorder.init(stateJson)
                lastStateJson = stateJson
                kotlinx.atomicfu.locks.synchronized(recentLock) { recent.clear() }
                _events.tryEmit(DevToolsEvent.Initialized(stateJson))
            }
            is Capture.Action -> {
                val actionJson = serializer.toJson(capture.action)
                val stateJson = serializer.toJson(capture.state)
                val before = lastStateJson ?: stateJson
                val diff = diffJson(before, stateJson)
                val recorded = recorder.record(actionJson, stateJson)
                lastStateJson = stateJson
                val event = DevToolsEvent.ActionRecorded(
                    actionId = recorded.actionId,
                    action = actionJson,
                    state = stateJson,
                    diff = diff,
                    timestampMillis = capture.timestampMillis,
                    isExcess = recorded.isExcess,
                )
                kotlinx.atomicfu.locks.synchronized(recentLock) {
                    recent.addLast(event)
                    while (recent.size > config.maxAge) recent.removeFirst()
                }
                _events.tryEmit(event)
            }
        }
    }

    /** Factory used by [DevToolsHub] and tests. [dispatcher] defaults to [Dispatchers.Default]. */
    public companion object {
        /** Creates a session running its single background consumer on [dispatcher]. */
        public fun create(
            config: DevToolsConfig,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): DevToolsSession {
            val serializer = config.serializer ?: platformDefaultSerializer()
            val recorder = LiftedStateRecorder(maxAge = config.maxAge, clock = systemClock)
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            return DevToolsSession(config.instanceId ?: config.name, config, serializer, recorder, scope)
        }
    }
}
```

- [ ] **Step 3a: Extract `shouldSend` into its own file first (the session and the Task 7 enhancer both use it)**

Create `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/Filtering.kt` with the function copied verbatim from the old `DevTools.kt`:

```kotlin
package org.reduxkotlin.devtools

/**
 * Returns `true` if [action] should be recorded given the [denylist]/[allowlist] regexes. An action
 * is identified by its class `simpleName` (falling back to `toString()`); denied matches win, and a
 * non-empty allowlist must match.
 */
internal fun shouldSend(action: Any, denylist: List<Regex>, allowlist: List<Regex>): Boolean {
    val key = action::class.simpleName ?: action.toString()
    val denied = denylist.any { it.containsMatchIn(key) }
    val allowed = allowlist.isEmpty() || allowlist.any { it.containsMatchIn(key) }
    return !denied && allowed
}
```

> **Grounded against source:** `Clock.kt` provides `internal typealias EpochMillis = () -> Long` and `internal val systemClock: EpochMillis` — use `systemClock` for the recorder clock and `systemClock()` for the capture timestamp (both shown in the session code). `shouldSend` is the verbatim body from the old `DevTools.kt` (don't reimplement). `kotlinx.atomicfu.locks.synchronized(recentLock)` guards only the small `recent` deque (written by the consumer coroutine, read by `history()` on the UI thread); `recentLock` is a `kotlinx.atomicfu.locks.SynchronizedObject` — same primitives as `DevToolsHub`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsSessionTest*' --console=plain`
Expected: PASS (2 tests). If a clock symbol mismatch appears, fix per the note and re-run.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsSession.kt \
        redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/Filtering.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsSessionTest.kt
git commit -m "feat(devtools-core): transport-agnostic DevToolsSession with off-thread serialize/diff"
```

---

## Task 6: `DevToolsHub` (process-global session + output registry)

Thread-safe (dispatch threads create/look up sessions; UI thread reads; multiple stores run concurrently). Mirrors `redux-kotlin-registry`'s atomicfu locking.

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsHub.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsHubTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DevToolsHubTest {

    @AfterTest
    fun cleanup() = DevToolsHub.reset()

    @Test
    fun createSession_is_idempotent_per_instanceId() {
        val a = DevToolsHub.createSession(DevToolsConfig(name = "store"))
        val b = DevToolsHub.createSession(DevToolsConfig(name = "store"))
        assertSame(a, b)
        assertEquals(1, DevToolsHub.sessions().size)
    }

    @Test
    fun distinct_instanceIds_yield_distinct_sessions() {
        DevToolsHub.createSession(DevToolsConfig(name = "one"))
        DevToolsHub.createSession(DevToolsConfig(name = "two"))
        assertEquals(2, DevToolsHub.sessions().size)
    }

    @Test
    fun registerOutput_lists_the_output() {
        val out = object : DevToolsOutput {
            override val id = "remote"
            override val label = "Remote"
            override fun start(session: DevToolsSession) = Unit
            override fun stop() = Unit
        }
        DevToolsHub.registerOutput(out)
        assertTrue(DevToolsHub.outputs().any { it.id == "remote" })
    }

    @Test
    fun colliding_id_with_different_config_logs_a_warning() {
        val warnings = mutableListOf<String>()
        val logger: (String) -> Unit = { warnings.add(it) }
        // Same id ("dup"), different config (different maxAge) => collision warning, one session.
        DevToolsHub.createSession(DevToolsConfig(name = "dup", maxAge = 50, logger = logger))
        DevToolsHub.createSession(DevToolsConfig(name = "dup", maxAge = 10, logger = logger))
        assertEquals(1, DevToolsHub.sessions().size)
        assertTrue(warnings.any { it.contains("share devtools id") })
    }

    @Test
    fun removeSession_closes_and_drops_it() {
        DevToolsHub.createSession(DevToolsConfig(name = "gone"))
        DevToolsHub.removeSession("gone")
        assertTrue(DevToolsHub.sessions().isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsHubTest*' --console=plain`
Expected: FAIL — `DevToolsHub` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Process-global, debug-only registry that rendezvous the [devTools] enhancer with its outputs.
 *
 * The enhancer records into a [DevToolsSession] keyed by `instanceId`; [DevToolsOutput]s subscribe
 * to those sessions. Multi-store support falls out for free — each enhanced store is one session.
 * This object holds static state and therefore must only exist in debug builds (the release no-op
 * artifact in Plan 3 has no hub).
 */
public object DevToolsHub {
    private val lock = SynchronizedObject()
    private val sessionsById = LinkedHashMap<String, DevToolsSession>()
    private val configsById = LinkedHashMap<String, DevToolsConfig>()
    private val registeredOutputs = ArrayList<DevToolsOutput>()

    /**
     * Returns the existing session for the config's id, or creates and registers a new one.
     *
     * Sessions are keyed by `instanceId ?: name`. **Footgun guard:** if a session already exists for
     * the id but was created from a *different* config, two distinct stores have collided on one id
     * (most often two stores both left at the default `name = "redux-kotlin"`). Their actions would
     * interleave into one session. We log a warning so the integrator gives each store a distinct
     * `name`/`instanceId`; we still return the existing session (re-enhancing the same store with the
     * same config is the legitimate idempotent case and stays silent).
     */
    public fun createSession(config: DevToolsConfig): DevToolsSession = synchronized(lock) {
        val id = config.instanceId ?: config.name
        val existing = sessionsById[id]
        if (existing != null) {
            if (configsById[id] != config) {
                config.logger(
                    "devtools: two stores share devtools id \"$id\" — give each store a distinct " +
                        "DevToolsConfig.name or instanceId, or their actions will interleave.",
                )
            }
            return@synchronized existing
        }
        val created = DevToolsSession.create(config)
        sessionsById[id] = created
        configsById[id] = config
        created
    }

    /** The session registered under [id], or `null`. */
    public fun session(id: String): DevToolsSession? = synchronized(lock) { sessionsById[id] }

    /** A snapshot of all active sessions (the drawer's store-picker source). */
    public fun sessions(): List<DevToolsSession> = synchronized(lock) { sessionsById.values.toList() }

    /** Closes and removes the session under [id]. Call when a store is torn down to avoid leaks. */
    public fun removeSession(id: String): Unit = synchronized(lock) {
        sessionsById.remove(id)?.close()
        configsById.remove(id)
    }

    /** Registers a [DevToolsOutput]. Outputs decide for themselves whether to start (off by default). */
    public fun registerOutput(output: DevToolsOutput): Unit = synchronized(lock) {
        if (registeredOutputs.none { it.id == output.id }) registeredOutputs.add(output)
    }

    /** A snapshot of all registered outputs (the Outputs tab source). */
    public fun outputs(): List<DevToolsOutput> = synchronized(lock) { registeredOutputs.toList() }

    /** Clears all sessions and outputs. For tests only. */
    public fun reset(): Unit = synchronized(lock) {
        sessionsById.values.forEach { it.close() }
        sessionsById.clear()
        configsById.clear()
        registeredOutputs.clear()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsHubTest*' --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsHub.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsHubTest.kt
git commit -m "feat(devtools-core): add process-global DevToolsHub registry"
```

---

## Task 7: transport-agnostic `devTools()` enhancer

Wraps the store creator: creates/looks up the session, records the initial state, and wraps `dispatch` to record each action + resulting state. Every instrumentation path is wrapped so it can never break the host store.

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevTools.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsEnhancerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DevToolsEnhancerTest {

    private data class St(val n: Int = 0)
    private object Inc

    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @AfterTest
    fun cleanup() = DevToolsHub.reset()

    @Test
    fun enhancer_creates_a_session_and_does_not_alter_dispatch_result() {
        val store = createStore(reducer, St(), devTools(DevToolsConfig(name = "app")))
        store.dispatch(Inc)
        // Host behaviour is untouched.
        assertEquals(1, store.state.n)
        // A session exists in the hub for this store.
        assertEquals("app", DevToolsHub.sessions().single().id)
    }

    @Test
    fun enhancer_composes_with_applyMiddleware() {
        val log = mutableListOf<Any>()
        val mw: (org.reduxkotlin.Store<St>) -> ((Any) -> Any) -> (Any) -> Any =
            { _ -> { next -> { action -> log.add(action); next(action) } } }
        val store = createStore(reducer, St(), org.reduxkotlin.compose(devTools(DevToolsConfig(name = "c")), applyMiddleware(mw)))
        store.dispatch(Inc)
        assertEquals(1, store.state.n)
        assertEquals(listOf<Any>(Inc), log)
    }
}
```

> The second test pins down composition order: `compose(devTools(...), applyMiddleware(...))` must yield a working store whose middleware still runs and whose state is correct. If `compose` argument order needs flipping for enhancers in this codebase, adjust the test to the idiom used by existing enhancer tests (check `redux-kotlin`'s `applyMiddleware` tests) — the assertion (state == 1, middleware ran) stays.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsEnhancerTest*' --console=plain`
Expected: FAIL — `devTools` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools

import org.reduxkotlin.Store
import org.reduxkotlin.StoreCreator
import org.reduxkotlin.StoreEnhancer

/**
 * The one DevTools store enhancer. Records actions and resulting state into a [DevToolsSession] in
 * the [DevToolsHub] and publishes them; it owns no transport. Outputs (in-app drawer, remote WS)
 * subscribe to the session feed. All instrumentation is wrapped so it can never break the host store.
 *
 * @param config recording configuration (name, filters, serializer, logger).
 */
public fun <State> devTools(config: DevToolsConfig = DevToolsConfig()): StoreEnhancer<State> =
    { storeCreator: StoreCreator<State> ->
        { reducer, initialState, enhancer ->
            val store: Store<State> = storeCreator(reducer, initialState, enhancer)
            val session = runCatching { DevToolsHub.createSession(config) }
                .onFailure { config.logger("devtools: init failed: ${it.message}") }
                .getOrNull()

            if (session != null) {
                runCatching { session.init(store.getState()) }
                    .onFailure { config.logger("devtools: init-state failed: ${it.message}") }

                val origDispatch = store.dispatch
                store.dispatch = { action ->
                    val result = origDispatch(action)
                    @Suppress("TooGenericExceptionCaught") // devtools must never break the host store
                    try {
                        session.record(action, store.getState())
                    } catch (t: Throwable) {
                        config.logger("devtools: record failed: ${t.message}")
                    }
                    result
                }
            }
            store
        }
    }
```

> **Grounded against source:** this mirrors the existing `DevTools.kt` idiom verbatim — `StoreEnhancer`/`StoreCreator`/`Dispatcher` are function-type typealiases, so use **plain lambdas** (`{ storeCreator -> { reducer, initialState, enhancer -> ... } }`) and assign `store.dispatch = { action -> ... }` (it is a `var`). Wrap the record path in `try/catch(Throwable)` with the `@Suppress("TooGenericExceptionCaught")` comment exactly as the original did — `runCatching` also works but match the house style. The `@@INIT` action is dispatched *inside* `createStore` before this wrapper is installed, so it is never double-recorded; the initial state is captured via `session.init(store.getState())`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsEnhancerTest*' --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Add the safety-contract test (instrumentation never breaks the host)**

Append to `DevToolsEnhancerTest.kt`:

```kotlin
    @Test
    fun throwing_serializer_never_breaks_dispatch_or_state() {
        val boom = object : ValueSerializer {
            override fun toJson(value: Any?): kotlinx.serialization.json.JsonElement =
                throw IllegalStateException("serializer boom")
        }
        val logged = mutableListOf<String>()
        val config = DevToolsConfig(name = "safe", serializer = boom, logger = { logged.add(it) })

        val store = createStore(reducer, St(), devTools(config))
        // Dispatch must succeed and the reducer's state must be correct even though the serializer
        // throws on the background coroutine. The host is never affected.
        store.dispatch(Inc)
        store.dispatch(Inc)

        assertEquals(2, store.state.n)
    }
```

This proves two protection layers: the enhancer's `try/catch` around `record(...)` on the dispatch thread, and the session consumer's `runCatching` around `process(...)` on the background coroutine (where `toJson` actually throws). The serializer error is swallowed and logged; `store.state` stays correct. (Background logging is async, so this test asserts only the host-side guarantee — `state == 2` — not the log contents, to stay non-flaky.)

- [ ] **Step 6: Run the safety test**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsEnhancerTest*' --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 7: Run the whole core module green**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --console=plain`
Expected: PASS (JsonDiff, Session, Hub, Enhancer suites).

- [ ] **Step 8: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevTools.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsEnhancerTest.kt
git commit -m "feat(devtools-core): transport-agnostic devTools() enhancer + safety-contract test"
```

---

## Task 8: Generate core public-API dump

**Files:**
- Create: `redux-kotlin-devtools-core/api/*.api` (generated)

- [ ] **Step 1: Generate the dump**

Run: `./gradlew :redux-kotlin-devtools-core:apiDump --console=plain`
Expected: BUILD SUCCESSFUL; `redux-kotlin-devtools-core/api/` now contains `.api` file(s).

- [ ] **Step 2: Sanity-check the surface**

Run: `git status --short redux-kotlin-devtools-core/api/`
Expected: new `.api` file(s) listed. Open one and confirm it contains `devTools`, `DevToolsHub`, `DevToolsSession`, `DevToolsOutput`, `DevToolsEvent`, `DevToolsConfig`, `diffJson`, `DiffEntry`, `DiffOp` — and does **not** leak internals (e.g. `Capture`).

- [ ] **Step 3: Verify apiCheck passes**

Run: `./gradlew :redux-kotlin-devtools-core:apiCheck --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-devtools-core/api/
git commit -m "build(devtools-core): commit public API dump"
```

---

## Task 9: Scaffold `redux-kotlin-devtools-remote` and move the Ktor transport

The WS code moves into `-remote` under package `org.reduxkotlin.devtools.remote`. The moved files currently use package `org.reduxkotlin.devtools` and reference each other; update their package + imports.

**Files:**
- Create: `redux-kotlin-devtools-remote/build.gradle.kts`
- Move: `WebSocketEngine.kt` (+ actuals), `socketcluster/ScClient.kt`, `socketcluster/ScFrame.kt`, `wire/Messages.kt`, `DevToolsSession.kt` (Ktor one → `RemoteConnection.kt`)
- Create: `RemoteConfig.kt`
- Modify: `settings.gradle.kts` (add `-remote` include if not already added in Task 1)

- [ ] **Step 1: Create `redux-kotlin-devtools-remote/build.gradle.kts`**

```kotlin
plugins {
    id("convention.library-mpp-loved")
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
            namespace = "org.reduxkotlin.devtools.remote"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-devtools-core"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        named("jvmCommonMain") {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                dependencies {
                    implementation(libs.ktor.client.cio)
                }
            }
        }
        named("nativeMain") {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        named("jsMain") {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        named("wasmJsMain") {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
            }
        }
    }
}
```

- [ ] **Step 2: Ensure the `-remote` include exists in `settings.gradle.kts`**

If Task 1 Step 1 added only `":redux-kotlin-devtools-core",`, add `":redux-kotlin-devtools-remote",` directly beneath it now.

- [ ] **Step 3: Move the Ktor files (preserving package-relative dirs)**

```bash
SRC=redux-kotlin-devtools/src
DST=redux-kotlin-devtools-remote/src
PKG=kotlin/org/reduxkotlin/devtools
for SS in commonMain jvmCommonMain jsMain nativeMain wasmJsMain; do
  mkdir -p "$DST/$SS/$PKG/socketcluster" "$DST/$SS/$PKG/wire"
done
git mv $SRC/commonMain/$PKG/socketcluster/ScClient.kt $DST/commonMain/$PKG/socketcluster/ScClient.kt
git mv $SRC/commonMain/$PKG/socketcluster/ScFrame.kt  $DST/commonMain/$PKG/socketcluster/ScFrame.kt
git mv $SRC/commonMain/$PKG/wire/Messages.kt          $DST/commonMain/$PKG/wire/Messages.kt
git mv $SRC/commonMain/$PKG/WebSocketEngine.kt        $DST/commonMain/$PKG/WebSocketEngine.kt
git mv $SRC/commonMain/$PKG/DevToolsSession.kt        $DST/commonMain/$PKG/RemoteConnection.kt
for SS in jvmCommonMain jsMain nativeMain wasmJsMain; do
  git mv "$SRC/$SS/$PKG/WebSocketEngine."* "$DST/$SS/$PKG/" 2>/dev/null || true
done
# Move any remaining Ktor-coupled files the explorer didn't enumerate:
ls $SRC/commonMain/$PKG/ 2>/dev/null || true
```

The final `ls` should show only `DevTools.kt` (the OLD enhancer — to be deleted in Task 11) remaining in the old module, plus possibly `commonTest` WS tests (move those too: `git mv $SRC/commonTest/$PKG/<WsTest>.kt $DST/commonTest/$PKG/`).

- [ ] **Step 4: Re-package the moved files**

In every moved file change the package declaration from `package org.reduxkotlin.devtools` (or `...devtools.socketcluster` / `...devtools.wire`) to the `.remote`-prefixed package: `org.reduxkotlin.devtools.remote`, `org.reduxkotlin.devtools.remote.socketcluster`, `org.reduxkotlin.devtools.remote.wire`. Then add imports for the core symbols they now consume across the module boundary: `ValueSerializer`, `ToStringValueSerializer`, `DevToolsConfig` → `import org.reduxkotlin.devtools.<symbol>`. Update intra-remote imports (`socketcluster`, `wire`) to the new `.remote.*` packages.

Run a compile to surface every unresolved reference and fix iteratively:
```bash
./gradlew :redux-kotlin-devtools-remote:compileKotlinJvm --console=plain
```
Expected after fixes: BUILD SUCCESSFUL. The `RemoteConnection` class (old Ktor `DevToolsSession`) will still reference `DevToolsConfig.host/port/secure` — those fields moved to `RemoteConfig` in the next task, so leave its connection loop referencing a `RemoteConfig` parameter you introduce in Task 10. Until then it may not compile; proceed to Task 10 and compile at its end.

- [ ] **Step 5: Commit (WIP allowed; module not yet compiling until Task 10)**

```bash
git add -A
git commit -m "refactor(devtools): relocate Ktor WS transport into redux-kotlin-devtools-remote"
```

---

## Task 10: `RemoteConfig` + `RemoteOutput` (the WS sink as a DevToolsOutput)

`RemoteOutput` implements `DevToolsOutput`, is off by default, and when started subscribes to the session feed and streams the existing wire messages over the existing connection machinery.

**Files:**
- Create: `redux-kotlin-devtools-remote/src/commonMain/.../RemoteConfig.kt`
- Create: `redux-kotlin-devtools-remote/src/commonMain/.../RemoteOutput.kt`
- Modify: `RemoteConnection.kt` (take a `RemoteConfig` for host/port/secure)
- Test: `redux-kotlin-devtools-remote/src/commonTest/.../RemoteOutputTest.kt`

- [ ] **Step 1: Create `RemoteConfig.kt`**

```kotlin
package org.reduxkotlin.devtools.remote

/**
 * Connection settings for the remote (WebSocket) DevTools output.
 *
 * @property host server host (use `10.0.2.2` from an Android emulator, or `localhost` with `adb reverse`).
 * @property port server port; the `@redux-devtools/cli` default is 8000.
 * @property secure use `wss` instead of `ws`.
 * @property startEnabled if `true`, connect at startup; otherwise stay off until enabled (default).
 */
public data class RemoteConfig(
    public val host: String = "localhost",
    public val port: Int = 8000,
    public val secure: Boolean = false,
    public val startEnabled: Boolean = false,
)
```

- [ ] **Step 2: Point `RemoteConnection` at `RemoteConfig`**

In `RemoteConnection.kt` (the relocated Ktor session), replace reads of `config.host`/`config.port`/`config.secure` with a `RemoteConfig` constructor parameter (`remoteConfig.host`, etc.). Keep the recording `DevToolsConfig` only if it still needs `name`/`instanceId`/`logger` for wire `MessageContext`; otherwise pass just the fields it uses. Recompile:
```bash
./gradlew :redux-kotlin-devtools-remote:compileKotlinJvm --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Write the failing test (RemoteOutput is off by default; starts on demand)**

```kotlin
package org.reduxkotlin.devtools.remote

import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteOutputTest {

    @Test
    fun has_stable_identity_and_is_off_by_default() {
        val out = RemoteOutput(RemoteConfig())
        assertEquals("remote", out.id)
        assertFalse(out.isRunning)
    }

    @Test
    fun start_then_stop_toggles_running_state() {
        val out = RemoteOutput(RemoteConfig(startEnabled = false))
        val session = DevToolsSession.create(DevToolsConfig(name = "t"))
        out.start(session)
        assertTrue(out.isRunning)
        out.stop()
        assertFalse(out.isRunning)
        session.close()
    }
}
```

> This test exercises lifecycle/state only — not a live socket (no server in unit scope). The existing JVM integration test that stands up a Ktor WS server (moved from the old module in Task 9) covers the wire round-trip; keep it running against `RemoteConnection`.

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-remote:jvmTest --tests '*RemoteOutputTest*' --console=plain`
Expected: FAIL — `RemoteOutput` unresolved.

- [ ] **Step 5: Write `RemoteOutput.kt`**

```kotlin
package org.reduxkotlin.devtools.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.DevToolsOutput
import org.reduxkotlin.devtools.DevToolsSession

/**
 * Streams a [DevToolsSession]'s feed to the external Redux DevTools monitor over WebSocket.
 *
 * Off by default — it carries connection overhead — and started either by [RemoteConfig.startEnabled]
 * at registration time or by the in-app Outputs toggle. The integrator (or the in-app module)
 * registers it with the hub.
 *
 * Late-start correctness: because the monitor expects a full STATE snapshot on (re)connect and the
 * session's flow only replays its single most recent event, [start] seeds the connection with the
 * current [DevToolsSession.liftedState] before following [DevToolsSession.events]. Events are handed
 * to the connection's bounded outbound buffer (`enqueue`), which the connect loop drains once the WS
 * handshake completes — so events captured before the socket is up are buffered, not lost.
 *
 * @param config connection settings.
 */
public class RemoteOutput(private val config: RemoteConfig) : DevToolsOutput {

    override val id: String = "remote"
    override val label: String = "Remote (WebSocket)"

    private var scope: CoroutineScope? = null
    private var connection: RemoteConnection? = null

    /** Whether the output is currently subscribed/connected. */
    public val isRunning: Boolean get() = scope != null

    override fun start(session: DevToolsSession) {
        if (isRunning) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        val conn = RemoteConnection(config, session).also { connection = it }
        conn.start()                                   // begins connect-and-drain loop on its own scope
        // Seed the monitor with the full current history, then follow live events.
        runCatching { conn.enqueueState(session.liftedState()) }
        session.events
            .onEach { event -> runCatching { conn.enqueue(toWireMessage(it = event, session = session)) } }
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

> **Adapter note (wire to the relocated `RemoteConnection`):** the old Ktor `DevToolsSession` exposed `start()` / `enqueue(JsonObject)` / `stop()` with an internal `Channel(256)` outbound buffer drained by its connect loop — keep that shape and rename the class to `RemoteConnection`. Give it a `RemoteConfig` (host/port/secure) + the recording `DevToolsConfig` (for `name`/`instanceId` in `MessageContext`), and add a thin `enqueueState(lifted: JsonObject)` that wraps the existing `stateMessage(ctx, lifted)` helper. Implement `toWireMessage(it: DevToolsEvent, session): JsonObject` mapping `DevToolsEvent.ActionRecorded` → the existing `actionMessage(ctx, performAction, state, nextActionId, isExcess)` and `DevToolsEvent.Initialized` → `startMessage(ctx)` followed by `stateMessage(ctx, session.liftedState())`, all from the relocated `wire/Messages.kt`. **Reuse those helpers — do not reinvent the SocketCluster framing.** `actionMessage` needs `nextActionId` and `isExcess`: `isExcess` is now carried on `DevToolsEvent.ActionRecorded` (added in Task 4) and `nextActionId = actionId + 1`, so no extra plumbing is required.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-remote:jvmTest --tests '*RemoteOutputTest*' --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 7: Run the moved WS integration test**

Run: `./gradlew :redux-kotlin-devtools-remote:jvmTest --console=plain`
Expected: PASS (RemoteOutput suite + the relocated server round-trip test).

- [ ] **Step 8: Generate remote API dump + commit**

```bash
./gradlew :redux-kotlin-devtools-remote:apiDump --console=plain
git add -A
git commit -m "feat(devtools-remote): RemoteConfig + RemoteOutput streaming the session feed"
```

---

## Task 11: Remove the old `redux-kotlin-devtools` module and reconcile references

**Files:**
- Delete: `redux-kotlin-devtools/`
- Modify: `settings.gradle.kts` (ensure old include gone), `redux-kotlin-bom/build.gradle.kts` (if it lists the module), any sample/`examples` references

- [ ] **Step 1: Confirm nothing useful remains in the old module**

Run: `find redux-kotlin-devtools/src -type f`
Expected: only `DevTools.kt` (old enhancer, superseded by `-core`) and any already-copied tests. If anything unmoved still has unique logic, move it now; otherwise proceed.

- [ ] **Step 2: Find all references to the old module path**

Run:
```bash
grep -rn 'redux-kotlin-devtools"' settings.gradle.kts redux-kotlin-bom/ examples/ 2>/dev/null
grep -rn ':redux-kotlin-devtools\b' --include=*.kts . 2>/dev/null | grep -v devtools-core | grep -v devtools-remote
```
Expected: lists the stale `:redux-kotlin-devtools` include and any BOM/sample dep. Update each to `:redux-kotlin-devtools-core` (and add `-remote` where the WS transport was used).

- [ ] **Step 3: Delete the module**

```bash
git rm -r redux-kotlin-devtools
```
And remove the stale `":redux-kotlin-devtools",` include from `settings.gradle.kts` if still present.

- [ ] **Step 4: Full build**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL — compiles, all tests, detekt, and `apiCheck` for both new modules pass. Fix any remaining reference. (If `iosSimulatorArm64Test` fails with an Xcode SDK error, that's environmental per CLAUDE.md — trust CI; re-run with `./gradlew build -x iosSimulatorArm64Test` to confirm the rest is green.)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(devtools): remove monolithic redux-kotlin-devtools module"
```

---

## Task 12: Final gate — detekt, apiCheck, full build

- [ ] **Step 1: Lint the whole tree**

Run: `./gradlew detektAll --console=plain`
Expected: BUILD SUCCESSFUL. (The pre-commit hook already ran `--auto-correct`; this confirms no KDoc/`UndocumentedPublic*` violations remain on the new public surfaces.)

- [ ] **Step 2: API check both modules**

Run: `./gradlew :redux-kotlin-devtools-core:apiCheck :redux-kotlin-devtools-remote:apiCheck --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Confirm clean tree**

Run: `git status --short`
Expected: empty (all committed).

- [ ] **Step 4: Final summary commit (if any stray dumps/format)**

If Step 1/2 produced changes:
```bash
git add -A
git commit -m "chore(devtools): apiDump + detekt fixups"
```

---

## Self-Review (completed against the spec)

**Spec coverage (engine scope):**
- "Extract `redux-kotlin-devtools-core` from the WS module" → Tasks 1–8. ✔
- "transport-agnostic `devTools()` enhancer — records + publishes only" → Task 7. ✔
- "`DevToolsHub`: process-global registry of per-store sessions + outputs; publishes Flow" → Tasks 5–6. ✔
- "`DevToolsOutput` interface for a toggleable consumer" → Task 4. ✔
- "JSON diff" (core) → Task 3. ✔
- "Reshape WS into a `DevToolsOutput` (`-remote`), off by default, `startEnabled`" → Tasks 9–10. ✔
- "Multi-store falls out for free — each enhanced store is a session" → Task 6 (idempotent per `instanceId`, distinct ids → distinct sessions). ✔
- "Off-thread serialization; dispatch captures action + immutable state ref" → Task 5 (channel + background coroutine). ✔
- "Read-only safety: instrumentation never throws into host" → Task 7 (`runCatching` on every path). ✔
- Config split (core `DevToolsConfig` minus transport; `-remote` `RemoteConfig`) → Tasks 2, 10. ✔
- Public-API guardrail (`apiDump`/`apiCheck`) → Tasks 8, 10, 12. ✔

**Deferred to later plans (intentionally out of scope, noted in header):**
- Pipeline combinators + `PipelineModel` + per-action trace → **Plan 2** (`DevToolsEvent` is sealed to accept new cases).
- `-inapp` Compose UI, triggers, tabs, theme; `-inapp-noop` → **Plan 3**.
- Per-platform integration docs + samples → end of **Plan 3**.

**Placeholder scan:** No "TBD"/"handle errors"/"similar to". Three explicit *adapter notes* (clock symbol, enhancer lambda syntax, `RemoteConnection` method names) are present because they depend on exact moved-code shapes the executor will see; each gives the concrete fallback and forbids reinvention. These are verification instructions, not unfinished steps.

**Type consistency:** `DevToolsConfig`, `DevToolsSession.create(config, dispatcher)`, `DevToolsSession.{init,record,liftedState,history,close}`, `DevToolsEvent.{Initialized,ActionRecorded(+isExcess)}`, `DevToolsOutput.{id,label,start,stop}`, `DiffEntry/DiffOp/diffJson`, `DevToolsHub.{createSession,session,sessions,removeSession,registerOutput,outputs,reset}`, `RemoteConfig.{host,port,secure,startEnabled}`, `RemoteOutput(config).{id,label,isRunning,start,stop}` are used identically across Tasks 4–10.

---

## Review-driven revisions (correctness / threading / performance / gaps)

This plan was reviewed against the real source before finalizing. Changes applied:

- **C1 (compile):** Task 7 enhancer rewritten from illegal SAM syntax to the confirmed plain-lambda idiom (`{ storeCreator -> { reducer, initialState, enhancer -> ... } }`, `store.dispatch = { action -> ... }`, `try/catch(Throwable)` + `@Suppress("TooGenericExceptionCaught")`), matching the existing `DevTools.kt`.
- **C2 (compile):** Task 5 uses the real clock — `LiftedStateRecorder(maxAge, clock = systemClock)` and `systemClock()` for the capture timestamp (`Clock.kt` exposes `EpochMillis`/`systemClock`, not a `currentEpochMillis()`).
- **C3 (dedupe):** Task 5 reuses the existing `shouldSend(action, denyRegex, allowRegex)` (moved into `-core`) instead of a forked filter.
- **T1 (threading, documented):** added the immutability precondition — off-thread serialization is only correct if state/actions are deeply immutable.
- **T2 (threading, verified):** `liftedState()` returns the recorder's `@Volatile snapshot`; safe from any thread after the first `init`. Precondition documents "never read before init."
- **T3 (threading, simplified):** dropped the needless `@Volatile` on `lastStateJson` — it is confined to the single consumer coroutine.
- **G1 (gap, mandated test):** added the safety-contract test (throwing serializer → dispatch + state unaffected) to Task 7.
- **G2 (gap, footgun):** `DevToolsHub.createSession` now warns on id collision with a differing config (two un-named stores) + a test.
- **G3 (gap, backfill):** added `DevToolsSession.history()` and documented the subscribe-then-seed-then-dedupe contract for late subscribers (the UI in Plan 3 and the remote snapshot-on-connect both depend on it).
- **G4 (gap, remote):** `RemoteOutput` now seeds `liftedState()` on connect and routes events through the connection's bounded `enqueue` buffer (drain-after-handshake), not a direct send racing the WS handshake.
- **G5 (gap, leak):** added `DevToolsHub.removeSession(id)` + test.
- **P1/P2 (perf):** `record()` counts and throttle-logs dropped captures under burst (`DROP_OLDEST` is intentional — never block dispatch). `shouldSend`'s `simpleName ?: toString()` name derivation runs on the dispatch path; acceptable, noted.

**Carried to Plan 2 (pipeline):** the combinator↔session wiring (how wrapped middleware/reducers find the active session to write traces) is an open design point flagged in the spec — resolve with a short design spike before Plan 2; `DevToolsEvent` is sealed to accept `PipelineRegistered`/`PipelineTraced` cases then.

**Carried to Plan 3 (in-app UI):** the UI must seed from `DevToolsSession.history()` (backfill) then follow `events`, deduping by `actionId`. The release no-op artifact must reproduce the full app-facing surface (`devTools`, `DevToolsHub`, `ReduxDevToolsHost`, config types) with empty bodies and **no hub** so no static state leaks into release.
