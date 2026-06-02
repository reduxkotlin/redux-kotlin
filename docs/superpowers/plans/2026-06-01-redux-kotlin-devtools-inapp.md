# redux-kotlin In-App DevTools UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the in-app DevTools surface: a `redux-kotlin-devtools-inapp` Compose Multiplatform module (`ReduxDevToolsHost { app }` host, edge-swipe + floating-bubble triggers, an adaptive drawer with the five tabs Actions/State/Diff/Pipeline/Outputs, all rendered against the ReduxKotlin brand theme), plus a zero-overhead `redux-kotlin-devtools-inapp-noop` sibling with the identical app-facing API for release builds.

**Architecture:** A pure, framework-free `InAppModel` reduces the session's `DevToolsEvent` stream into immutable `InAppState` (action log, selection, filter, active tab, pipeline structure + per-action traces, outputs) — fully unit-testable in `commonTest`. A thin Compose layer (`rememberDevToolsController`) seeds the model from `session.history()`, follows `session.events`, dedupes by `actionId`, and exposes the state to the composables. `ReduxDevToolsHost` overlays the drawer + triggers **inside the host app's own Compose tree** — no system overlay window. Width adaptivity uses `BoxWithConstraints` (compact = bottom sheet, expanded = right-docked panel). The no-op artifact reproduces only the app-facing symbols with empty bodies and depends solely on `redux-kotlin` + `compose.runtime`.

**Tech Stack:** Compose Multiplatform 1.11.0 (`compose.runtime`, `compose.foundation`, `compose.material3`, `compose.ui`), `org.jetbrains.kotlin.plugin.compose`. Builds on Plan 1 (engine) + Plan 2 (pipeline). Visual source of truth: `docs/superpowers/specs/ReduxKotlin Design System/ui_kits/devtools/` + `colors_and_type.css`.

**Depends on:** Plan 1 + Plan 2 complete. Consumes `DevToolsHub`, `DevToolsSession.{events,history,liftedState}`, `DevToolsEvent.*`, `PipelineStructure`, `PipelineTrace`, `DiffEntry`/`DiffOp`.

**Backfill contract (from Plan 1 G3):** the controller MUST `session.history()` first, push those into the model, *then* start collecting `session.events`, deduping by `actionId` — so actions dispatched before the drawer opened are not lost and not double-counted.

**No-op safety contract (the #1 footgun):** `ReduxDevToolsHost`/`devTools()` are called from the app's **main** source set, so a `debugImplementation`-only dep would not compile in release. The fix is the LeakCanary pattern — `releaseImplementation(...-inapp-noop)` with an identical API, empty bodies, and **no** transitive Compose-material3/Ktor/core. Release compiles, links ~nothing, runs nothing.

---

## File Structure

### New module `redux-kotlin-devtools-inapp` (package `org.reduxkotlin.devtools.inapp`)
- `build.gradle.kts` — CMP library; deps `api(:redux-kotlin-devtools-core)` + Compose.
- `commonMain/.../InAppConfig.kt` — triggers, start tab, theme mode.
- `commonMain/.../theme/Tokens.kt` — brand colors/gradient/dimens from `colors_and_type.css`.
- `commonMain/.../theme/ReduxKotlinDevToolsTheme.kt` — `MaterialTheme` (ColorScheme + Typography + Shapes).
- `commonMain/.../model/InAppState.kt` — immutable UI state + `DevToolsTab` enum + `payloadPreview`/json helpers.
- `commonMain/.../model/InAppModel.kt` — pure reducer of `DevToolsEvent` → `InAppState` (TDD).
- `commonMain/.../DevToolsController.kt` — `rememberDevToolsController(session)`; seed + follow.
- `commonMain/.../ReduxDevToolsHost.kt` — the host composable + `object ReduxDevTools`.
- `commonMain/.../ui/Drawer.kt` — adaptive sheet/panel scaffold + tab bar + header.
- `commonMain/.../ui/Triggers.kt` — floating bubble + edge-swipe detector.
- `commonMain/.../ui/tabs/ActionsTab.kt`, `StateTab.kt`, `DiffTab.kt`, `PipelineTab.kt`, `OutputsTab.kt`.

### New module `redux-kotlin-devtools-inapp-noop` (same package, app-facing subset)
- `build.gradle.kts` — CMP library; deps `api(:redux-kotlin)` + `implementation(compose.runtime)` only.
- `commonMain/.../NoOp.kt` — `devTools`, `devToolsMiddleware`, `devToolsCombineReducers`, `named` ×2, `NamedMiddleware`, `NamedReducer`, `DevToolsConfig`, `InAppConfig`, `ReduxDevToolsHost`, `object ReduxDevTools` — all inert.

### Modified
- `settings.gradle.kts` — add the two module includes.

---

## Task 1: Scaffold the `-inapp` module

**Files:**
- Create: `redux-kotlin-devtools-inapp/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Create dirs under `redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/` and `.../commonTest/...`

- [ ] **Step 1: Add includes to `settings.gradle.kts`**

After the devtools-core/remote includes add:
```kotlin
    ":redux-kotlin-devtools-inapp",
    ":redux-kotlin-devtools-inapp-noop",
```

- [ ] **Step 2: Create `redux-kotlin-devtools-inapp/build.gradle.kts`** (mirrors `redux-kotlin-compose`)

```kotlin
plugins {
    id("convention.library-mpp-loved")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("convention.publishing-mpp")
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
            namespace = "org.reduxkotlin.devtools.inapp"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-devtools-core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        named("jvmTest") {
            dependencies {
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
```

- [ ] **Step 3: Create source dirs**

```bash
mkdir -p redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/{theme,model,ui/tabs} \
         redux-kotlin-devtools-inapp/src/commonTest/kotlin/org/reduxkotlin/devtools/inapp/model
```

- [ ] **Step 4: Verify configuration**

Run: `./gradlew :redux-kotlin-devtools-inapp:tasks --console=plain | head -3`
Expected: no configuration error.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts redux-kotlin-devtools-inapp/build.gradle.kts
git commit -m "build(devtools-inapp): scaffold Compose Multiplatform module"
```

---

## Task 2: `InAppConfig` + `DevToolsTab`

**Files:**
- Create: `.../inapp/InAppConfig.kt`
- Create: `.../inapp/model/InAppState.kt` (the `DevToolsTab` enum portion first; state struct in Task 3)

- [ ] **Step 1: `InAppConfig.kt`**

```kotlin
package org.reduxkotlin.devtools.inapp

/** Which built-in triggers open the drawer. */
public enum class DevToolsTrigger {
    /** A floating, draggable bubble (tap to open). Default on. */
    BUBBLE,

    /** A right-edge swipe/tab. Default on. */
    EDGE_SWIPE,
}

/** Drawer theme mode. */
public enum class DevToolsThemeMode {
    /** Follow the host app's light/dark setting. */
    SYSTEM,

    /** Always dark (the UI-kit default — best contrast for a developer tool). */
    DARK,

    /** Always light. */
    LIGHT,
}

/** Which tab is shown when the drawer first opens. */
public enum class DevToolsTab {
    /** The action log. */ ACTIONS,
    /** The state tree. */ STATE,
    /** The per-action diff. */ DIFF,
    /** The pipeline map. */ PIPELINE,
    /** The outputs list. */ OUTPUTS,
}

/**
 * Configuration for [ReduxDevToolsHost].
 *
 * @property triggers which built-in triggers are enabled (default: bubble + edge-swipe).
 * @property startTab the tab shown when the drawer opens.
 * @property theme the drawer theme mode.
 * @property instanceId the session id to show; `null` shows the hub's sole session (or a picker if many).
 */
public data class InAppConfig(
    public val triggers: Set<DevToolsTrigger> = setOf(DevToolsTrigger.BUBBLE, DevToolsTrigger.EDGE_SWIPE),
    public val startTab: DevToolsTab = DevToolsTab.ACTIONS,
    public val theme: DevToolsThemeMode = DevToolsThemeMode.DARK,
    public val instanceId: String? = null,
)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :redux-kotlin-devtools-inapp:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/InAppConfig.kt
git commit -m "feat(devtools-inapp): InAppConfig + DevToolsTab/trigger/theme enums"
```

---

## Task 3: `InAppState` + helpers

**Files:**
- Create/extend: `.../inapp/model/InAppState.kt`

- [ ] **Step 1: Write the file**

```kotlin
package org.reduxkotlin.devtools.inapp.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.PipelineStructure
import org.reduxkotlin.devtools.PipelineTrace
import org.reduxkotlin.devtools.inapp.DevToolsTab

/** A registered output row in the Outputs tab. */
public data class OutputRow(
    /** Stable output id (e.g. `"inapp"`, `"remote"`). */
    public val id: String,
    /** Display label. */
    public val label: String,
    /** Whether the output is currently enabled. */
    public val enabled: Boolean,
    /** `true` for the in-app output, which cannot be toggled off. */
    public val locked: Boolean,
)

/**
 * Immutable UI state for the drawer. Produced by [InAppModel] from the session event stream.
 *
 * @property actions recorded actions, oldest first (bounded by `maxAge`).
 * @property selectedId the action currently selected (drives State/Diff/Pipeline); defaults to the newest.
 * @property filter case-insensitive substring filter on action type.
 * @property activeTab the visible tab.
 * @property structure the static pipeline structure, if registered.
 * @property tracesById per-action pipeline traces, keyed by action id.
 * @property outputs the registered outputs and their on/off state.
 */
public data class InAppState(
    public val actions: List<DevToolsEvent.ActionRecorded> = emptyList(),
    public val selectedId: Int? = null,
    public val filter: String = "",
    public val activeTab: DevToolsTab = DevToolsTab.ACTIONS,
    public val structure: PipelineStructure? = null,
    public val tracesById: Map<Int, PipelineTrace> = emptyMap(),
    public val outputs: List<OutputRow> = emptyList(),
) {
    /** Actions matching [filter] (all when blank). */
    public val filteredActions: List<DevToolsEvent.ActionRecorded>
        get() = if (filter.isBlank()) actions else actions.filter { actionType(it.action).contains(filter, ignoreCase = true) }

    /** The selected action, or the newest, or `null` if empty. */
    public val selected: DevToolsEvent.ActionRecorded?
        get() = actions.firstOrNull { it.actionId == selectedId } ?: actions.lastOrNull()
}

/** Best-effort action type label from a serialized action (`type` field, else the primitive/string form). */
public fun actionType(action: JsonElement): String = when (action) {
    is JsonObject -> (action["type"] as? JsonPrimitive)?.content
        ?: (action["__name"] as? JsonPrimitive)?.content
        ?: "action"
    is JsonPrimitive -> action.content
    else -> action.toString()
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :redux-kotlin-devtools-inapp:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/model/InAppState.kt
git commit -m "feat(devtools-inapp): InAppState + action-type helper"
```

---

## Task 4: `InAppModel` — pure event reducer (TDD)

**Files:**
- Create: `.../inapp/model/InAppModel.kt`
- Test: `.../commonTest/.../inapp/model/InAppModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools.inapp.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.PipelineNode
import org.reduxkotlin.devtools.PipelineNodeKind
import org.reduxkotlin.devtools.PipelineNodeTrace
import org.reduxkotlin.devtools.PipelineStructure
import org.reduxkotlin.devtools.PipelineTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InAppModelTest {

    private fun action(id: Int, type: String) = DevToolsEvent.ActionRecorded(
        actionId = id,
        action = buildJsonObject { put("type", type) },
        state = buildJsonObject { put("n", id) },
        diff = emptyList(),
        timestampMillis = id.toLong(),
        isExcess = false,
    )

    @Test
    fun actions_accumulate_and_dedupe_by_id() {
        val m = InAppModel(maxActions = 50)
        m.submit(action(1, "A"))
        m.submit(action(2, "B"))
        m.submit(action(1, "A"))                       // duplicate (replay/backfill overlap)
        assertEquals(listOf(1, 2), m.state.value.actions.map { it.actionId })
    }

    @Test
    fun seed_then_live_events_backfill_without_duplicates() {
        val m = InAppModel(maxActions = 50)
        m.seed(listOf(action(1, "A"), action(2, "B")))
        m.submit(action(2, "B"))                       // already seeded
        m.submit(action(3, "C"))
        assertEquals(listOf(1, 2, 3), m.state.value.actions.map { it.actionId })
    }

    @Test
    fun selection_defaults_to_newest_until_user_selects() {
        val m = InAppModel(maxActions = 50)
        m.submit(action(1, "A")); m.submit(action(2, "B"))
        assertEquals(2, m.state.value.selected?.actionId)
        m.select(1)
        assertEquals(1, m.state.value.selected?.actionId)
    }

    @Test
    fun filter_narrows_by_type() {
        val m = InAppModel(maxActions = 50)
        m.submit(action(1, "AddTodo")); m.submit(action(2, "SetFilter"))
        m.setFilter("add")
        assertEquals(listOf(1), m.state.value.filteredActions.map { it.actionId })
    }

    @Test
    fun pipeline_structure_and_traces_are_stored() {
        val m = InAppModel(maxActions = 50)
        val structure = PipelineStructure(listOf(PipelineNode("dispatch", "dispatch(action)", PipelineNodeKind.ENTRY)))
        m.submit(DevToolsEvent.PipelineRegistered(structure))
        m.submit(DevToolsEvent.PipelineTraced(PipelineTrace(1, listOf(PipelineNodeTrace("dispatch", 5, true, false)))))
        assertEquals(structure, m.state.value.structure)
        assertTrue(m.state.value.tracesById.containsKey(1))
    }

    @Test
    fun maxActions_bounds_the_log() {
        val m = InAppModel(maxActions = 2)
        m.submit(action(1, "A")); m.submit(action(2, "B")); m.submit(action(3, "C"))
        assertEquals(listOf(2, 3), m.state.value.actions.map { it.actionId })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-inapp:jvmTest --tests '*InAppModelTest*' --console=plain`
Expected: FAIL — `InAppModel` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools.inapp.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.inapp.DevToolsTab

/**
 * Pure, framework-free holder that reduces the session's [DevToolsEvent] stream into [InAppState].
 * No Compose dependency, so it is unit-tested directly. The Compose layer ([rememberDevToolsController])
 * seeds it from history, then forwards live events.
 *
 * @param maxActions ring bound on the retained action log (typically the session's `maxAge`).
 */
public class InAppModel(private val maxActions: Int = 50) {

    private val _state = MutableStateFlow(InAppState())

    /** Observable UI state. */
    public val state: StateFlow<InAppState> = _state

    /** Seeds the log from [DevToolsSession.history] before live collection starts. */
    public fun seed(history: List<DevToolsEvent.ActionRecorded>) {
        history.forEach { submit(it) }
    }

    /** Applies one event. Idempotent for actions (dedupes by `actionId`). */
    public fun submit(event: DevToolsEvent) {
        _state.value = reduce(_state.value, event)
    }

    /** User selected an action row. */
    public fun select(actionId: Int) {
        _state.value = _state.value.copy(selectedId = actionId)
    }

    /** User typed in the filter box. */
    public fun setFilter(text: String) {
        _state.value = _state.value.copy(filter = text)
    }

    /** User switched tabs. */
    public fun setTab(tab: DevToolsTab) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    /** Replaces the outputs list (from the hub). */
    public fun setOutputs(outputs: List<OutputRow>) {
        _state.value = _state.value.copy(outputs = outputs)
    }

    private fun reduce(s: InAppState, event: DevToolsEvent): InAppState = when (event) {
        is DevToolsEvent.Initialized -> s.copy(actions = emptyList(), selectedId = null, tracesById = emptyMap())
        is DevToolsEvent.ActionRecorded -> {
            if (s.actions.any { it.actionId == event.actionId }) {
                s
            } else {
                val next = (s.actions + event).takeLast(maxActions)
                s.copy(actions = next)
            }
        }
        is DevToolsEvent.PipelineRegistered -> s.copy(structure = event.structure)
        is DevToolsEvent.PipelineTraced -> s.copy(tracesById = s.tracesById + (event.trace.actionId to event.trace))
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-inapp:jvmTest --tests '*InAppModelTest*' --console=plain`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/model/InAppModel.kt \
        redux-kotlin-devtools-inapp/src/commonTest/kotlin/org/reduxkotlin/devtools/inapp/model/InAppModelTest.kt
git commit -m "feat(devtools-inapp): pure InAppModel event reducer (TDD)"
```

---

## Task 5: Brand theme (`Tokens.kt` + `ReduxKotlinDevToolsTheme`)

Maps `colors_and_type.css` to Compose. Dark default (sheet `#0E1726`), brand blue/magenta/orange, gradient accent.

**Files:**
- Create: `.../inapp/theme/Tokens.kt`
- Create: `.../inapp/theme/ReduxKotlinDevToolsTheme.kt`

- [ ] **Step 1: `Tokens.kt`**

```kotlin
package org.reduxkotlin.devtools.inapp.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Raw ReduxKotlin brand tokens (from the design system's `colors_and_type.css`). */
public object RkTokens {
    /** Web primary blue `#137AF9`. */ public val Blue: Color = Color(0xFF137AF9)
    /** Lighter blue for dark surfaces. */ public val BlueLight: Color = Color(0xFF62A8FB)
    /** Logo gradient warm end `#F98909`. */ public val Orange: Color = Color(0xFFF98909)
    /** Logo gradient cool end `#C858BC`. */ public val Magenta: Color = Color(0xFFC858BC)
    /** Heritage Redux purple `#764ABC`. */ public val Purple: Color = Color(0xFF764ABC)
    /** Success green. */ public val Green: Color = Color(0xFF5FD39A)
    /** Error red. */ public val Red: Color = Color(0xFFFF7A8A)
    /** Diff "changed" amber. */ public val Amber: Color = Color(0xFFF9B357)

    /** Dark sheet surface `#0E1726`. */ public val InkSurface: Color = Color(0xFF0E1726)
    /** Dark elevated surface. */ public val InkSurfaceHigh: Color = Color(0xFF16203A)
    /** Primary text on dark. */ public val InkOn: Color = Color(0xFFE8EAF1)
    /** Dim text on dark. */ public val InkDim: Color = Color(0xFF8A93A5)
    /** Faint text on dark. */ public val InkFaint: Color = Color(0xFF5B657A)

    /** The signature magenta→orange gradient (use sparingly: tab indicator, bubble, edge tab). */
    public val gradient: Brush = Brush.linearGradient(
        colors = listOf(Magenta, Orange),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    /** Sheet top corner radius (M3 expressive `xl`). */ public val SheetCorner = 28.dp
}
```

- [ ] **Step 2: `ReduxKotlinDevToolsTheme.kt`**

```kotlin
package org.reduxkotlin.devtools.inapp.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import org.reduxkotlin.devtools.inapp.DevToolsThemeMode

private val DarkColors = darkColorScheme(
    primary = RkTokens.BlueLight,
    secondary = RkTokens.Magenta,
    tertiary = RkTokens.Orange,
    background = RkTokens.InkSurface,
    surface = RkTokens.InkSurface,
    surfaceVariant = RkTokens.InkSurfaceHigh,
    onPrimary = RkTokens.InkSurface,
    onBackground = RkTokens.InkOn,
    onSurface = RkTokens.InkOn,
    error = RkTokens.Red,
)

private val LightColors = lightColorScheme(
    primary = RkTokens.Blue,
    secondary = RkTokens.Magenta,
    tertiary = RkTokens.Orange,
)

/**
 * Wraps DevTools content in a ReduxKotlin-branded [MaterialTheme]. Defaults to dark — the UI-kit
 * default and best contrast for a developer overlay — or follows the host per [mode].
 *
 * @param mode theme mode from [InAppConfig].
 * @param systemDark whether the host is currently in dark mode (used when [mode] is SYSTEM).
 * @param content the themed content.
 */
@Composable
public fun ReduxKotlinDevToolsTheme(
    mode: DevToolsThemeMode,
    systemDark: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        DevToolsThemeMode.DARK -> true
        DevToolsThemeMode.LIGHT -> false
        DevToolsThemeMode.SYSTEM -> systemDark
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
```

> **Type/eyebrow polish (optional):** the design system specifies Roboto Flex (UI) + JetBrains Mono (all log/JSON/timing). Wiring custom `FontFamily`s in CMP needs font resources per target; for v1 use the platform default `Typography` and apply `FontFamily.Monospace` locally to log/JSON/timing `Text`s (done in the tab composables). A follow-up can bundle the brand fonts.

- [ ] **Step 3: Compile + commit**

Run: `./gradlew :redux-kotlin-devtools-inapp:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL.
```bash
git add redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/theme/
git commit -m "feat(devtools-inapp): ReduxKotlin brand theme + tokens"
```

---

## Task 6: `DevToolsController` — seed + follow the session

**Files:**
- Create: `.../inapp/DevToolsController.kt`

- [ ] **Step 1: Write the file**

```kotlin
package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.DevToolsSession
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.OutputRow

/**
 * Creates an [InAppModel] bound to a [DevToolsSession], honoring the backfill contract: it seeds from
 * [DevToolsSession.history] first, then collects [DevToolsSession.events], deduping by action id.
 *
 * @param session the session to observe (resolved by [ReduxDevToolsHost] from the hub).
 * @return a remembered model whose `state` drives the drawer.
 */
@Composable
internal fun rememberDevToolsController(session: DevToolsSession): InAppModel {
    val model = remember(session.id) { InAppModel() }
    LaunchedEffect(session.id) {
        // Backfill contract: snapshot history, then follow live — dedupe is in the model.
        model.seed(session.history())
        model.setOutputs(
            DevToolsHub.outputs().map { OutputRow(it.id, it.label, enabled = false, locked = false) } +
                OutputRow("inapp", "In-app drawer", enabled = true, locked = true),
        )
        session.events.collect { model.submit(it) }
    }
    return model
}
```

> **Output toggle wiring (resolve at implementation):** `OutputRow.enabled` for non-inapp outputs should reflect actual state. The core `DevToolsOutput` interface (Plan 1) has `start/stop` but no `isEnabled`; the remote `RemoteOutput` added `isRunning`. For v1 the OutputsTab toggle calls `output.start(session)`/`output.stop()` and optimistically flips the row; a follow-up can add an `isEnabled: Boolean` to `DevToolsOutput` for authoritative state. Keep the inapp row locked-on.

- [ ] **Step 2: Compile + commit**

Run: `./gradlew :redux-kotlin-devtools-inapp:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL.
```bash
git add redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/DevToolsController.kt
git commit -m "feat(devtools-inapp): controller seeds history then follows session"
```

---

## Task 7: Tab composables (Actions, State, Diff, Pipeline, Outputs)

Functional Compose realizations of the five tabs (UI kit = visual reference for spacing/animation polish). Mono font on all log/JSON/timing text; brand colors from `RkTokens`.

**Files:**
- Create: `.../inapp/ui/tabs/ActionsTab.kt`, `StateTab.kt`, `DiffTab.kt`, `PipelineTab.kt`, `OutputsTab.kt`

- [ ] **Step 1: `ActionsTab.kt`** (filterable log; tap to select)

```kotlin
package org.reduxkotlin.devtools.inapp.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.inapp.model.InAppState
import org.reduxkotlin.devtools.inapp.model.actionType
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/**
 * The Actions tab: a filter field over a tappable action log. Selecting a row drives the other tabs.
 *
 * @param state current UI state.
 * @param onFilter called when the filter text changes.
 * @param onSelect called when a row is tapped.
 */
@Composable
internal fun ActionsTab(state: InAppState, onFilter: (String) -> Unit, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.filter,
            onValueChange = onFilter,
            label = { Text("Filter actions") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.filteredActions, key = { it.actionId }) { a ->
                val selected = a.actionId == state.selected?.actionId
                Column(
                    Modifier.fillMaxWidth().clickable { onSelect(a.actionId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        "#${a.actionId}  ${actionType(a.action)}",
                        color = if (selected) RkTokens.BlueLight else RkTokens.Orange,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${a.timestampMillis} ms · ${a.diff.size} changes",
                        color = RkTokens.InkFaint,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: `StateTab.kt`** (recursive JSON tree, leaf colors by type)

```kotlin
package org.reduxkotlin.devtools.inapp.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/** The State tab: a recursive, expandable tree of the selected action's serialized state. */
@Composable
internal fun StateTab(state: JsonElement?) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (state == null) Text("No state yet.", color = RkTokens.InkDim)
        else JsonNode(key = "state", value = state, depth = 0)
    }
}

@Composable
private fun JsonNode(key: String?, value: JsonElement, depth: Int) {
    val pad = (depth * 14).dp
    when (value) {
        is JsonObject, is JsonArray -> {
            var open by remember { mutableStateOf(depth < 2) }
            val count = if (value is JsonArray) value.size else (value as JsonObject).size
            val summary = if (value is JsonArray) "Array($count)" else "{$count}"
            Text(
                text = (key?.let { "$it: " } ?: "") + (if (open) "▾ " else "▸ ") + summary,
                color = RkTokens.BlueLight,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = pad).clickable { open = !open },
            )
            if (open) {
                val entries: List<Pair<String, JsonElement>> = when (value) {
                    is JsonArray -> value.mapIndexed { i, v -> i.toString() to v }
                    is JsonObject -> value.entries.map { it.key to it.value }
                    else -> emptyList()
                }
                entries.forEach { (k, v) -> JsonNode(k, v, depth + 1) }
            }
        }
        is JsonPrimitive -> {
            val color = when {
                value.isString -> RkTokens.Green
                value.content == "true" || value.content == "false" -> RkTokens.Magenta
                value.content.toDoubleOrNull() != null -> RkTokens.Orange
                else -> RkTokens.InkDim
            }
            val text = if (value.isString) "\"${value.content}\"" else value.content
            Text("${key?.let { "$it: " } ?: ""}$text", color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = pad))
        }
    }
}
```

- [ ] **Step 3: `DiffTab.kt`** (added/changed/removed rows)

```kotlin
package org.reduxkotlin.devtools.inapp.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.DiffEntry
import org.reduxkotlin.devtools.DiffOp
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/** The Diff tab: added/changed/removed leaf paths for the selected action. */
@Composable
internal fun DiffTab(diff: List<DiffEntry>) {
    if (diff.isEmpty()) {
        Text("No changes for this action.", color = RkTokens.InkDim, modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn {
        items(diff) { e ->
            val (sym, color) = when (e.op) {
                DiffOp.ADDED -> "+" to RkTokens.Green
                DiffOp.REMOVED -> "−" to RkTokens.Red
                DiffOp.CHANGED -> "~" to RkTokens.Amber
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("$sym ${e.path}", color = color, fontFamily = FontFamily.Monospace)
                e.before?.let { Text("  - $it", color = RkTokens.Red, fontFamily = FontFamily.Monospace) }
                e.after?.let { Text("  + $it", color = RkTokens.Green, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}
```

- [ ] **Step 4: `PipelineTab.kt`** (static map lit by the selected trace)

```kotlin
package org.reduxkotlin.devtools.inapp.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.PipelineStructure
import org.reduxkotlin.devtools.PipelineTrace
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/** The Pipeline tab: the static node map, lighting nodes the selected action's [trace] traversed. */
@Composable
internal fun PipelineTab(structure: PipelineStructure?, trace: PipelineTrace?) {
    if (structure == null) {
        Text("No pipeline registered. Use devToolsMiddleware / devToolsCombineReducers.", color = RkTokens.InkDim, modifier = Modifier.padding(16.dp))
        return
    }
    val traceByNode = trace?.nodes?.associateBy { it.nodeId } ?: emptyMap()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        structure.nodes.forEach { node ->
            val nt = traceByNode[node.id]
            val lit = nt != null
            val color = when {
                nt?.changed == true -> RkTokens.Green
                lit -> RkTokens.BlueLight
                else -> RkTokens.InkFaint
            }
            val suffix = nt?.let { "  ·  ${it.durationNanos / 1000}µs${if (it.changed) "  changed" else ""}" } ?: ""
            Text("● ${node.label}$suffix", color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}
```

- [ ] **Step 5: `OutputsTab.kt`** (output rows + switches)

```kotlin
package org.reduxkotlin.devtools.inapp.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.inapp.model.OutputRow
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/** The Outputs tab: one integration, multiple outputs. In-app is locked on; remote/file toggle. */
@Composable
internal fun OutputsTab(outputs: List<OutputRow>, onToggle: (String, Boolean) -> Unit) {
    Column {
        outputs.forEach { o ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.label, color = RkTokens.InkOn)
                    if (o.locked) Text("in-process · always on", color = RkTokens.InkFaint)
                    else Text(if (o.enabled) "connected" else "off", color = if (o.enabled) RkTokens.Green else RkTokens.InkFaint)
                }
                Switch(checked = o.enabled, enabled = !o.locked, onCheckedChange = { onToggle(o.id, it) })
            }
        }
        Text(
            "Remote streaming leaves the device over WebSocket — off by default. The in-app drawer keeps all data in-process.",
            color = RkTokens.InkFaint,
            modifier = Modifier.padding(16.dp),
        )
    }
}
```

- [ ] **Step 6: Compile + commit**

Run: `./gradlew :redux-kotlin-devtools-inapp:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL.
```bash
git add redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/ui/tabs/
git commit -m "feat(devtools-inapp): five tab composables (Actions/State/Diff/Pipeline/Outputs)"
```

---

## Task 8: Drawer scaffold + triggers + `ReduxDevToolsHost`

The host overlays triggers + an adaptive drawer over the app content, inside the app's Compose tree. Open state lives in a process-visible holder so `ReduxDevTools.open()/close()` works programmatically.

**Files:**
- Create: `.../inapp/ui/Triggers.kt`, `.../inapp/ui/Drawer.kt`, `.../inapp/ReduxDevToolsHost.kt`

- [ ] **Step 1: `ui/Triggers.kt`**

```kotlin
package org.reduxkotlin.devtools.inapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.inapp.theme.RkTokens
import kotlin.math.roundToInt

/** Floating, draggable bubble; tap (without drag) opens the drawer. */
@Composable
internal fun BoxScopeBubble(badge: Int, onOpen: () -> Unit) {
    var offset by remember { mutableStateOf(IntOffset(40, 240)) }
    Box(
        Modifier
            .offset { offset }
            .size(56.dp)
            .clip(CircleShape)
            .background(RkTokens.InkSurfaceHigh)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onOpen() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset = IntOffset((offset.x + drag.x).roundToInt(), (offset.y + drag.y).roundToInt())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (badge > 0) badge.toString() else "RK", color = RkTokens.InkOn)
    }
}

/** Right-edge tab; tap opens the drawer. */
@Composable
internal fun BoxScopeEdgeTab(onOpen: () -> Unit) {
    Box(
        Modifier
            .size(width = 22.dp, height = 96.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .background(RkTokens.gradient)
            .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = RkTokens.InkOn)
    }
}
```

> **Alignment note:** these are placed by the caller inside a `Box` with `Modifier.align(...)` (bubble free-floating, edge tab `Alignment.CenterEnd`). Rename to plain `DevToolsBubble`/`EdgeTab` and let `ReduxDevToolsHost` apply alignment; the `BoxScope` prefix here is only a reminder they live in a `Box`.

- [ ] **Step 2: `ui/Drawer.kt`** (adaptive scaffold + tab bar + content switch)

```kotlin
package org.reduxkotlin.devtools.inapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fractionalWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.inapp.DevToolsTab
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.InAppState
import org.reduxkotlin.devtools.inapp.theme.RkTokens
import org.reduxkotlin.devtools.inapp.ui.tabs.ActionsTab
import org.reduxkotlin.devtools.inapp.ui.tabs.DiffTab
import org.reduxkotlin.devtools.inapp.ui.tabs.OutputsTab
import org.reduxkotlin.devtools.inapp.ui.tabs.PipelineTab
import org.reduxkotlin.devtools.inapp.ui.tabs.StateTab

/** The drawer: scrim + adaptive panel (bottom sheet on compact width, right panel on wide). */
@Composable
internal fun Drawer(open: Boolean, state: InAppState, model: InAppModel, onClose: () -> Unit, onToggleOutput: (String, Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        // Scrim
        AnimatedVisibility(open) {
            Box(Modifier.fillMaxSize().background(RkTokens.InkSurface.copy(alpha = 0.5f)).clickable { onClose() })
        }
        val panelModifier = if (wide) {
            Modifier.fillMaxHeight().fractionalWidth(0.42f).align(Alignment.CenterEnd)
        } else {
            Modifier.fillMaxWidth().fillMaxHeight(0.85f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = RkTokens.SheetCorner, topEnd = RkTokens.SheetCorner))
        }
        AnimatedVisibility(
            visible = open,
            enter = if (wide) slideInHorizontally { it } else slideInVertically { it },
            exit = if (wide) slideOutHorizontally { it } else slideOutVertically { it },
            modifier = panelModifier,
        ) {
            Column(Modifier.fillMaxSize().background(RkTokens.InkSurface)) {
                DrawerHeader(state, onClose)
                val tabs = DevToolsTab.entries
                TabRow(selectedTabIndex = tabs.indexOf(state.activeTab), containerColor = RkTokens.InkSurface) {
                    tabs.forEach { t ->
                        Tab(selected = t == state.activeTab, onClick = { model.setTab(t) }, text = { Text(t.name) })
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (state.activeTab) {
                        DevToolsTab.ACTIONS -> ActionsTab(state, model::setFilter) { model.select(it) }
                        DevToolsTab.STATE -> StateTab(state.selected?.state)
                        DevToolsTab.DIFF -> DiffTab(state.selected?.diff ?: emptyList())
                        DevToolsTab.PIPELINE -> PipelineTab(state.structure, state.selected?.let { state.tracesById[it.actionId] })
                        DevToolsTab.OUTPUTS -> OutputsTab(state.outputs, onToggleOutput)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(state: InAppState, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Redux DevTools", color = RkTokens.InkOn, style = MaterialTheme.typography.titleMedium)
        Text("  ·  ${state.actions.size} actions", color = RkTokens.InkDim, modifier = Modifier.weight(1f))
        Text("✕", color = RkTokens.InkDim, modifier = Modifier.clickable { onClose() }.padding(8.dp))
    }
}
```

> **API note:** `Modifier.fractionalWidth` is illustrative — use `Modifier.fillMaxWidth(0.42f)` (CMP has `fillMaxWidth(fraction)`/`fillMaxHeight(fraction)`). Replace `fractionalWidth(0.42f)` → `fillMaxWidth(0.42f)` and drop the bad import. `DevToolsTab.entries` requires Kotlin 1.9+ enum entries (available). The gradient tab indicator + cross-fade animations from the UI kit are polish — `TabRow`'s default indicator is acceptable for v1; a custom gradient indicator can follow.

- [ ] **Step 3: `ReduxDevToolsHost.kt`** (host + programmatic open/close)

```kotlin
package org.reduxkotlin.devtools.inapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.inapp.theme.ReduxKotlinDevToolsTheme
import org.reduxkotlin.devtools.inapp.ui.DevToolsBubble
import org.reduxkotlin.devtools.inapp.ui.Drawer
import org.reduxkotlin.devtools.inapp.ui.EdgeTab

/** Process-visible drawer open-state, so [ReduxDevTools] can toggle it from anywhere. */
internal object DrawerState {
    var open by mutableStateOf(false)
}

/** Programmatic control of the in-app drawer. */
public object ReduxDevTools {
    /** Opens the drawer. */
    public fun open() { DrawerState.open = true }

    /** Closes the drawer. */
    public fun close() { DrawerState.open = false }
}

/**
 * Wraps the app root, rendering [content] plus the DevTools overlay (triggers + drawer) inside the
 * app's own Compose tree — no system overlay window. Resolves the session to show from the hub.
 *
 * @param config drawer configuration.
 * @param content the host application content.
 */
@Composable
public fun ReduxDevToolsHost(config: InAppConfig = InAppConfig(), content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()

        val session = config.instanceId?.let { DevToolsHub.session(it) } ?: DevToolsHub.sessions().firstOrNull()
        if (session != null) {
            val model = rememberDevToolsController(session)
            val state by model.state.collectAsStateValue()

            if (!DrawerState.open) {
                if (DevToolsTrigger.BUBBLE in config.triggers) {
                    Box(Modifier.fillMaxSize()) { DevToolsBubble(badge = state.actions.size) { DrawerState.open = true } }
                }
                if (DevToolsTrigger.EDGE_SWIPE in config.triggers) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) { EdgeTab { DrawerState.open = true } }
                }
            }

            ReduxKotlinDevToolsTheme(mode = config.theme, systemDark = true) {
                Drawer(
                    open = DrawerState.open,
                    state = state,
                    model = model,
                    onClose = { DrawerState.open = false },
                    onToggleOutput = { id, on -> toggleOutput(session, id, on) },
                )
            }
        }
    }
}
```

> **Resolve at implementation:** (1) `collectAsStateValue()` is shorthand — use `androidx.compose.runtime.collectAsState()` on the `StateFlow` (`val state by model.state.collectAsState()`); fix the import. (2) `DevToolsBubble`/`EdgeTab` are the renamed trigger composables from Step 1. (3) `toggleOutput(session, id, on)` finds the hub output by id and calls `start(session)`/`stop()` then updates the model row optimistically (see the controller note); implement as a small private function in this file. (4) `systemDark = true` is a placeholder for "follow host" — wire a real `isSystemInDarkTheme()` per target if desired; DARK is the default so this only matters for SYSTEM mode.

- [ ] **Step 4: Compile (resolve the noted shims) + commit**

Run: `./gradlew :redux-kotlin-devtools-inapp:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL after fixing `collectAsState`, `fillMaxWidth(fraction)`, trigger names, and adding `toggleOutput`.
```bash
git add redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/ui/ \
        redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/ReduxDevToolsHost.kt
git commit -m "feat(devtools-inapp): adaptive drawer, triggers, ReduxDevToolsHost"
```

---

## Task 9: Compose smoke test (host renders content; programmatic open shows drawer)

**Files:**
- Test: `.../jvmTest/.../inapp/ReduxDevToolsHostTest.kt`

- [ ] **Step 1: Write the test** (Compose UI test on JVM/desktop)

```kotlin
package org.reduxkotlin.devtools.inapp

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.reduxkotlin.createStore
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.devTools
import org.junit.After
import org.junit.Rule
import org.junit.Test

class ReduxDevToolsHostTest {

    @get:Rule val rule = createComposeRule()

    @After fun cleanup() { DevToolsHub.reset(); ReduxDevTools.close() }

    private data class St(val n: Int = 0)

    @Test
    fun host_renders_app_content_and_opens_drawer_programmatically() {
        // A session must exist for the overlay to appear.
        createStore({ s: St, _ -> s }, St(), devTools(DevToolsConfig(name = "ui")))

        rule.setContent {
            ReduxDevToolsHost(InAppConfig()) { Text("app content") }
        }
        rule.onNodeWithText("app content").assertIsDisplayed()

        ReduxDevTools.open()
        rule.waitForIdle()
        rule.onNodeWithText("Redux DevTools").assertIsDisplayed()   // drawer header visible
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :redux-kotlin-devtools-inapp:jvmTest --tests '*ReduxDevToolsHostTest*' --console=plain`
Expected: PASS. (If the desktop Compose test harness needs a `@OptIn(ExperimentalTestApi)` or `runComposeUiTest` instead of the JUnit rule on this CMP version, switch to `runComposeUiTest { setContent { ... } }` — the assertions stay.)

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-devtools-inapp/src/jvmTest/kotlin/org/reduxkotlin/devtools/inapp/ReduxDevToolsHostTest.kt
git commit -m "test(devtools-inapp): host renders content + programmatic drawer open"
```

---

## Task 10: API dump for `-inapp`

- [ ] **Step 1: Dump + check**

Run: `./gradlew :redux-kotlin-devtools-inapp:apiDump --console=plain`
Then open `redux-kotlin-devtools-inapp/api/*.api`; confirm the public surface = `ReduxDevToolsHost`, `ReduxDevTools`, `InAppConfig`, `DevToolsTab`, `DevToolsTrigger`, `DevToolsThemeMode`, `ReduxKotlinDevToolsTheme`, `RkTokens`, `InAppModel`, `InAppState`, `OutputRow`, `actionType` — and that the `ui`/tab composables are `internal` (absent). Make any leaked helper `internal`, then re-dump.

- [ ] **Step 2: Commit**

```bash
git add redux-kotlin-devtools-inapp/api/
git commit -m "build(devtools-inapp): commit public API dump"
```

---

## Task 11: The `-inapp-noop` module (release-safe, zero overhead)

Identical app-facing API, empty bodies, deps = `redux-kotlin` + `compose.runtime` only. No core, no material3, no Ktor, no hub.

**Files:**
- Create: `redux-kotlin-devtools-inapp-noop/build.gradle.kts`
- Create: `redux-kotlin-devtools-inapp-noop/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/NoOp.kt`

- [ ] **Step 1: `build.gradle.kts`**

```kotlin
plugins {
    id("convention.library-mpp-loved")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("convention.publishing-mpp")
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
            namespace = "org.reduxkotlin.devtools.inapp"
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                implementation(compose.runtime)
            }
        }
    }
}
```

- [ ] **Step 2: `NoOp.kt`** (mirror the app-facing surface with inert bodies)

```kotlin
package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable
import org.reduxkotlin.Middleware
import org.reduxkotlin.Reducer
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.combineReducers

/** No-op replacement of `DevToolsConfig` for release builds. All fields inert. */
public data class DevToolsConfig(
    /** Inert. */ public val name: String = "redux-kotlin",
    /** Inert. */ public val instanceId: String? = null,
    /** Inert. */ public val maxAge: Int = 50,
    /** Inert. */ public val allowlist: List<String> = emptyList(),
    /** Inert. */ public val denylist: List<String> = emptyList(),
    /** Inert. */ public val logger: (String) -> Unit = {},
)

/** No-op enhancer: returns an identity store enhancer (no recording, no hub). */
public fun <State> devTools(config: DevToolsConfig = DevToolsConfig()): StoreEnhancer<State> =
    { storeCreator -> storeCreator }

/** No-op labeled middleware. */
public class NamedMiddleware<State> internal constructor(internal val middleware: Middleware<State>)

/** No-op labeled reducer. */
public class NamedReducer<State> internal constructor(internal val reducer: Reducer<State>)

/** Labels a middleware (label ignored in release). */
public fun <State> named(label: String, middleware: Middleware<State>): NamedMiddleware<State> = NamedMiddleware(middleware)

/** Labels a reducer (label ignored in release). */
public fun <State> named(label: String, reducer: Reducer<State>): NamedReducer<State> = NamedReducer(reducer)

/** No-op: behaves exactly like `applyMiddleware` with no instrumentation. */
public fun <State> devToolsMiddleware(config: DevToolsConfig, vararg middlewares: NamedMiddleware<State>): StoreEnhancer<State> =
    applyMiddleware(*middlewares.map { it.middleware }.toTypedArray())

/** No-op: behaves exactly like `combineReducers` with no instrumentation. */
public fun <State> devToolsCombineReducers(config: DevToolsConfig, vararg reducers: NamedReducer<State>): Reducer<State> =
    combineReducers(*reducers.map { it.reducer }.toTypedArray())

/** No-op trigger enum (kept for API parity). */
public enum class DevToolsTrigger { /** Inert. */ BUBBLE, /** Inert. */ EDGE_SWIPE }

/** No-op theme mode (kept for API parity). */
public enum class DevToolsThemeMode { /** Inert. */ SYSTEM, /** Inert. */ DARK, /** Inert. */ LIGHT }

/** No-op start tab (kept for API parity). */
public enum class DevToolsTab { /** Inert. */ ACTIONS, /** Inert. */ STATE, /** Inert. */ DIFF, /** Inert. */ PIPELINE, /** Inert. */ OUTPUTS }

/** No-op replacement of `InAppConfig`. */
public data class InAppConfig(
    /** Inert. */ public val triggers: Set<DevToolsTrigger> = setOf(DevToolsTrigger.BUBBLE, DevToolsTrigger.EDGE_SWIPE),
    /** Inert. */ public val startTab: DevToolsTab = DevToolsTab.ACTIONS,
    /** Inert. */ public val theme: DevToolsThemeMode = DevToolsThemeMode.DARK,
    /** Inert. */ public val instanceId: String? = null,
)

/** No-op programmatic control (does nothing in release). */
public object ReduxDevTools {
    /** No-op. */ public fun open() { /* no-op */ }
    /** No-op. */ public fun close() { /* no-op */ }
}

/** No-op host: renders [content] directly, with no overlay, no hub, no Compose-material3. */
@Composable
public fun ReduxDevToolsHost(config: InAppConfig = InAppConfig(), content: @Composable () -> Unit) {
    content()
}
```

- [ ] **Step 3: Add include (done in Task 1) + compile**

Run: `./gradlew :redux-kotlin-devtools-inapp-noop:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-devtools-inapp-noop/build.gradle.kts \
        redux-kotlin-devtools-inapp-noop/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/NoOp.kt
git commit -m "feat(devtools-inapp-noop): zero-overhead release sibling with identical app-facing API"
```

---

## Task 12: No-op parity test + API dump

Proves the no-op's app-facing surface matches `-inapp` so release builds always compile against it.

**Files:**
- Test: `redux-kotlin-devtools-inapp-noop/src/commonTest/kotlin/org/reduxkotlin/devtools/inapp/NoOpParityTest.kt`

- [ ] **Step 1: Write a usage-parity test** (the same call sites the docs show must compile + run inertly)

```kotlin
package org.reduxkotlin.devtools.inapp

import org.reduxkotlin.Store
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals

class NoOpParityTest {

    private data class St(val n: Int = 0)
    private object Inc
    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @Test
    fun the_documented_integration_compiles_and_runs_inertly() {
        val cfg = DevToolsConfig(name = "release")
        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        val root = devToolsCombineReducers(cfg, named("count", reducer))
        val store = createStore(root, St(), compose(devTools(cfg), devToolsMiddleware(cfg, named("logger", mw))))
        store.dispatch(Inc)
        ReduxDevTools.open(); ReduxDevTools.close()
        assertEquals(1, store.state.n)               // identical behavior, zero instrumentation
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :redux-kotlin-devtools-inapp-noop:jvmTest --tests '*NoOpParityTest*' --console=plain`
Expected: PASS. (`ReduxDevToolsHost` parity is compile-time — the `@Composable` signature matches `-inapp`; the host test in Task 9 covers the real one.)

- [ ] **Step 3: API dump for the no-op + confirm surface matches the app-facing subset**

Run: `./gradlew :redux-kotlin-devtools-inapp-noop:apiDump --console=plain`
Open both dumps and diff the **app-facing** symbols (`devTools`, `devToolsMiddleware`, `devToolsCombineReducers`, `named`×2, `NamedMiddleware`, `NamedReducer`, `DevToolsConfig`, `InAppConfig`, `DevToolsTab`, `DevToolsTrigger`, `DevToolsThemeMode`, `ReduxDevToolsHost`, `ReduxDevTools`): signatures must match between `-inapp` and `-inapp-noop`. (The `-inapp` module additionally exposes model/theme symbols the no-op omits — that is fine; release code only calls the app-facing subset.)

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-devtools-inapp-noop/api/ \
        redux-kotlin-devtools-inapp-noop/src/commonTest/kotlin/org/reduxkotlin/devtools/inapp/NoOpParityTest.kt
git commit -m "test(devtools-inapp-noop): API parity with -inapp + inert behavior"
```

---

## Task 13: Per-platform integration docs + sample (rollout phase 5)

**Files:**
- Create: `redux-kotlin-devtools-core/README.md` (or a top-level `docs/devtools.md`)
- Modify: `examples/todos/...` (optional sample wiring) — verify the example builds

- [ ] **Step 1: Write the integration recipe** (the copy-paste blocks)

Create `docs/devtools.md` documenting:
- The three artifacts: `-core` (always), `-remote` (optional WS), `-inapp` (debug) + `-inapp-noop` (release).
- Android variant wiring:
  ```kotlin
  debugImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp:<version>")
  releaseImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp-noop:<version>")
  // optional remote: debugImplementation("org.reduxkotlin:redux-kotlin-devtools-remote:<version>")
  ```
- The KMP/iOS/Desktop note: `debug/releaseImplementation` are Android-only; for other targets document dependency-substitution-by-build-type or a compile flag, and that a Gradle plugin to auto-wire real/no-op is future work.
- The integration:
  ```kotlin
  val cfg = DevToolsConfig(name = "appStore")
  val store = createStore(
      devToolsCombineReducers(cfg, named("todos", todosReducer), named("filter", filterReducer)),
      AppState(),
      compose(devTools(cfg), devToolsMiddleware(cfg, named("thunk", thunk), named("logger", logger))),
  )
  // at the app root:
  ReduxDevToolsHost { App() }
  ```
- The footguns: **give each store a distinct `DevToolsConfig.name`**; pass the **same** config to all three calls; sensitive state is visible in debug — use a custom `ValueSerializer` to redact; remote streaming leaves the device (off by default).

- [ ] **Step 2: Verify docs build / no broken links** (if added under `website/`, run the docs build)

Run (only if placed in `website/`): `cd website && yarn build`
Expected: success (`onBrokenLinks: 'throw'`). If `docs/devtools.md` is a plain repo doc, skip.

- [ ] **Step 3: Commit**

```bash
git add docs/devtools.md
git commit -m "docs(devtools): per-platform integration recipe + footgun guide"
```

---

## Task 14: Full gate

- [ ] **Step 1: Build the new modules end to end**

Run: `./gradlew :redux-kotlin-devtools-inapp:build :redux-kotlin-devtools-inapp-noop:build --console=plain`
Expected: BUILD SUCCESSFUL (compile, tests, detekt, apiCheck). If `iosSimulatorArm64Test` fails on an Xcode SDK error, that's environmental — confirm with `-x iosSimulatorArm64Test`.

- [ ] **Step 2: Whole-tree lint**

Run: `./gradlew detektAll --console=plain`
Expected: BUILD SUCCESSFUL — every new public symbol has KDoc; no `UndocumentedPublic*`.

- [ ] **Step 3: Clean tree**

Run: `git status --short`
Expected: empty.

---

## Self-Review (against spec)

**Spec "UI (`-inapp`)" coverage:**
- `ReduxDevToolsHost(config) { content }` wrapping the app root, overlay inside the app's Compose tree, no system window → Task 8. ✔
- Compose MP material3; adaptive (compact sheet / wide panel) via width breakpoint → Task 8 (`BoxWithConstraints`, 600.dp). ✔
- Five tabs Actions/State/Diff/Pipeline/**Outputs** with the documented semantics (filter+select; JSON tree w/ type colors; +/~/− diff; lit pipeline map w/ timing+changed; output switches, in-app locked) → Task 7. ✔
- Store-picker when >1 session → **partial**: host picks the first/`instanceId` session; a multi-session picker in the header is a noted follow-up (the model/hub expose `sessions()` to add it). Flagged.
- Triggers: edge-swipe + bubble default-on (config), programmatic `ReduxDevTools.open()/close()` → Tasks 8. Keyboard shortcut + shake deferred (shake already a spec non-goal; desktop hotkey is a small follow-up). ✔/noted.
- Theme from brand tokens → Task 5. ✔
- Backfill contract (history then follow, dedupe) → Tasks 4, 6. ✔

**Spec "Production-safety" coverage:**
- No-op release artifact, identical app-facing API, deps = `redux-kotlin` + `compose.runtime` only (no core/material3/Ktor/hub) → Tasks 11–12. ✔
- Parity guard (no-op compiles against the same call sites) → Task 12. ✔

**Placeholder scan:** Several **resolve-at-implementation notes** remain where a Compose API name needs the exact CMP-1.11 symbol (`collectAsState` vs the shorthand; `fillMaxWidth(fraction)` vs `fractionalWidth`; trigger composable rename; `toggleOutput` helper; desktop UI-test entrypoint). Each names the concrete fix; they exist because the precise CMP symbol is verified at compile time, not because the step is undecided. No bare TBDs; all logic (model, no-op, theme) is complete.

**Type consistency:** `InAppConfig`, `DevToolsTab`/`DevToolsTrigger`/`DevToolsThemeMode`, `InAppModel.{state,seed,submit,select,setFilter,setTab,setOutputs}`, `InAppState.{actions,selected,filteredActions,structure,tracesById,outputs}`, `OutputRow`, `rememberDevToolsController(session)`, `ReduxDevToolsHost(config){content}`, `ReduxDevTools.{open,close}`, `RkTokens`, `ReduxKotlinDevToolsTheme(mode,systemDark,content)` are used consistently across Tasks 2–9; the no-op (Task 11) redeclares the app-facing subset with matching signatures.

**Deferred (noted, not gaps):** multi-session store-picker UI; gradient tab indicator + UI-kit motion polish; brand font bundling; desktop keyboard-shortcut trigger; an `isEnabled` on `DevToolsOutput` for authoritative output state; a Gradle plugin to auto-wire debug/release artifacts.
