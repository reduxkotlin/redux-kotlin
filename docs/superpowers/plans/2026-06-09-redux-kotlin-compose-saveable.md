# redux-kotlin-compose-saveable Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `redux-kotlin-compose-saveable` companion module whose `Store<S>.rememberSaveableState(saver)` persists a minimal serialized snapshot of store state across Android rotation and process death, rehydrating via a single dispatched action.

**Architecture:** A `StateSaver` value type (serializer + `save` projection + `restore` action factory, no Compose state) is consumed by a `@Composable` anchor that drives Compose's `SaveableStateRegistry` directly: on a real restore it decodes and dispatches the restore action exactly once (fixing the clobber where one-directional bindings would overwrite the restored value with the store's initial state); a registered provider serializes the projection only at save time. The composable delegates to an `internal` non-composable `wireSaveable` so the correctness path is unit-testable without a composition.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11.0 (`runtime`, `runtime-saveable`), kotlinx.serialization 1.7.3, kotlin-test, Compose `compose.uiTest` (desktop) for integration.

**Spec:** `docs/superpowers/specs/2026-06-09-redux-kotlin-compose-saveable-design.md`

---

## File structure

| Path | Responsibility |
|---|---|
| `redux-kotlin-compose-saveable/build.gradle.kts` | Module build wiring (create) |
| `redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/StateSaver.kt` | The `StateSaver` value type (create) |
| `redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/RememberSaveableState.kt` | `rememberSaveableState` composable + `internal wireSaveable` (create) |
| `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/TestFixtures.kt` | Shared test state/actions/reducer/saver (create) |
| `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/StateSaverTest.kt` | `StateSaver` round-trip (create) |
| `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/RegistryRoundTripTest.kt` | Primary mechanism test via `SaveableStateRegistry` (create) |
| `redux-kotlin-compose-saveable/src/jvmTest/kotlin/org/reduxkotlin/compose/saveable/SaveableIntegrationTest.kt` | End-to-end via `StateRestorationTester` (create) |
| `redux-kotlin-compose-saveable/api/redux-kotlin-compose-saveable.api` | Public API dump (generated) |
| `settings.gradle.kts` | Register the module (modify) |
| `gradle/libs.versions.toml` | Add `compose-runtime-saveable` library (modify) |
| `redux-kotlin-bom/build.gradle.kts` | Add BOM constraint (modify) |
| `redux-kotlin-bundle-compose/build.gradle.kts` | Add `api` dependency (modify) |
| `CLAUDE.md`, `docs/agent/_fragments/modules.md` | Module docs (modify) |

---

## Task 1: Scaffold the module (build wiring)

**Files:**
- Create: `redux-kotlin-compose-saveable/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Add the `runtime-saveable` library to the version catalog**

In `gradle/libs.versions.toml`, under `[libraries]`, add (next to the other `kotlinx-serialization-json`/compose entries):

```toml
compose-runtime-saveable = { module = "org.jetbrains.compose.runtime:runtime-saveable", version.ref = "compose-multiplatform" }
```

(The `compose.runtimeSaveable` Gradle accessor is deprecated in Compose 1.11; use this catalog entry instead.)

- [ ] **Step 2: Create the module build file**

Create `redux-kotlin-compose-saveable/build.gradle.kts`:

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
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.compose.saveable"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-compose"))
                implementation(compose.runtime)
                implementation(libs.compose.runtime.saveable)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        named("jvmTest") {
            dependencies {
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.foundation)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
```

- [ ] **Step 3: Register the module in settings**

In `settings.gradle.kts`, inside the `include(` list, add this line after `":redux-kotlin-compose-multimodel",`:

```kotlin
    ":redux-kotlin-compose-saveable",
```

- [ ] **Step 4: Verify the module configures and compiles (empty sources)**

Run: `./gradlew :redux-kotlin-compose-saveable:compileKotlinJvm`
Expected: `BUILD SUCCESSFUL` (no sources yet — compiles an empty source set).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts redux-kotlin-compose-saveable/build.gradle.kts
git commit -m "build(compose-saveable): scaffold module"
```

---

## Task 2: `StateSaver` value type

**Files:**
- Create: `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/TestFixtures.kt`
- Create: `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/StateSaverTest.kt`
- Create: `redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/StateSaver.kt`

- [ ] **Step 1: Write shared test fixtures**

Create `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/TestFixtures.kt`:

```kotlin
package org.reduxkotlin.compose.saveable

import kotlinx.serialization.Serializable
import org.reduxkotlin.Store
import org.reduxkotlin.createStore

internal data class TestState(val tab: Int = 0, val query: String = "")

internal data class SetTab(val tab: Int)

internal data class RehydrateUi(val tab: Int, val query: String)

@Serializable
internal data class UiSnapshot(val tab: Int, val query: String)

internal val testReducer: (TestState, Any) -> TestState = { state, action ->
    when (action) {
        is SetTab -> state.copy(tab = action.tab)
        is RehydrateUi -> state.copy(tab = action.tab, query = action.query)
        else -> state
    }
}

internal fun newTestStore(initial: TestState = TestState()): Store<TestState> =
    createStore(testReducer, initial)

internal val testSaver: StateSaver<TestState, UiSnapshot> = StateSaver(
    serializer = UiSnapshot.serializer(),
    save = { state -> UiSnapshot(state.tab, state.query) },
    restore = { snapshot -> RehydrateUi(snapshot.tab, snapshot.query) },
)
```

- [ ] **Step 2: Write the failing test**

Create `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/StateSaverTest.kt`:

```kotlin
package org.reduxkotlin.compose.saveable

import kotlin.test.Test
import kotlin.test.assertEquals

class StateSaverTest {

    @Test
    fun encode_then_decode_roundtrips_snapshot() {
        val snapshot = testSaver.save(TestState(tab = 4, query = "hi"))
        val encoded = testSaver.json.encodeToString(testSaver.serializer, snapshot)
        val decoded = testSaver.json.decodeFromString(testSaver.serializer, encoded)
        assertEquals(snapshot, decoded)
    }

    @Test
    fun restore_builds_expected_action() {
        val action = testSaver.restore(UiSnapshot(tab = 4, query = "hi"))
        assertEquals(RehydrateUi(4, "hi"), action)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :redux-kotlin-compose-saveable:jvmTest`
Expected: FAIL — `Unresolved reference: StateSaver` (compilation error in `TestFixtures.kt`/`StateSaverTest.kt`).

- [ ] **Step 4: Implement `StateSaver`**

Create `redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/StateSaver.kt`:

```kotlin
package org.reduxkotlin.compose.saveable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Describes how a slice of store state [S] is projected to a small
 * serializable [Snapshot], and how a restored snapshot is turned back into
 * an action the store's reducer applies.
 *
 * Holds no Compose state — its serialization round-trip is unit-testable
 * without a composition. Reuse one instance across screens.
 */
public class StateSaver<S, Snapshot : Any>(
    /** Serializer for the [Snapshot] type (e.g. `MySnapshot.serializer()`). */
    public val serializer: KSerializer<Snapshot>,
    /** Projects current state to the minimal snapshot worth persisting. */
    public val save: (S) -> Snapshot,
    /** Turns a restored snapshot into an action the reducer applies. */
    public val restore: (Snapshot) -> Any,
    /** JSON codec; override to tune (e.g. `Json { ignoreUnknownKeys = true }`). */
    public val json: Json = Json,
)
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :redux-kotlin-compose-saveable:jvmTest`
Expected: PASS (both `StateSaverTest` tests green).

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-compose-saveable/src/commonMain redux-kotlin-compose-saveable/src/commonTest
git commit -m "feat(compose-saveable): add StateSaver value type"
```

---

## Task 3: `wireSaveable` + `rememberSaveableState` (the mechanism)

**Files:**
- Create: `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/RegistryRoundTripTest.kt`
- Create: `redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/RememberSaveableState.kt`

- [ ] **Step 1: Write the failing mechanism test**

Create `redux-kotlin-compose-saveable/src/commonTest/kotlin/org/reduxkotlin/compose/saveable/RegistryRoundTripTest.kt`:

```kotlin
package org.reduxkotlin.compose.saveable

import androidx.compose.runtime.saveable.SaveableStateRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistryRoundTripTest {

    @Test
    fun restore_rehydrates_a_fresh_store() {
        // Phase 1: a source store with non-initial state, register the provider.
        val source = newTestStore(TestState(tab = 5, query = "x"))
        val registry1 = SaveableStateRegistry(restoredValues = null) { true }
        wireSaveable(source, registry1, "k", testSaver)
        val saved = registry1.performSave()

        // Phase 2: a brand-new store (simulating process death) seeded with the
        // saved values. Restore must dispatch the rehydrate action.
        val fresh = newTestStore(TestState())
        val registry2 = SaveableStateRegistry(restoredValues = saved) { true }
        wireSaveable(fresh, registry2, "k", testSaver)

        assertEquals(TestState(tab = 5, query = "x"), fresh.state)
    }

    @Test
    fun cold_start_dispatches_nothing() {
        val store = newTestStore(TestState(tab = 9))
        var notified = 0
        val unsubscribe = store.subscribe { notified++ }

        val registry = SaveableStateRegistry(restoredValues = null) { true } // empty == cold start
        wireSaveable(store, registry, "k", testSaver)

        unsubscribe()
        assertEquals(0, notified)
        assertEquals(TestState(tab = 9), store.state)
    }

    @Test
    fun corrupt_snapshot_is_ignored_as_cold_start() {
        val store = newTestStore(TestState(tab = 9))
        var notified = 0
        val unsubscribe = store.subscribe { notified++ }

        val registry = SaveableStateRegistry(restoredValues = mapOf("k" to listOf("not-json"))) { true }
        wireSaveable(store, registry, "k", testSaver)

        unsubscribe()
        assertEquals(0, notified)
        assertEquals(TestState(tab = 9), store.state)
    }

    @Test
    fun failing_save_does_not_crash_perform_save() {
        val store = newTestStore(TestState(tab = 1))
        val throwingSaver = StateSaver<TestState, UiSnapshot>(
            serializer = UiSnapshot.serializer(),
            save = { error("boom") },
            restore = { RehydrateUi(it.tab, it.query) },
        )
        val registry = SaveableStateRegistry(restoredValues = null) { true }
        wireSaveable(store, registry, "k", throwingSaver)

        val saved = registry.performSave() // must not throw
        assertEquals(null, saved["k"]?.firstOrNull())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :redux-kotlin-compose-saveable:jvmTest --tests "*RegistryRoundTripTest*"`
Expected: FAIL — `Unresolved reference: wireSaveable`.

- [ ] **Step 3: Implement the composable and `wireSaveable`**

Create `redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/RememberSaveableState.kt`:

```kotlin
package org.reduxkotlin.compose.saveable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import org.reduxkotlin.Store

/**
 * Anchors saveable persistence for [this] store to the enclosing
 * [SaveableStateRegistry]. Place it **once** per persisted scope (typically
 * near the root, or once per screen).
 *
 * On a real restore (rotation or process death) the saved snapshot is decoded
 * and dispatched via [StateSaver.restore] exactly once, so downstream bindings
 * observe the rehydrated store (a single stale frame is possible before they
 * re-sample). On cold start nothing is dispatched. The latest projection is
 * provided to the registry and serialized only when the platform actually
 * saves — there is no steady-state subscription.
 *
 * The persisted store must accept main-thread reads and dispatch (the
 * Compose-facing store: concurrent/threadsafe, or main-confined).
 *
 * @param saver describes the snapshot projection and restore action.
 * @param key stable registry key. Required when multiple anchors exist, inside
 *   lists, or across navigation where positional keys collide. Defaults to the
 *   call-site composite key.
 */
@Composable
public fun <S, Snapshot : Any> Store<S>.rememberSaveableState(
    saver: StateSaver<S, Snapshot>,
    key: String? = null,
) {
    val store = this
    val registry = LocalSaveableStateRegistry.current
    val finalKey = key ?: currentCompositeKeyHash.toString(radix = 36)
    DisposableEffect(store, registry, finalKey) {
        val entry = wireSaveable(store, registry, finalKey, saver)
        onDispose { entry?.unregister() }
    }
}

/**
 * Non-composable core: consume any restored snapshot and dispatch the restore
 * action exactly once (only on a real restore), then register the save
 * provider. Extracted so the correctness path is testable without a
 * composition. Returns the provider entry to unregister on dispose.
 */
internal fun <S, Snapshot : Any> wireSaveable(
    store: Store<S>,
    registry: SaveableStateRegistry?,
    key: String,
    saver: StateSaver<S, Snapshot>,
): SaveableStateRegistry.Entry? {
    val restored = (registry?.consumeRestored(key) as? String)?.let { encoded ->
        runCatching { saver.json.decodeFromString(saver.serializer, encoded) }.getOrNull()
    }
    if (restored != null) {
        store.dispatch(saver.restore(restored))
    }
    return registry?.registerProvider(key) {
        runCatching { saver.json.encodeToString(saver.serializer, saver.save(store.state)) }.getOrNull()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :redux-kotlin-compose-saveable:jvmTest --tests "*RegistryRoundTripTest*"`
Expected: PASS (all four tests green).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-compose-saveable/src/commonMain redux-kotlin-compose-saveable/src/commonTest
git commit -m "feat(compose-saveable): rememberSaveableState anchor + wireSaveable"
```

---

## Task 4: End-to-end integration test (`StateRestorationTester`)

**Files:**
- Create: `redux-kotlin-compose-saveable/src/jvmTest/kotlin/org/reduxkotlin/compose/saveable/SaveableIntegrationTest.kt`

- [ ] **Step 1: Write the integration test**

Create `redux-kotlin-compose-saveable/src/jvmTest/kotlin/org/reduxkotlin/compose/saveable/SaveableIntegrationTest.kt`:

```kotlin
package org.reduxkotlin.compose.saveable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.compose.fieldState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SaveableIntegrationTest {

    @Test
    fun child_binding_shows_restored_value_after_state_restoration() = runComposeUiTest {
        val tester = StateRestorationTester(this)
        tester.setContent {
            // `remember` (not rememberSaveable): re-initialized on restore, so the
            // store is recreated fresh — simulating process death. The anchor must
            // rehydrate it from the saved snapshot.
            val store = remember { newTestStore(TestState()) }
            store.rememberSaveableState(testSaver, key = "root")
            Column {
                BasicText(
                    text = store.fieldState(TestState::tab).value.toString(),
                    modifier = Modifier.testTag("tab"),
                )
                BasicText(
                    text = "set",
                    modifier = Modifier
                        .testTag("set")
                        .clickable { store.dispatch(SetTab(7)) },
                )
            }
        }

        onNodeWithTag("tab").assertTextEquals("0")
        onNodeWithTag("set").performClick()
        onNodeWithTag("tab").assertTextEquals("7")

        tester.emulateSavedInstanceStateRestore()

        // The button was not clicked again; only rehydration can produce "7".
        onNodeWithTag("tab").assertTextEquals("7")
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `./gradlew :redux-kotlin-compose-saveable:jvmTest --tests "*SaveableIntegrationTest*"`
Expected: PASS. If `StateRestorationTester`'s restore method resolves under a different name in the pinned Compose (`emulateSaveAndRestore`), adjust the single call accordingly and re-run.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-compose-saveable/src/jvmTest
git commit -m "test(compose-saveable): end-to-end StateRestorationTester integration"
```

---

## Task 5: Public API (ABI) dump

This repo validates its public surface with Kotlin's built-in ABI validation —
tasks `updateKotlinAbi` / `checkKotlinAbi`, dumps under `api/` (a merged
`<module>.klib.api` plus a per-target `api/jvm/<module>.api`). There is **no**
`apiDump`/`apiCheck` task (the CLAUDE.md names are stale).

**Files:**
- Create (generated): `redux-kotlin-compose-saveable/api/redux-kotlin-compose-saveable.klib.api` and `redux-kotlin-compose-saveable/api/jvm/redux-kotlin-compose-saveable.api`

- [ ] **Step 1: Generate the ABI dump**

Run: `./gradlew :redux-kotlin-compose-saveable:updateKotlinAbi`
Expected: `BUILD SUCCESSFUL`; new files appear under `redux-kotlin-compose-saveable/api/` containing `StateSaver` and `rememberSaveableState` (and NOT `wireSaveable`, which is `internal`).

- [ ] **Step 2: Verify the dump matches**

Run: `./gradlew :redux-kotlin-compose-saveable:checkKotlinAbi`
Expected: `BUILD SUCCESSFUL` (committed dump matches the surface).

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-compose-saveable/api
git commit -m "build(compose-saveable): commit public ABI dump"
```

---

## Task 6: BOM + bundle-compose membership

**Files:**
- Modify: `redux-kotlin-bom/build.gradle.kts`
- Modify: `redux-kotlin-bundle-compose/build.gradle.kts`

- [ ] **Step 1: Add the BOM constraint**

In `redux-kotlin-bom/build.gradle.kts`, inside the `constraints { ... }` block, add after the `redux-kotlin-compose-multimodel` line:

```kotlin
        api("$g:redux-kotlin-compose-saveable:$v")
```

- [ ] **Step 2: Add to the Compose bundle**

In `redux-kotlin-bundle-compose/build.gradle.kts`, inside `commonMain { dependencies { ... } }`, add after the `redux-kotlin-compose-multimodel` line:

```kotlin
                api(project(":redux-kotlin-compose-saveable"))
```

- [ ] **Step 3: Verify both modules build**

Run: `./gradlew :redux-kotlin-bom:assemble :redux-kotlin-bundle-compose:compileKotlinJvm`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-bom/build.gradle.kts redux-kotlin-bundle-compose/build.gradle.kts
git commit -m "build(compose-saveable): add to BOM and bundle-compose"
```

---

## Task 7: Module docs

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/agent/_fragments/modules.md`

- [ ] **Step 1: Update CLAUDE.md module list**

In `CLAUDE.md`, in the `## Modules` section: change the lead sentence "Nine published library modules" to "Ten published library modules", and add this bullet after the `redux-kotlin-compose-multimodel` bullet:

```markdown
- `redux-kotlin-compose-saveable` — `rememberSaveableState` store-anchored snapshot persistence (survives rotation + process death) via Compose `SaveableStateRegistry` + kotlinx.serialization.
```

- [ ] **Step 2: Update the agent modules fragment**

In `docs/agent/_fragments/modules.md`, add this bullet immediately after the `redux-kotlin-compose-multimodel` line (it is the last line in the file):

```markdown
- `redux-kotlin-compose-saveable` — `StateSaver` + `Store<S>.rememberSaveableState` store-anchored snapshot persistence (rotation + process death) via `SaveableStateRegistry`.
```

- [ ] **Step 3: Verify docs files are well-formed**

Run: `git diff --stat CLAUDE.md docs/agent/_fragments/modules.md`
Expected: both files show the added lines.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md docs/agent/_fragments/modules.md
git commit -m "docs(compose-saveable): add module to CLAUDE.md and agent modules fragment"
```

---

## Task 8: Full build gate

- [ ] **Step 1: Run the module's full test matrix the host can run**

Run: `./gradlew :redux-kotlin-compose-saveable:allTests`
Expected: `BUILD SUCCESSFUL` (commonTest + jvmTest green; native/iOS targets are host-gated — trust CI for those).

- [ ] **Step 2: Run the lint + API gates across the tree**

Run: `./gradlew detektAll checkKotlinAbi`
Expected: `BUILD SUCCESSFUL`. If detekt auto-corrects formatting (pre-commit hook), re-stage and re-commit.

- [ ] **Step 3: Final verification commit (if the gates changed any files)**

```bash
git add -A
git commit -m "chore(compose-saveable): satisfy detekt + apiCheck gates" || echo "nothing to commit"
```

---

## Website docs — follow-up (separate PR)

Spec §12 lists a short usage page on the Docusaurus site. This is intentionally **out of this plan's critical path** (it touches `website/` sidebar wiring and the `yarn build` `onBrokenLinks: throw` guard, independent of the module). After the module merges, add a page mirroring an existing extensions page under `website/docs/`, wire it into `website/sidebars.ts`, and run `cd website && yarn build` before committing.

---

## Self-review notes

- **Spec coverage:** §4 module → Task 1; §5 `StateSaver` → Task 2; §5/§6 composable + `wireSaveable` → Task 3; §11 commonTest round-trip + cold-start + corrupt + failing-save → Task 3; §11 integration → Task 4; §8 defensive decode/encode → Task 3 (`runCatching`); §12 apiDump → Task 5, BOM + bundle-compose → Task 6, docs → Task 7 (+ website follow-up); §7 threading → documented in `rememberSaveableState` KDoc (Task 3).
- **Type consistency:** `StateSaver(serializer, save, restore, json)`, `wireSaveable(store, registry, key, saver)`, and `rememberSaveableState(saver, key)` are used identically across Tasks 2–4 and the fixtures.
- **No placeholders:** every code/edit step shows concrete content; the only soft spot is the `StateRestorationTester` restore method name (Task 4 Step 2 gives the exact fallback).
