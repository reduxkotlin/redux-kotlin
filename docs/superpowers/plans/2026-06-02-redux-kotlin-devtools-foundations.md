# DevTools Standalone — Plan A: Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the shared foundations the standalone monitor needs — extract a reusable `redux-kotlin-devtools-ui` module from `-inapp`, add a multi-store `StoreRegistryModel`, and add a kotlinx.serialization `ValueSerializer` tier to `-core` — without changing any in-app behavior.

**Architecture:** Move the framework-agnostic + reusable-Compose pieces (`InAppModel`/`InAppState`, the five tab composables, theme, the `DevToolsTab`/`DevToolsThemeMode` enums) out of `redux-kotlin-devtools-inapp` into a new `redux-kotlin-devtools-ui` library (FQNs unchanged → no consumer breakage; tab composables promoted `internal → public`). `-inapp` then depends on `-ui` and keeps only the drawer host/triggers/controller. Add `StoreRegistryModel` (pure, in `-ui`) that aggregates many `InAppModel`s into one selectable/filterable/merged view. Add `KotlinxValueSerializer` in `-core` so non-JVM apps stream structured state.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11 (runtime/foundation/material3/ui), kotlinx-serialization-json, kotlinx-coroutines. detekt + `explicitApi()` + ABI validation (`updateKotlinAbi`/`checkKotlinAbi`).

**Source spec:** `docs/superpowers/specs/2026-06-02-redux-kotlin-devtools-standalone-design.md`. **Builds on:** the completed `-core`/`-inapp` (this branch).

**Conventions (obey):**
- Pre-commit hook runs `detektAll --auto-correct` and rewrites formatting — after each commit run `git status --short` and re-stage+`commit --amend --no-edit` until clean. **Never** `--no-verify`.
- `explicitApi()` on: every `public` symbol needs an explicit modifier + KDoc. detekt's `OutdatedDocumentation` rejects `@param`/`@property` that don't match exactly — if it fires, fold into prose (keep a KDoc).
- After any public-API change run `./gradlew :<module>:updateKotlinAbi` and commit the `api/` dump; `checkKotlinAbi` is the gate.
- Use explicit `git add <paths>` — never `git add -A` (untracked design-system assets exist under `docs/`).
- Examples/tests need no KDoc.

---

## File structure

### New module `redux-kotlin-devtools-ui` (package keeps `org.reduxkotlin.devtools.inapp.*`)
- `build.gradle.kts` — CMP library (runtime/foundation/material3/ui) + serialization + coroutines; **drops `linuxX64`/`mingwX64`** (no material3 there), like `-inapp`.
- `commonMain/.../inapp/DevToolsTab.kt` — `DevToolsTab` + `DevToolsThemeMode` (moved out of `-inapp`'s `InAppConfig.kt`).
- `commonMain/.../inapp/model/InAppState.kt`, `model/InAppModel.kt` (moved verbatim).
- `commonMain/.../inapp/theme/Tokens.kt`, `theme/ReduxKotlinDevToolsTheme.kt` (moved verbatim).
- `commonMain/.../inapp/ui/tabs/{ActionsTab,StateTab,DiffTab,PipelineTab,OutputsTab}.kt` (moved; `internal → public`).
- `commonMain/.../inapp/model/StoreRegistryModel.kt` — **new**: multi-store aggregation.

### Modified `redux-kotlin-devtools-inapp`
- `build.gradle.kts` — `api(project(":redux-kotlin-devtools-ui"))`; drop the now-moved deps it no longer needs directly (keep compose for the host/drawer).
- `InAppConfig.kt` — keep `InAppConfig` + `DevToolsTrigger`; **remove** `DevToolsTab`/`DevToolsThemeMode` (now imported from `-ui`).
- (unchanged sources) `DevToolsController.kt`, `ReduxDevToolsHost.kt`, `ui/Drawer.kt`, `ui/Triggers.kt` — recompile against `-ui`.
- regenerate `api/` (tab composables now public live in `-ui`; `-inapp` surface shrinks).

### Modified `redux-kotlin-devtools-core`
- `commonMain/.../devtools/KotlinxValueSerializer.kt` — **new**: a `ValueSerializer` backed by kotlinx.serialization.

### Modified `settings.gradle.kts`
- add `":redux-kotlin-devtools-ui"`.

---

## Task 1: Scaffold `redux-kotlin-devtools-ui`

**Files:**
- Create: `redux-kotlin-devtools-ui/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Create dirs under `redux-kotlin-devtools-ui/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/{model,theme,ui/tabs}` and `.../commonTest/.../inapp/model`

- [ ] **Step 1: Add the include**

In `settings.gradle.kts`, add to the `include(...)` block (next to the other devtools modules):
```kotlin
    ":redux-kotlin-devtools-ui",
```

- [ ] **Step 2: Create `redux-kotlin-devtools-ui/build.gradle.kts`** (mirror `redux-kotlin-devtools-inapp`'s, including the linux/mingw removal and the macOS compose flag already in `gradle.properties`)

```kotlin
plugins {
    id("convention.library-mpp-loved")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
    // Compose material3/foundation/ui are not published for linuxX64/mingwX64 (CMP 1.11).
    targets.removeIf { it.name == "linuxX64" || it.name == "mingwX64" }

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
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
```
> If `targets.removeIf {...}` placement or the macOS compose-enable flag differs from how `-inapp` does it, mirror `-inapp/build.gradle.kts` exactly (it compiles today).

- [ ] **Step 3: Create the dirs**

```bash
mkdir -p redux-kotlin-devtools-ui/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/model \
         redux-kotlin-devtools-ui/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/theme \
         redux-kotlin-devtools-ui/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/ui/tabs \
         redux-kotlin-devtools-ui/src/commonTest/kotlin/org/reduxkotlin/devtools/inapp/model
```

- [ ] **Step 4: Verify it configures**

Run: `./gradlew :redux-kotlin-devtools-ui:help --console=plain`
Expected: BUILD SUCCESSFUL (empty module configures).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts redux-kotlin-devtools-ui/build.gradle.kts
git commit -m "build(devtools-ui): scaffold shared UI module"
```

---

## Task 2: Move model + theme + tabs into `-ui`; repoint `-inapp`

Mechanical move (history-preserving). Package FQNs do **not** change, so consumers are unaffected; the only API change is the tab composables becoming `public`.

**Files:**
- Move (git mv): `model/InAppState.kt`, `model/InAppModel.kt`, `theme/Tokens.kt`, `theme/ReduxKotlinDevToolsTheme.kt`, `ui/tabs/{ActionsTab,StateTab,DiffTab,PipelineTab,OutputsTab}.kt` (+ their tests) from `redux-kotlin-devtools-inapp` to `redux-kotlin-devtools-ui`.
- Split: `redux-kotlin-devtools-inapp/.../inapp/InAppConfig.kt` → move `DevToolsTab` + `DevToolsThemeMode` into a new `redux-kotlin-devtools-ui/.../inapp/DevToolsTab.kt`.
- Modify: both modules' `build.gradle.kts`.

- [ ] **Step 1: Move the verbatim files**

```bash
SRC=redux-kotlin-devtools-inapp/src
DST=redux-kotlin-devtools-ui/src
PKG=kotlin/org/reduxkotlin/devtools/inapp
git mv $SRC/commonMain/$PKG/model/InAppState.kt            $DST/commonMain/$PKG/model/InAppState.kt
git mv $SRC/commonMain/$PKG/model/InAppModel.kt            $DST/commonMain/$PKG/model/InAppModel.kt
git mv $SRC/commonMain/$PKG/theme/Tokens.kt               $DST/commonMain/$PKG/theme/Tokens.kt
git mv $SRC/commonMain/$PKG/theme/ReduxKotlinDevToolsTheme.kt $DST/commonMain/$PKG/theme/ReduxKotlinDevToolsTheme.kt
for t in ActionsTab StateTab DiffTab PipelineTab OutputsTab; do
  git mv "$SRC/commonMain/$PKG/ui/tabs/$t.kt" "$DST/commonMain/$PKG/ui/tabs/$t.kt"
done
git mv $SRC/commonTest/$PKG/model/InAppModelTest.kt        $DST/commonTest/$PKG/model/InAppModelTest.kt
```
Verify nothing else remains that should move: `ls redux-kotlin-devtools-inapp/src/commonMain/$PKG/model redux-kotlin-devtools-inapp/src/commonMain/$PKG/theme redux-kotlin-devtools-inapp/src/commonMain/$PKG/ui/tabs 2>/dev/null` — should be empty/absent.

- [ ] **Step 2: Promote the five tab composables to `public`**

In each moved `ui/tabs/*.kt`, change the composable's visibility from `internal fun` to `public fun` and add a one-line KDoc to each public composable (detekt requires it). Example for `ActionsTab.kt`:
```kotlin
/** The Actions tab: a filterable, tappable action log; selecting a row drives the other tabs. */
@Composable
public fun ActionsTab(state: InAppState, onFilter: (String) -> Unit, onSelect: (Int) -> Unit) {
```
Do the same for `StateTab(state: JsonElement?)`, `DiffTab(diff: List<DiffEntry>)`, `PipelineTab(structure, trace)`, `OutputsTab(outputs, onToggle)` — KDoc + `public`. Leave private helpers (`JsonNode`, etc.) `private`.

- [ ] **Step 3: Move `DevToolsTab` + `DevToolsThemeMode` into `-ui`**

Create `redux-kotlin-devtools-ui/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/DevToolsTab.kt`:
```kotlin
package org.reduxkotlin.devtools.inapp

/** Drawer theme mode. */
public enum class DevToolsThemeMode {
    /** Follow the host app's light/dark setting. */
    SYSTEM,

    /** Always dark (the UI-kit default — best contrast for a developer tool). */
    DARK,

    /** Always light. */
    LIGHT,
}

/** Which inspector tab is shown. */
public enum class DevToolsTab {
    /** The action log. */ ACTIONS,
    /** The state tree. */ STATE,
    /** The per-action diff. */ DIFF,
    /** The pipeline map. */ PIPELINE,
    /** The outputs list. */ OUTPUTS,
}
```
Then edit `redux-kotlin-devtools-inapp/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/InAppConfig.kt` to **remove** the `DevToolsThemeMode` and `DevToolsTab` enum declarations (keep `DevToolsTrigger` and `InAppConfig`). `InAppConfig` still references `DevToolsTab`/`DevToolsThemeMode` — they now resolve from `-ui` (same package, via the `api` dependency). No import line needed (same package).

- [ ] **Step 4: Repoint the build files**

Edit `redux-kotlin-devtools-inapp/build.gradle.kts` `commonMain` dependencies: replace `api(project(":redux-kotlin-devtools-core"))` with `api(project(":redux-kotlin-devtools-ui"))` (which transitively `api`s `-core`). Keep the compose deps (the host/drawer/triggers still use them). Keep the `targets.removeIf { linux/mingw }` and serialization plugin/dep as-is.

- [ ] **Step 5: Compile both modules**

Run: `./gradlew :redux-kotlin-devtools-ui:compileKotlinJvm :redux-kotlin-devtools-inapp:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL. (`-inapp`'s `Drawer.kt` imports the tab composables from the same FQNs — now satisfied by `-ui`. `ReduxKotlinDevToolsTheme` + `InAppModel` resolve from `-ui`.) If `Drawer.kt`/`ReduxDevToolsHost.kt` had explicit imports of the tabs, they remain valid (same package path).

- [ ] **Step 6: Run the moved test + the in-app tests**

Run: `./gradlew :redux-kotlin-devtools-ui:jvmTest :redux-kotlin-devtools-inapp:jvmTest --console=plain`
Expected: PASS (the moved `InAppModelTest` (6) under `-ui`; the `-inapp` host smoke test still passes).

- [ ] **Step 7: Regenerate both ABI dumps**

Run: `./gradlew :redux-kotlin-devtools-ui:updateKotlinAbi :redux-kotlin-devtools-inapp:updateKotlinAbi --console=plain`
Confirm: `-ui`'s dump now lists `InAppModel`, `InAppState`, `OutputRow`, `actionType`, the five tab composables, `RkTokens`, `ReduxKotlinDevToolsTheme`, `DevToolsTab`, `DevToolsThemeMode`; `-inapp`'s dump shrinks to `ReduxDevToolsHost`, `ReduxDevTools`, `InAppConfig`, `DevToolsTrigger` (+ whatever host symbols were already public). Run `./gradlew :redux-kotlin-devtools-ui:checkKotlinAbi :redux-kotlin-devtools-inapp:checkKotlinAbi --console=plain` → SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add redux-kotlin-devtools-ui redux-kotlin-devtools-inapp
git commit -m "refactor(devtools): extract redux-kotlin-devtools-ui (shared model/theme/tabs)"
```
Verify clean tree (re-amend if the hook rewrote formatting).

---

## Task 3: `StoreRegistryModel` (multi-store aggregation, TDD)

A pure, Compose-free holder that aggregates many `InAppModel`s into one selectable view. Selection is `All` / `Subset(ids)` / `One(id)`; the merged action stream interleaves the selected stores' actions by `timestampMillis`, each row tagged with its store id + name.

**Files:**
- Create: `redux-kotlin-devtools-ui/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/model/StoreRegistryModel.kt`
- Test: `redux-kotlin-devtools-ui/src/commonTest/kotlin/org/reduxkotlin/devtools/inapp/model/StoreRegistryModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools.inapp.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DevToolsEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreRegistryModelTest {

    private fun action(id: Int, type: String, ts: Long) = DevToolsEvent.ActionRecorded(
        actionId = id,
        action = buildJsonObject { put("type", type) },
        state = buildJsonObject { put("n", id) },
        diff = emptyList(),
        timestampMillis = ts,
        isExcess = false,
    )

    private fun model(vararg evs: DevToolsEvent.ActionRecorded): InAppModel =
        InAppModel(maxActions = 50).apply { evs.forEach { submit(it) } }

    @Test
    fun registers_stores_and_lists_them() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        assertEquals(listOf("a", "b"), reg.state.value.stores.map { it.ref.id })
    }

    @Test
    fun all_mode_merges_actions_by_timestamp_with_store_tags() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10), action(2, "A2", 30)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.selectAll()
        val rows = reg.state.value.mergedRows
        assertEquals(listOf("a" to "A1", "b" to "B1", "a" to "A2"), rows.map { it.storeId to actionTypeOf(it) })
    }

    @Test
    fun subset_filters_to_selected_stores() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.select(setOf("b"))
        assertEquals(listOf("b"), reg.state.value.mergedRows.map { it.storeId })
    }

    @Test
    fun one_mode_shows_a_single_store_and_marks_not_merged() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.focus("a")
        val s = reg.state.value
        assertEquals(setOf("a"), s.selectedIds)
        assertEquals(false, s.merged)
        assertEquals(listOf("a"), s.mergedRows.map { it.storeId })
    }
}

// test helper
private fun actionTypeOf(row: StoreActionRow): String =
    (row.event.action as kotlinx.serialization.json.JsonObject)["type"].toString().trim('"')
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-ui:jvmTest --tests '*StoreRegistryModelTest*' --console=plain`
Expected: FAIL — `StoreRegistryModel`/`StoreRef`/`StoreActionRow` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools.inapp.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.reduxkotlin.devtools.DevToolsEvent

/** Stable identity of a store in the registry. */
public data class StoreRef(
    /** Unique key (in-app: `instanceId`; standalone: `clientId + storeInstanceId`). */
    public val id: String,
    /** Display name shown on rows/badges. */
    public val name: String,
)

/** One row of the merged action log, tagged with the store it came from. */
public data class StoreActionRow(
    /** The store's id. */
    public val storeId: String,
    /** The store's display name (for the badge). */
    public val storeName: String,
    /** The recorded action. */
    public val event: DevToolsEvent.ActionRecorded,
)

/** A registered store and its current snapshot. */
public data class StoreEntry(
    /** The store's identity. */
    public val ref: StoreRef,
    /** The store's UI state. */
    public val state: InAppState,
)

/** Immutable aggregate view across all registered stores. */
public data class StoreRegistryState(
    /** All registered stores, in registration order. */
    public val stores: List<StoreEntry> = emptyList(),
    /** Ids currently included in the view. */
    public val selectedIds: Set<String> = emptySet(),
) {
    /** `true` when more than one store is selected (drives badges + "merged by time"). */
    public val merged: Boolean get() = selectedIds.size > 1

    /** Selected stores' actions interleaved by `timestampMillis`, each tagged with its store. */
    public val mergedRows: List<StoreActionRow>
        get() = stores
            .filter { it.ref.id in selectedIds }
            .flatMap { e -> e.state.actions.map { StoreActionRow(e.ref.id, e.ref.name, it) } }
            .sortedWith(compareBy({ it.event.timestampMillis }, { it.storeId }, { it.event.actionId }))
}

/**
 * Aggregates many [InAppModel]s into one selectable/filterable/merged view. Pure and Compose-free,
 * so it is unit-tested directly and reused by both the in-app drawer (keyed by `instanceId`) and the
 * standalone monitor (keyed by `clientId + storeInstanceId`). The host registers/updates stores; the
 * model recomputes the aggregate [state].
 */
public class StoreRegistryModel {
    private data class Slot(val ref: StoreRef, val model: InAppModel)

    private val slots = LinkedHashMap<String, Slot>()
    private val _state = MutableStateFlow(StoreRegistryState())

    /** Observable aggregate view. */
    public val state: StateFlow<StoreRegistryState> = _state

    /** Registers (or replaces) the store [ref] backed by [model]; auto-selects the first store. */
    public fun put(ref: StoreRef, model: InAppModel) {
        slots[ref.id] = Slot(ref, model)
        if (_state.value.selectedIds.isEmpty()) _state.value = _state.value.copy(selectedIds = setOf(ref.id))
        recompute()
    }

    /** Removes a store. */
    public fun remove(id: String) {
        slots.remove(id)
        _state.value = _state.value.copy(selectedIds = _state.value.selectedIds - id)
        if (_state.value.selectedIds.isEmpty()) slots.keys.firstOrNull()?.let { focus(it) }
        recompute()
    }

    /** Selects exactly the given store ids (filter to a subset). */
    public fun select(ids: Set<String>) {
        _state.value = _state.value.copy(selectedIds = ids.ifEmpty { slots.keys.take(1).toSet() })
    }

    /** Selects exactly one store (solo view). */
    public fun focus(id: String): Unit = select(setOf(id))

    /** Selects all registered stores ("view all"). */
    public fun selectAll(): Unit = select(slots.keys.toSet())

    private fun recompute() {
        _state.value = _state.value.copy(
            stores = slots.values.map { StoreEntry(it.ref, it.model.state.value) },
        )
    }
}
```
> **Snapshot note:** `recompute()` reads each `InAppModel.state.value` at registration/structural change. The Compose layer (Plan C) collects each model's `state` flow and calls a refresh on change; for the headless unit tests, the models are pre-populated before `put`, so the snapshot is correct. Keep `StoreRegistryModel` pure — it does not subscribe to flows itself (the host wires updates). Add an explicit `refresh()` that re-runs `recompute()` for the host to call on any child-model change:
> ```kotlin
>     /** Re-reads child models' current state into the aggregate (host calls on any child change). */
>     public fun refresh(): Unit = recompute()
> ```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-ui:jvmTest --tests '*StoreRegistryModelTest*' --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-ui/src/commonMain/kotlin/org/reduxkotlin/devtools/inapp/model/StoreRegistryModel.kt \
        redux-kotlin-devtools-ui/src/commonTest/kotlin/org/reduxkotlin/devtools/inapp/model/StoreRegistryModelTest.kt
git commit -m "feat(devtools-ui): add multi-store StoreRegistryModel"
```

---

## Task 4: `KotlinxValueSerializer` tier in `-core` (TDD)

A `ValueSerializer` that serializes via kotlinx.serialization, so iOS/native/JS apps stream structured state (not `toString` blobs). It resolves a `KSerializer` for each value from the `Json`'s `serializersModule` via `getContextual(value::class)` — multiplatform, no reflection — and falls back to `ToStringValueSerializer` when no contextual serializer is registered.

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/KotlinxValueSerializer.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/KotlinxValueSerializerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinxValueSerializerTest {

    @Serializable
    private data class St(val n: Int, val label: String)

    private val json = Json {
        serializersModule = SerializersModule { contextual(St::class, St.serializer()) }
    }

    @Test
    fun serializes_a_registered_type_structurally() {
        val ser = KotlinxValueSerializer(json)
        val el = ser.toJson(St(n = 3, label = "hi"))
        assertTrue(el is JsonObject)
        assertEquals(JsonPrimitive(3), (el as JsonObject)["n"])
        assertEquals(JsonPrimitive("hi"), el["label"])
    }

    @Test
    fun falls_back_to_toString_for_unregistered_types() {
        val ser = KotlinxValueSerializer(json)
        // Int has no contextual registration here → fallback to ToString tier (a JSON primitive).
        val el = ser.toJson(42)
        assertEquals(JsonPrimitive("42"), el)
    }

    @Test
    fun null_serializes_to_json_null() {
        val ser = KotlinxValueSerializer(json)
        assertEquals(kotlinx.serialization.json.JsonNull, ser.toJson(null))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*KotlinxValueSerializerTest*' --console=plain`
Expected: FAIL — `KotlinxValueSerializer` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * A [ValueSerializer] backed by kotlinx.serialization, for structured cross-platform state/actions.
 *
 * For each value it resolves a serializer from [json]'s `serializersModule` via
 * `getContextual(value::class)` — multiplatform and reflection-free — and encodes it to a
 * [JsonElement]. When no contextual serializer is registered for a value's class, it falls back to
 * [ToStringValueSerializer] (a JSON primitive). Register your state and (sealed) action base types
 * contextually/polymorphically in [json] for a rich State tree on iOS/native/JS:
 *
 * ```
 * val json = Json { serializersModule = SerializersModule {
 *     contextual(AppState::class, AppState.serializer())
 *     polymorphic(Action::class) { /* subclasses */ }
 * } }
 * DevToolsConfig(serializer = KotlinxValueSerializer(json))
 * ```
 *
 * @param json the configured Json whose `serializersModule` holds the contextual/polymorphic serializers.
 */
public class KotlinxValueSerializer(private val json: Json) : ValueSerializer {
    @Suppress("UNCHECKED_CAST")
    override fun toJson(value: Any?): JsonElement {
        if (value == null) return JsonNull
        val contextual = json.serializersModule.getContextual(value::class) as KSerializer<Any>?
        return if (contextual != null) {
            runCatching { json.encodeToJsonElement(contextual, value) }
                .getOrElse { ToStringValueSerializer.toJson(value) }
        } else {
            ToStringValueSerializer.toJson(value)
        }
    }
}
```
> **Grounded against `-core`:** `ValueSerializer.toJson(value: Any?): JsonElement` and `object ToStringValueSerializer : ValueSerializer` already exist in `Serialization.kt`. `Json.serializersModule.getContextual(KClass)` is multiplatform. `value::class` is multiplatform. If `getContextual`'s signature in this kotlinx-serialization version needs a `KClass<Any>` cast, adjust the cast — keep the lookup-then-fallback behavior. Do NOT change the `ValueSerializer` interface.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*KotlinxValueSerializerTest*' --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Full `-core` test + API dump**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --console=plain` → all pass.
Run: `./gradlew :redux-kotlin-devtools-core:updateKotlinAbi --console=plain` → dump gains `KotlinxValueSerializer`. Then `:checkKotlinAbi` → SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/KotlinxValueSerializer.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/KotlinxValueSerializerTest.kt \
        redux-kotlin-devtools-core/api/
git commit -m "feat(devtools-core): kotlinx.serialization ValueSerializer tier"
```

---

## Task 5: Retro-fit the in-app drawer store-picker on `StoreRegistryModel`

The drawer currently shows only the first hub session. Wire it to `StoreRegistryModel` so multiple stores are selectable + an "All" merged log works — validating `StoreRegistryModel` in a real UI. Minimal: a header store-picker dropdown ("All stores" + each store) feeding the existing tabs; per-row store badge in merged mode (the `ActionsTab` already shows `actionType`; add the badge there behind a flag).

**Files:**
- Modify: `redux-kotlin-devtools-inapp/.../DevToolsController.kt`, `ReduxDevToolsHost.kt`, `ui/Drawer.kt`
- Test: `redux-kotlin-devtools-inapp/src/jvmTest/.../inapp/MultiStoreDrawerTest.kt`

- [ ] **Step 1: Extend the controller to build a `StoreRegistryModel` over all hub sessions**

In `DevToolsController.kt`, add a sibling to `rememberDevToolsController` that aggregates every hub session:
```kotlin
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.inapp.model.StoreRegistryModel
import org.reduxkotlin.devtools.inapp.model.StoreRef

/**
 * Builds a [StoreRegistryModel] over every session in the hub (one `InAppModel` per store), seeding
 * + following each session's feed. Drives the drawer's store-picker and "All stores" merged view.
 */
@Composable
internal fun rememberStoreRegistry(): StoreRegistryModel {
    val registry = remember { StoreRegistryModel() }
    val sessions = DevToolsHub.sessions()
    sessions.forEach { session ->
        val model = rememberDevToolsController(session) // seeds history + follows events
        LaunchedEffect(session.id) {
            registry.put(StoreRef(session.id, session.id), model)
            model.state.collect { registry.refresh() }
        }
    }
    return registry
}
```
> `session.id` is the store name/instanceId (the in-app `StoreRef.name` = the id here; the standalone supplies richer labels). `rememberDevToolsController` already exists and seeds+follows one session.

- [ ] **Step 2: Use the registry in `ReduxDevToolsHost` + add a picker to the drawer header**

In `ReduxDevToolsHost.kt`, when `config.instanceId == null`, use `rememberStoreRegistry()` and pass its `state` to `Drawer`; when `config.instanceId != null`, keep the single-session path (unchanged behavior for an explicitly-targeted store). In `Drawer.kt`'s header, add a minimal store-picker: a clickable label showing the active store name (or "All stores"); a dropdown listing "All stores" + each `StoreRegistryState.stores` entry; selecting calls `registry.focus(id)` / `registry.selectAll()`. In the action log, when `state.merged`, prefix each row with a mono store-name chip (reuse `RkTokens`); hide it otherwise. Drive the tabs from the selected row's store (in merged mode) or the single store.
> Keep this minimal and behavior-preserving for the single-store case (the common one): if the hub has exactly one session, the picker shows that store with no "All"/badges. The detailed dock UX lives in the standalone (Plan C); here it's a compact picker + merged log.

- [ ] **Step 3: Write a Compose test (multi-store picker + merged log)**

```kotlin
package org.reduxkotlin.devtools.inapp

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsHub
import kotlin.test.AfterTest
import kotlin.test.Test

class MultiStoreDrawerTest {

    @AfterTest fun cleanup() { DevToolsHub.reset(); ReduxDevTools.close() }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun two_stores_both_appear_in_the_picker() = runComposeUiTest {
        // Two sessions in the hub → two selectable stores.
        DevToolsHub.createSession(DevToolsConfig(name = "StoreA"))
        DevToolsHub.createSession(DevToolsConfig(name = "StoreB"))

        setContent { ReduxDevToolsHost(InAppConfig()) { Text("app") } }
        ReduxDevTools.open()
        waitForIdle()
        // Open the picker and assert both stores are listed.
        onNodeWithText("All stores").assertIsDisplayed()
    }
}
```
> If asserting picker contents needs a click to open the dropdown, perform `onNodeWithText(<active store>).performClick()` first. Keep the assertion meaningful (both stores reachable); adapt selectors to the actual header labels you implement.

- [ ] **Step 4: Run tests**

Run: `./gradlew :redux-kotlin-devtools-inapp:jvmTest --console=plain`
Expected: PASS (host smoke test + the new multi-store test).

- [ ] **Step 5: API dump + commit**

```bash
./gradlew :redux-kotlin-devtools-inapp:updateKotlinAbi --console=plain
git add redux-kotlin-devtools-inapp
git commit -m "feat(devtools-inapp): multi-store picker + merged log via StoreRegistryModel"
```

---

## Task 6: Final gate

- [ ] **Step 1: Build + test the three modules**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest :redux-kotlin-devtools-ui:jvmTest :redux-kotlin-devtools-inapp:jvmTest :redux-kotlin-devtools-inapp-noop:jvmTest --console=plain`
Expected: all PASS. (`-inapp-noop` is unaffected but confirm it still compiles against the unchanged app-facing API.)

- [ ] **Step 2: ABI + native compile across the new/changed modules**

Run: `./gradlew :redux-kotlin-devtools-core:checkKotlinAbi :redux-kotlin-devtools-ui:checkKotlinAbi :redux-kotlin-devtools-inapp:checkKotlinAbi --console=plain`
Expected: BUILD SUCCESSFUL (klib native compiles too). If `iosSimulatorArm64Test`/browser tests are pulled in and fail on env, that's host-limited — re-run with the failing native/browser test excluded and confirm the rest is green.

- [ ] **Step 3: Whole-tree lint**

Run: `./gradlew detektAll --console=plain` → BUILD SUCCESSFUL.

- [ ] **Step 4: Confirm clean tree**

Run: `git status --short` → empty.

---

## Self-Review (against the spec)

**Spec coverage (Plan A scope = rollout steps 1–2):**
- "Extract `redux-kotlin-devtools-ui` (move model/theme, promote tabs public, repoint `-inapp`); regenerate ABI" → Tasks 1–2. ✔
- "`StoreRegistryModel` (selection All/Client/Store/Subset + merged-by-timestamp action view with per-row store identity)" → Task 3 (`All`/`Subset`/`focus`; `mergedRows` sorted by `timestampMillis`; rows tagged with store id+name). ✔ (The "Client" grouping layer is a *standalone* rail concern — Plan C; the registry is keyed by opaque `StoreRef.id`, which the standalone sets to `clientId+storeInstanceId`.)
- "Retro-fit the in-app drawer's store-picker on top of `StoreRegistryModel`" → Task 5. ✔
- "kotlinx.serialization `ValueSerializer` tier" → Task 4. ✔
- "single-store mode hides the badge" → Task 3 (`merged == false` when ≤1 selected) + Task 5 (badge behind `merged`). ✔

**Deferred to later plans (intentional):** the wire protocol/handshake, `-bridge`, native target expansion → **Plan B**; the standalone app (server ingestion, dock UI, P0 features, web, docs) → **Plan C**.

**Placeholder scan:** no TBDs. The notes (build-file mirroring, `getContextual` cast, picker selector adaptation) are concrete fallbacks for exact-symbol/exact-label confirmations the executor makes at compile time, not unfinished work.

**Type consistency:** `StoreRef(id,name)`, `StoreActionRow(storeId,storeName,event)`, `StoreEntry(ref,state)`, `StoreRegistryState.{stores,selectedIds,merged,mergedRows}`, `StoreRegistryModel.{state,put,remove,select,focus,selectAll,refresh}`, `KotlinxValueSerializer(json).toJson`, `rememberStoreRegistry()` are used consistently across Tasks 3–5.
