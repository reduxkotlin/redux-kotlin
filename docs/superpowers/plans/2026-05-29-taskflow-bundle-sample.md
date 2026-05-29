# TaskFlow Sample App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build TaskFlow — a Compose Multiplatform (Android / iOS / Web-wasmJs / JVM-desktop) Kanban sample that showcases `redux-kotlin-bundle` + `redux-kotlin-bundle-compose` end-to-end.

**Architecture:** Two-layer store topology — a single root `ConcurrentModelStore` (accounts, app settings, auth flow) plus an `AccountRegistry: StoreRegistry<AccountId, ModelState>` giving one isolated store per logged-in account. Within an account, a single store uses dynamic model inject/eject (board models added on open, removed on leave). All logic + UI live in `commonMain`; platform entry points only host `App()`. Middleware pipeline: activityLogger → undo → effects(coroutine) → routed reducers. Material 3 Expressive UI, navigation as Redux state.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.10.0, Material 3 Expressive (alpha material3 artifact), redux-kotlin-bundle-compose, kotlinx-datetime, kotlinx-coroutines, Coil 3, multiplatform-markdown-renderer-m3.

---

## Source-of-truth artifacts (read before executing)

- `docs/superpowers/specs/2026-05-29-taskflow-bundle-sample-design.md` — architecture (authoritative).
- `docs/superpowers/specs/Redux-kotlin kanban specs/TaskFlow Hi-Fi Spec.html` + `spec-assets/spec-data.js` — tokens (colors, type, shape, spacing, elevation, motion springs), 14-component inventory, Step-0 feasibility.
- `docs/superpowers/specs/Redux-kotlin kanban specs/TaskFlow Screens Spec.html` — per-screen redlines (compact + expanded), motion choreography, asset list.
- `CLAUDE.md` — detekt gate (`detektAll --auto-correct` on commit; never `--no-verify`), example-module conventions, KMP targets.

## Resolved dependencies (Step-0 outcome — pin these)

| Concern | Resolution | Notes |
|---|---|---|
| Compose MP runtime | `org.jetbrains.compose 1.10.0` (in-repo), Kotlin 2.3.20 | No change; stable on android/jvm/iosX64/iosArm64/iosSimulatorArm64/wasmJs. |
| **Material 3 Expressive** | Pin **alpha** material3 explicitly: `org.jetbrains.compose.material3:material3:1.10.0-alpha02` (aligned to Jetpack Material3 1.5.0-alpha04). | `MaterialExpressiveTheme` / `MotionScheme.expressive` / `ButtonGroup` / `FloatingActionButtonMenu` are gated by `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` and were **removed from the stable line** — they ship only in the alpha artifact. Do **not** rely on the `compose.material3` accessor for these. **Verify the exact resolvable alpha at Task 1** with `./gradlew :examples:taskflow:composeApp:dependencies --configuration commonMainApi`; if `1.10.0-alpha02` is unresolved, use the newest `1.10.x-alphaNN` listed. **Fallback** (if an Expressive composable is absent): `ButtonGroup`→`Row` of `SegmentedButton`; `FloatingActionButtonMenu`→`FloatingActionButton` + `ModalBottomSheet`; `MaterialExpressiveTheme`→`MaterialTheme` with a hand-built `MotionScheme`. |
| Async images | `io.coil-kt.coil3:coil-compose:3.2.0` + `io.coil-kt.coil3:coil-network-ktor:3.2.0` | First-class wasmJs support; Ktor engine on wasm. `AsyncImage`/`SubcomposeAsyncImage` unchanged. |
| Markdown | `com.mikepenz:multiplatform-markdown-renderer-m3:0.39.0` + `com.mikepenz:multiplatform-markdown-renderer-coil3:0.39.0` | Ships wasm-js artifacts; pass `Coil3ImageTransformerImpl` so md images reuse the Coil loader. |
| Date/time | `org.jetbrains.kotlinx:kotlinx-datetime:0.6.2` | `Instant`/`Clock.System`. |
| Web fallback floor | wasmJs primary; document `composeCompatibilityBrowserDistribution` (Kotlin/JS canvas) for old browsers | Affects Web target only. |

> If any alpha artifact cannot be resolved in this repo's network at execution time, STOP and report — do not silently downgrade Expressive features beyond the documented per-composable fallback.

## File structure

```
examples/taskflow/
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/org/reduxkotlin/sample/taskflow/
│       │   │   ├── App.kt                         # root composable + theme + active-store binding
│       │   │   ├── model/
│       │   │   │   ├── Ids.kt                      # typed id value classes
│       │   │   │   ├── RootModels.kt               # AccountsModel, AppSettingsModel, AuthFlowModel
│       │   │   │   ├── AccountModels.kt            # SessionModel, NavModel, Route, BoardListModel
│       │   │   │   ├── BoardModels.kt              # BoardModel, Column, Card, Attachment, Label
│       │   │   │   └── BoardSlice.kt               # FilterModel, UndoModel, SyncModel, ActivityModel, ActivityEntry
│       │   │   ├── action/Actions.kt               # sealed action catalog (+ Undoable marker)
│       │   │   ├── reducer/
│       │   │   │   ├── RootReducers.kt             # accounts/appSettings/authFlow model reducers
│       │   │   │   ├── AccountReducers.kt          # session/nav/boardList model reducers
│       │   │   │   └── BoardReducers.kt            # board/filter/undo/sync/activity model reducers
│       │   │   ├── backend/
│       │   │   │   ├── FakeBackend.kt              # suspend repo w/ latency+failure from settings
│       │   │   │   └── SeedData.kt                 # seeded accounts, boards, cards, asset URLs
│       │   │   ├── middleware/
│       │   │   │   ├── ActivityLoggerMiddleware.kt
│       │   │   │   ├── UndoMiddleware.kt
│       │   │   │   ├── EffectsMiddleware.kt        # Request→Success/Failure, optimistic revert
│       │   │   │   └── BotCollaborator.kt
│       │   │   ├── store/
│       │   │   │   ├── AppStore.kt                 # root createConcurrentModelStore
│       │   │   │   ├── AccountStore.kt             # per-account store factory
│       │   │   │   ├── AccountRegistry.kt          # StoreRegistry wiring + dispose
│       │   │   │   └── BoardModelInjection.kt      # inject/eject board models via replaceReducer
│       │   │   └── ui/
│       │   │       ├── theme/                      # Color.kt, Type.kt, Shape.kt, Motion.kt, Theme.kt, Dimens.kt
│       │   │       ├── adaptive/WindowSize.kt      # BoxWithConstraints breakpoints
│       │   │       ├── components/                 # the 14 inventory composables
│       │   │       └── screens/                    # Login, AccountSwitcher, BoardList, Board, CardDetail, Profile, Settings
│       │   └── composeResources/files/{avatars,cards}/   # offline image fallbacks
│       ├── commonTest/kotlin/org/reduxkotlin/sample/taskflow/   # reducer/middleware/store tests
│       ├── androidMain/   # MainActivity.kt
│       ├── jvmMain/       # main.kt (desktop window)
│       ├── iosMain/       # MainViewController.kt
│       └── wasmJsMain/    # main.kt (ComposeViewport) + resources/index.html
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/{AndroidManifest.xml, kotlin/.../MainActivity.kt}
└── iosApp/                # Xcode project (mirror examples/counter/ios)
```

---

## Phase 0 — Module scaffolding & build (gets a blank App rendering on all targets)

### Task 1: Register module + version catalog entries

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add the modules to `settings.gradle.kts`** — insert after the `":redux-kotlin-routing-codegen-sample",` line inside `include(...)`:

```kotlin
    ":examples:taskflow:composeApp",
    ":examples:taskflow:androidApp",
```

- [ ] **Step 2: Add versions to `[versions]` in `gradle/libs.versions.toml`:**

```toml
coil = "3.2.0"
markdown-renderer = "0.39.0"
kotlinx-datetime = "0.6.2"
compose-material3-expressive = "1.10.0-alpha02"
```

- [ ] **Step 3: Add libraries to `[libraries]`:**

```toml
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor", version.ref = "coil" }
markdown-renderer-m3 = { module = "com.mikepenz:multiplatform-markdown-renderer-m3", version.ref = "markdown-renderer" }
markdown-renderer-coil3 = { module = "com.mikepenz:multiplatform-markdown-renderer-coil3", version.ref = "markdown-renderer" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
compose-material3-expressive = { module = "org.jetbrains.compose.material3:material3", version.ref = "compose-material3-expressive" }
```

- [ ] **Step 4: Verify Gradle still configures.** Run: `./gradlew :examples:taskflow:composeApp:help` — Expected: it FAILS with "project ':examples:taskflow:composeApp' not found" or a missing-build-file error (the directory/build file doesn't exist yet). This confirms the include parsed. Proceed to Task 2.

- [ ] **Step 5: Commit.**

```bash
git add settings.gradle.kts gradle/libs.versions.toml
git commit -m "build(taskflow): register sample modules + add Compose/Coil/markdown deps"
```

### Task 2: composeApp build file + KMP targets

**Files:**
- Create: `examples/taskflow/composeApp/build.gradle.kts`

- [ ] **Step 1: Write the build file.** (Android target is gated on SDK presence, mirroring `redux-kotlin-compose/build.gradle.kts`.)

```kotlin
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    id("convention.control")
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

val hasAndroidSdk: Boolean = run {
    val localProps = rootProject.file("local.properties")
    val inProps = localProps.exists() && localProps.readText().lineSequence()
        .any { it.trim().startsWith("sdk.dir=") && it.substringAfter("sdk.dir=").isNotBlank() }
    val inEnv = !System.getenv("ANDROID_HOME").isNullOrBlank() ||
        !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
    inProps || inEnv
}

kotlin {
    jvm()
    js { browser() }            // Kotlin/JS canvas fallback distribution
    wasmJs { browser() }
    if (hasAndroidSdk) {
        androidTarget()
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { t ->
        t.binaries.framework { baseName = "TaskFlowApp"; isStatic = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":redux-kotlin-bundle-compose"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.material3.expressive)   // alpha M3 (Expressive APIs)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.markdown.renderer.coil3)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val jvmTest by getting {
            dependencies {
                @OptIn(ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
            }
        }
        val jvmMain by getting { dependencies { implementation(compose.desktop.currentOs) } }
    }
}
```

- [ ] **Step 2: Add `kotlinx-coroutines-core` to the catalog** if missing — in `[libraries]`:

```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
```

- [ ] **Step 3: Resolve the Expressive alpha.** Run: `./gradlew :examples:taskflow:composeApp:dependencies --configuration commonMainApi 2>&1 | grep -i material3` — Expected: `org.jetbrains.compose.material3:material3:1.10.0-alpha02` resolves. If unresolved, change `compose-material3-expressive` in the catalog to the newest available `1.10.x-alphaNN` and re-run.

- [ ] **Step 4: Commit.**

```bash
git add examples/taskflow/composeApp/build.gradle.kts gradle/libs.versions.toml
git commit -m "build(taskflow): composeApp KMP targets + Compose/Coil/markdown wiring"
```

### Task 3: Minimal `App()` + all platform entry points (prove multiplatform builds)

**Files:**
- Create: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/App.kt`
- Create: `examples/taskflow/composeApp/src/jvmMain/kotlin/main.kt`
- Create: `examples/taskflow/composeApp/src/wasmJsMain/kotlin/main.kt`
- Create: `examples/taskflow/composeApp/src/wasmJsMain/resources/index.html`
- Create: `examples/taskflow/composeApp/src/iosMain/kotlin/MainViewController.kt`
- Create: `examples/taskflow/composeApp/src/androidMain/kotlin/org/reduxkotlin/sample/taskflow/MainActivity.kt`
- Create: `examples/taskflow/androidApp/build.gradle.kts`, `examples/taskflow/androidApp/src/main/AndroidManifest.xml`

- [ ] **Step 1: `App.kt` (placeholder body — replaced in Task 30).**

```kotlin
package org.reduxkotlin.sample.taskflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("TaskFlow")
            }
        }
    }
}
```

- [ ] **Step 2: jvm `main.kt`.**

```kotlin
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.reduxkotlin.sample.taskflow.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TaskFlow") { App() }
}
```

- [ ] **Step 3: wasmJs `main.kt` + `index.html`.**

```kotlin
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.reduxkotlin.sample.taskflow.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(kotlinx.browser.document.body!!) { App() }
}
```

```html
<!doctype html>
<html lang="en">
<head><meta charset="UTF-8"><title>TaskFlow</title>
<style>html,body{margin:0;height:100%}</style></head>
<body><canvas id="ComposeTarget"></canvas>
<script src="composeApp.js"></script></body>
</html>
```

- [ ] **Step 4: iOS `MainViewController.kt`.**

```kotlin
import androidx.compose.ui.window.ComposeUIViewController
import org.reduxkotlin.sample.taskflow.App

fun MainViewController() = ComposeUIViewController { App() }
```

- [ ] **Step 5: Android `MainActivity.kt` + `androidApp` module.**

```kotlin
package org.reduxkotlin.sample.taskflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
```

`examples/taskflow/androidApp/build.gradle.kts`:

```kotlin
plugins {
    id("convention.control")
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

android {
    namespace = "org.reduxkotlin.sample.taskflow.app"
    compileSdk = 35
    defaultConfig { applicationId = "org.reduxkotlin.sample.taskflow"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1.0" }
}

dependencies {
    implementation(project(":examples:taskflow:composeApp"))
    implementation(libs.androidx.activity)
    implementation(compose.runtime)
}
```

`examples/taskflow/androidApp/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="TaskFlow" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name="org.reduxkotlin.sample.taskflow.MainActivity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
        </activity>
    </application>
</manifest>
```

> Note: the Android `MainActivity` lives in `composeApp`'s `androidMain`; the `androidApp` module depends on `composeApp` and references it. If the host's SDK is absent, `androidTarget()` is skipped and the `androidApp` module won't configure — that is acceptable (CI with the SDK covers it); develop against the jvm/wasm targets locally.

- [ ] **Step 6: Verify desktop + web build/run.** Run: `./gradlew :examples:taskflow:composeApp:jvmJar :examples:taskflow:composeApp:wasmJsBrowserDistribution` — Expected: BUILD SUCCESSFUL. Optionally `./gradlew :examples:taskflow:composeApp:run` shows a window with "TaskFlow".

- [ ] **Step 7: Commit.**

```bash
git add examples/taskflow
git commit -m "feat(taskflow): scaffold App() + android/ios/jvm/wasmJs entry points"
```

---

## Phase 1 — State & types

> These are mostly data declarations; tests target the few behaviors (id equality, undo cap helper, normalized-board invariants). `convention.control` does **not** enable `explicitApi()`, so no KDoc is required — but `detektAll` formatting/style still applies tree-wide.

### Task 4: Typed ids + root models

**Files:**
- Create: `.../model/Ids.kt`, `.../model/RootModels.kt`
- Test: `.../commonTest/.../model/IdsTest.kt`

- [ ] **Step 1: Failing test for value-class identity.**

```kotlin
package org.reduxkotlin.sample.taskflow.model
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdsTest {
    @Test fun ids_with_same_value_are_equal() {
        assertEquals(CardId("c1"), CardId("c1"))
        assertNotEquals(CardId("c1"), CardId("c2"))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (`CardId` unresolved). Run: `./gradlew :examples:taskflow:composeApp:jvmTest --tests "*IdsTest"`

- [ ] **Step 3: `Ids.kt`.**

```kotlin
package org.reduxkotlin.sample.taskflow.model

import kotlin.jvm.JvmInline

@JvmInline value class AccountId(val v: String)
@JvmInline value class BoardId(val v: String)
@JvmInline value class ColumnId(val v: String)
@JvmInline value class CardId(val v: String)
@JvmInline value class LabelId(val v: String)
```

- [ ] **Step 4: `RootModels.kt`.**

```kotlin
package org.reduxkotlin.sample.taskflow.model

data class AccountsModel(
    val accounts: Map<AccountId, AccountSummary> = emptyMap(),
    val activeAccountId: AccountId? = null,
)

data class AccountSummary(
    val id: AccountId,
    val displayName: String,
    val email: String,
    val avatarUrl: String,
)

enum class Theme { System, Light, Dark }

data class FakeServiceConfig(
    val latencyMinMs: Int = 300,
    val latencyMaxMs: Int = 800,
    val failureRate: Float = 0.10f,
    val botEnabled: Boolean = true,
    val botIntervalMs: Int = 4_000,
)

data class AppSettingsModel(
    val theme: Theme = Theme.System,
    val language: String = "en",
    val fakeService: FakeServiceConfig = FakeServiceConfig(),
)

enum class AuthMode { Login, AddAccount }

data class AuthFlowModel(
    val mode: AuthMode = AuthMode.Login,
    val inFlight: Boolean = false,
    val error: String? = null,
)
```

- [ ] **Step 5: Run, expect PASS.** `./gradlew :examples:taskflow:composeApp:jvmTest --tests "*IdsTest"`

- [ ] **Step 6: Commit.** `git add examples/taskflow && git commit -m "feat(taskflow): typed ids + root models"`

### Task 5: Per-account models + board models

**Files:**
- Create: `.../model/AccountModels.kt`, `.../model/BoardModels.kt`, `.../model/BoardSlice.kt`

- [ ] **Step 1: `AccountModels.kt`.**

```kotlin
package org.reduxkotlin.sample.taskflow.model

data class AccountDetail(
    val id: AccountId,
    val displayName: String,
    val email: String,
    val avatarUrl: String,
    val bio: String? = null,
)

data class SessionModel(val profile: AccountDetail)

sealed interface Route {
    data object BoardList : Route
    data class Board(val boardId: BoardId) : Route
    data object Profile : Route
    data object Settings : Route
}

data class NavModel(val route: Route = Route.BoardList, val openCardId: CardId? = null)

data class BoardSummary(
    val id: BoardId,
    val name: String,
    val color: Long,
    val cardCount: Int,
    val doneCount: Int,
    val updatedAt: kotlinx.datetime.Instant,
)

data class BoardListModel(
    val boards: Map<BoardId, BoardSummary> = emptyMap(),
    val order: List<BoardId> = emptyList(),
)
```

- [ ] **Step 2: `BoardModels.kt`.**

```kotlin
package org.reduxkotlin.sample.taskflow.model

import kotlinx.datetime.Instant

data class Label(val id: LabelId, val name: String, val color: Long)

sealed interface Attachment {
    data class Image(val url: String, val alt: String, val width: Int? = null, val height: Int? = null) : Attachment
    data class Link(val url: String, val title: String? = null, val description: String? = null, val imageUrl: String? = null) : Attachment
}

data class Card(
    val id: CardId,
    val title: String,
    val description: String,                 // Markdown
    val attachments: List<Attachment> = emptyList(),
    val labels: List<Label> = emptyList(),
    val assigneeId: AccountId? = null,
    val createdBy: AccountId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Column(
    val id: ColumnId,
    val title: String,
    val cardIds: List<CardId>,
    val wipLimit: Int? = null,
)

data class BoardModel(
    val boardId: BoardId,
    val columns: List<Column>,
    val cards: Map<CardId, Card>,
)
```

- [ ] **Step 3: `BoardSlice.kt`.**

```kotlin
package org.reduxkotlin.sample.taskflow.model

import kotlinx.datetime.Instant

data class FilterModel(
    val query: String = "",
    val assignee: AccountId? = null,
    val labelIds: Set<LabelId> = emptySet(),
)

data class UndoModel(
    val past: List<BoardModel> = emptyList(),
    val future: List<BoardModel> = emptyList(),
    val cap: Int = 50,
)

data class SyncModel(
    val inFlight: Set<String> = emptySet(),
    val lastError: String? = null,
)

data class ActivityEntry(
    val id: String,
    val actorId: AccountId,
    val summary: String,
    val timestamp: Instant,
)

data class ActivityModel(val entries: List<ActivityEntry> = emptyList())
```

- [ ] **Step 4: Verify compile.** Run: `./gradlew :examples:taskflow:composeApp:compileKotlinJvm` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.** `git add examples/taskflow && git commit -m "feat(taskflow): per-account + board state models"`

---

## Phase 2 — Action catalog

### Task 6: Actions + Undoable marker

**Files:**
- Create: `.../action/Actions.kt`
- Test: `.../commonTest/.../action/ActionsTest.kt`

- [ ] **Step 1: Failing test — undoable classification.**

```kotlin
package org.reduxkotlin.sample.taskflow.action
import org.reduxkotlin.sample.taskflow.model.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ActionsTest {
    @Test fun mutating_card_actions_are_undoable() {
        assertTrue(CardMoveRequested(CardId("c1"), ColumnId("a"), ColumnId("b"), 0) is Undoable)
        assertTrue(AddCard(ColumnId("a"), "t", "d") is Undoable)
    }
    @Test fun nav_and_async_results_are_not_undoable() {
        assertFalse(Navigate(Route.Profile) is Undoable)
        assertFalse(CardMoveSucceeded("op1") is Undoable)
    }
}
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :examples:taskflow:composeApp:jvmTest --tests "*ActionsTest"`

- [ ] **Step 3: `Actions.kt`** — full catalog. `Undoable` marks actions the undo middleware snapshots before.

```kotlin
package org.reduxkotlin.sample.taskflow.action

import org.reduxkotlin.sample.taskflow.model.*

/** Marker: the undo middleware snapshots the board slice before reducing these. */
sealed interface Undoable

sealed interface Action

// --- Auth / accounts (root) ---
data class StartLogin(val mode: AuthMode) : Action
data object LoginRequested : Action
data class AccountLoggedIn(val account: AccountSummary, val detail: AccountDetail) : Action
data class LoginFailed(val error: String) : Action
data class SwitchAccount(val id: AccountId) : Action
data class LogoutAccount(val id: AccountId) : Action

// --- App settings (root) ---
data class SetTheme(val theme: Theme) : Action
data class SetLatency(val minMs: Int, val maxMs: Int) : Action
data class SetFailureRate(val rate: Float) : Action
data class SetBotEnabled(val enabled: Boolean) : Action

// --- Navigation (per-account) ---
data class Navigate(val route: Route) : Action
data class OpenCard(val id: CardId) : Action
data object CloseCard : Action

// --- Board list (per-account) ---
data object LoadBoardListRequested : Action
data class LoadBoardListSucceeded(val boards: List<BoardSummary>) : Action
data class LoadBoardListFailed(val error: String) : Action
data class CreateBoard(val name: String) : Action

// --- Board load (per-account, injected slice) ---
data class LoadBoardRequested(val boardId: BoardId) : Action
data class LoadBoardSucceeded(val board: BoardModel) : Action
data class LoadBoardFailed(val boardId: BoardId, val error: String) : Action

// --- Card mutations (per-account, injected slice) — UNDOABLE + optimistic ---
data class CardMoveRequested(val cardId: CardId, val from: ColumnId, val to: ColumnId, val toIndex: Int) : Action, Undoable
data class AddCard(val columnId: ColumnId, val title: String, val description: String) : Action, Undoable
data class EditCard(val cardId: CardId, val title: String, val description: String) : Action, Undoable
data class DeleteCard(val cardId: CardId) : Action, Undoable

// --- Async op results (per-account) — NOT undoable ---
data class CardOpSucceeded(val opId: String) : Action
data class CardOpFailed(val opId: String, val error: String, val revertTo: BoardModel) : Action

// --- Undo / redo (per-account) ---
data object Undo : Action
data object Redo : Action

// --- Filter (per-account) ---
data class SetFilterQuery(val query: String) : Action
data class SetFilterAssignee(val id: AccountId?) : Action
data class ToggleFilterLabel(val id: LabelId) : Action

// --- Profile (per-account) ---
data class EditProfile(val displayName: String, val bio: String?) : Action

// --- Activity / bot (per-account) ---
data class RecordActivity(val entry: ActivityEntry) : Action
```

- [ ] **Step 4: Run, expect PASS.** `./gradlew :examples:taskflow:composeApp:jvmTest --tests "*ActionsTest"`

- [ ] **Step 5: Commit.** `git add examples/taskflow && git commit -m "feat(taskflow): action catalog + Undoable marker"`

---

## Phase 3 — Reducers (routed `on<Action>`)

> Each model gets a `ModelReducer` built with the routing DSL from `redux-kotlin-routing` (re-exported by the bundle). Compose them with `combineModelReducers`. Tests assert each transition.

### Task 7: Root model reducers (accounts, settings, auth)

**Files:**
- Create: `.../reducer/RootReducers.kt`
- Test: `.../commonTest/.../reducer/RootReducersTest.kt`

- [ ] **Step 1: Failing test.**

```kotlin
package org.reduxkotlin.sample.taskflow.reducer
import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class RootReducersTest {
    private val ann = AccountSummary(AccountId("ann"), "Ann", "ann@x.dev", "url")
    private val annDetail = AccountDetail(AccountId("ann"), "Ann", "ann@x.dev", "url")

    @Test fun login_adds_account_and_sets_active() {
        val s = accountsReducer(AccountsModel(), AccountLoggedIn(ann, annDetail))
        assertEquals(ann, s.accounts[AccountId("ann")])
        assertEquals(AccountId("ann"), s.activeAccountId)
    }
    @Test fun logout_removes_account_and_repoints_active() {
        val start = AccountsModel(mapOf(ann.id to ann), ann.id)
        val s = accountsReducer(start, LogoutAccount(ann.id))
        assertEquals(null, s.accounts[ann.id])
        assertEquals(null, s.activeAccountId)
    }
    @Test fun set_failure_rate_clamps_0_1() {
        val s = appSettingsReducer(AppSettingsModel(), SetFailureRate(2f))
        assertEquals(1f, s.fakeService.failureRate)
    }
}
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :examples:taskflow:composeApp:jvmTest --tests "*RootReducersTest"`

- [ ] **Step 3: `RootReducers.kt`.** Use the routing DSL; expose plain reducer functions too for direct unit testing.

```kotlin
package org.reduxkotlin.sample.taskflow.reducer

import org.reduxkotlin.routing.*           // routing DSL (re-exported by bundle)
import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.model.*

fun accountsReducer(state: AccountsModel, action: Any): AccountsModel = when (action) {
    is AccountLoggedIn -> state.copy(
        accounts = state.accounts + (action.account.id to action.account),
        activeAccountId = action.account.id,
    )
    is SwitchAccount -> if (state.accounts.containsKey(action.id)) state.copy(activeAccountId = action.id) else state
    is LogoutAccount -> {
        val remaining = state.accounts - action.id
        state.copy(
            accounts = remaining,
            activeAccountId = if (state.activeAccountId == action.id) remaining.keys.firstOrNull() else state.activeAccountId,
        )
    }
    else -> state
}

fun appSettingsReducer(state: AppSettingsModel, action: Any): AppSettingsModel = when (action) {
    is SetTheme -> state.copy(theme = action.theme)
    is SetLatency -> state.copy(fakeService = state.fakeService.copy(latencyMinMs = action.minMs, latencyMaxMs = action.maxMs))
    is SetFailureRate -> state.copy(fakeService = state.fakeService.copy(failureRate = action.rate.coerceIn(0f, 1f)))
    is SetBotEnabled -> state.copy(fakeService = state.fakeService.copy(botEnabled = action.enabled))
    else -> state
}

fun authFlowReducer(state: AuthFlowModel, action: Any): AuthFlowModel = when (action) {
    is StartLogin -> AuthFlowModel(mode = action.mode)
    LoginRequested -> state.copy(inFlight = true, error = null)
    is AccountLoggedIn -> AuthFlowModel()         // reset on success
    is LoginFailed -> state.copy(inFlight = false, error = action.error)
    else -> state
}
```

- [ ] **Step 4: Run, expect PASS.** `./gradlew :examples:taskflow:composeApp:jvmTest --tests "*RootReducersTest"`

- [ ] **Step 5: Commit.** `git add examples/taskflow && git commit -m "feat(taskflow): root model reducers"`

> NOTE on the routing DSL: confirm the exact public symbols of `redux-kotlin-routing` via `redux-kotlin-routing/README.md` and its `api/` dump before wiring `combineModelReducers`. The plain `fun fooReducer(state, action)` shape above keeps reducers unit-testable regardless of the DSL surface; Task 13 adapts them into `ModelReducerEntry`s for `combineModelReducers`.

### Task 8: Account reducers (session, nav, board list)

**Files:**
- Create: `.../reducer/AccountReducers.kt`
- Test: `.../commonTest/.../reducer/AccountReducersTest.kt`

- [ ] **Step 1: Failing test.**

```kotlin
package org.reduxkotlin.sample.taskflow.reducer
import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountReducersTest {
    @Test fun navigate_changes_route_and_clears_open_card() {
        val s = navReducer(NavModel(route = Route.BoardList, openCardId = CardId("c1")), Navigate(Route.Profile))
        assertEquals(Route.Profile, s.route)
        assertEquals(null, s.openCardId)
    }
    @Test fun open_and_close_card_set_openCardId() {
        val opened = navReducer(NavModel(), OpenCard(CardId("c9")))
        assertEquals(CardId("c9"), opened.openCardId)
        assertEquals(null, navReducer(opened, CloseCard).openCardId)
    }
    @Test fun load_board_list_succeeded_populates_order() {
        val b = BoardSummary(BoardId("b1"), "Sprint", 0, 0, 0, kotlinx.datetime.Instant.fromEpochSeconds(0))
        val s = boardListReducer(BoardListModel(), LoadBoardListSucceeded(listOf(b)))
        assertEquals(listOf(BoardId("b1")), s.order)
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: `AccountReducers.kt`.**

```kotlin
package org.reduxkotlin.sample.taskflow.reducer

import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.model.*

fun navReducer(state: NavModel, action: Any): NavModel = when (action) {
    is Navigate -> NavModel(route = action.route, openCardId = null)
    is OpenCard -> state.copy(openCardId = action.id)
    CloseCard -> state.copy(openCardId = null)
    else -> state
}

fun sessionReducer(state: SessionModel, action: Any): SessionModel = when (action) {
    is EditProfile -> state.copy(profile = state.profile.copy(displayName = action.displayName, bio = action.bio))
    else -> state
}

fun boardListReducer(state: BoardListModel, action: Any): BoardListModel = when (action) {
    is LoadBoardListSucceeded -> state.copy(
        boards = action.boards.associateBy { it.id },
        order = action.boards.map { it.id },
    )
    is CreateBoard -> state // board id minted by the effect; succeeded result re-loads the list
    else -> state
}
```

- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "feat(taskflow): account model reducers"`

### Task 9: Board reducers (board, filter, undo, sync, activity) — the core

**Files:**
- Create: `.../reducer/BoardReducers.kt`
- Test: `.../commonTest/.../reducer/BoardReducersTest.kt`

- [ ] **Step 1: Failing tests (normalized move + undo stacks + WIP-independent).**

```kotlin
package org.reduxkotlin.sample.taskflow.reducer
import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.model.*
import kotlinx.datetime.Instant
import kotlin.test.*

class BoardReducersTest {
    private val t = Instant.fromEpochSeconds(0)
    private fun card(id: String) = Card(CardId(id), id, "", createdBy = AccountId("ann"), createdAt = t, updatedAt = t)
    private fun board() = BoardModel(
        BoardId("b1"),
        columns = listOf(
            Column(ColumnId("todo"), "To Do", listOf(CardId("c1"), CardId("c2"))),
            Column(ColumnId("doing"), "Doing", emptyList(), wipLimit = 1),
        ),
        cards = mapOf(CardId("c1") to card("c1"), CardId("c2") to card("c2")),
    )

    @Test fun move_card_updates_only_two_columns() {
        val s = boardReducer(board(), CardMoveRequested(CardId("c1"), ColumnId("todo"), ColumnId("doing"), 0))!!
        assertEquals(listOf(CardId("c2")), s.columns.first { it.id == ColumnId("todo") }.cardIds)
        assertEquals(listOf(CardId("c1")), s.columns.first { it.id == ColumnId("doing") }.cardIds)
    }

    @Test fun undo_pushes_present_and_restores_past() {
        val before = board()
        val u0 = UndoModel()
        // undo middleware records the snapshot; reducer applies Undo against present
        val u1 = undoReducer(u0.copy(past = listOf(before)), Undo, present = board())
        assertEquals(before, u1.restored)
        assertEquals(1, u1.model.future.size)
        assertTrue(u1.model.past.isEmpty())
    }

    @Test fun undo_history_respects_cap() {
        val capped = pushUndo(UndoModel(cap = 2), board(), board(), board())
        assertEquals(2, capped.past.size)
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: `BoardReducers.kt`.** `boardReducer` returns `BoardModel?` (null when no board injected). The undo reducer is split so the middleware drives snapshots; expose `pushUndo` + an `UndoResult`.

```kotlin
package org.reduxkotlin.sample.taskflow.reducer

import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.model.*

fun boardReducer(state: BoardModel?, action: Any): BoardModel? {
    if (state == null) return if (action is LoadBoardSucceeded) action.board else null
    return when (action) {
        is LoadBoardSucceeded -> action.board
        is CardMoveRequested -> moveCard(state, action)
        is AddCard -> addCard(state, action)
        is EditCard -> editCard(state, action)
        is DeleteCard -> deleteCard(state, action)
        is CardOpFailed -> action.revertTo            // optimistic revert to snapshot
        else -> state
    }
}

private fun moveCard(b: BoardModel, a: CardMoveRequested): BoardModel = b.copy(
    columns = b.columns.map { col ->
        when (col.id) {
            a.from -> col.copy(cardIds = col.cardIds - a.cardId)
            a.to -> col.copy(cardIds = col.cardIds.toMutableList().also { it.add(a.toIndex.coerceIn(0, it.size), a.cardId) })
            else -> col
        }
    },
)

private fun addCard(b: BoardModel, a: AddCard): BoardModel {
    val id = CardId("card-" + (b.cards.size + 1) + "-" + a.columnId.v)
    val now = b.cards.values.firstOrNull()?.updatedAt ?: kotlinx.datetime.Instant.DISTANT_PAST
    val card = Card(id, a.title, a.description, createdBy = b.cards.values.firstOrNull()?.createdBy ?: AccountId("unknown"), createdAt = now, updatedAt = now)
    return b.copy(
        cards = b.cards + (id to card),
        columns = b.columns.map { if (it.id == a.columnId) it.copy(cardIds = it.cardIds + id) else it },
    )
}

private fun editCard(b: BoardModel, a: EditCard): BoardModel =
    b.cards[a.cardId]?.let { b.copy(cards = b.cards + (a.cardId to it.copy(title = a.title, description = a.description))) } ?: b

private fun deleteCard(b: BoardModel, a: DeleteCard): BoardModel = b.copy(
    cards = b.cards - a.cardId,
    columns = b.columns.map { it.copy(cardIds = it.cardIds - a.cardId) },
)

fun filterReducer(state: FilterModel, action: Any): FilterModel = when (action) {
    is SetFilterQuery -> state.copy(query = action.query)
    is SetFilterAssignee -> state.copy(assignee = action.id)
    is ToggleFilterLabel -> state.copy(labelIds = if (action.id in state.labelIds) state.labelIds - action.id else state.labelIds + action.id)
    else -> state
}

fun syncReducer(state: SyncModel, action: Any): SyncModel = when (action) {
    is CardMoveRequested -> state.copy(inFlight = state.inFlight + opIdOf(action))
    is AddCard, is EditCard, is DeleteCard -> state
    is CardOpSucceeded -> state.copy(inFlight = state.inFlight - action.opId, lastError = null)
    is CardOpFailed -> state.copy(inFlight = state.inFlight - action.opId, lastError = action.error)
    is LoadBoardRequested -> state.copy(inFlight = state.inFlight + "load:" + action.boardId.v)
    is LoadBoardSucceeded -> state.copy(inFlight = state.inFlight - ("load:" + action.board.boardId.v))
    is LoadBoardFailed -> state.copy(inFlight = state.inFlight - ("load:" + action.boardId.v), lastError = action.error)
    else -> state
}

fun opIdOf(a: CardMoveRequested): String = "move:" + a.cardId.v + ":" + a.to.v

fun activityReducer(state: ActivityModel, action: Any): ActivityModel = when (action) {
    is RecordActivity -> state.copy(entries = (listOf(action.entry) + state.entries).take(50))
    else -> state
}

// --- Undo helpers (middleware-driven) ---
data class UndoResult(val model: UndoModel, val restored: BoardModel?)

fun pushUndo(model: UndoModel, vararg snapshots: BoardModel): UndoModel =
    snapshots.fold(model) { m, snap -> m.copy(past = (m.past + snap).takeLast(m.cap), future = emptyList()) }

fun undoReducer(model: UndoModel, action: Any, present: BoardModel?): UndoResult = when (action) {
    Undo -> model.past.lastOrNull()?.let { UndoResult(model.copy(past = model.past.dropLast(1), future = model.future + (present ?: it)), it) } ?: UndoResult(model, null)
    Redo -> model.future.lastOrNull()?.let { UndoResult(model.copy(future = model.future.dropLast(1), past = (model.past + (present ?: it)).takeLast(model.cap)), it) } ?: UndoResult(model, null)
    else -> UndoResult(model, null)
}
```

- [ ] **Step 4: Run, expect PASS.** `./gradlew :examples:taskflow:composeApp:jvmTest --tests "*BoardReducersTest"`

- [ ] **Step 5: Commit.** `git commit -m "feat(taskflow): board/filter/sync/activity reducers + undo helpers"`

---

## Phase 4 — Fake backend & seed data

### Task 10: FakeBackend (latency + failure from settings)

**Files:**
- Create: `.../backend/FakeBackend.kt`
- Test: `.../commonTest/.../backend/FakeBackendTest.kt`

- [ ] **Step 1: Failing test (deterministic via injected RNG + virtual time).**

```kotlin
package org.reduxkotlin.sample.taskflow.backend
import org.reduxkotlin.sample.taskflow.model.*
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*

class FakeBackendTest {
    @Test fun fails_when_rng_below_failure_rate() = runTest {
        val be = FakeBackend(settings = { FakeServiceConfig(failureRate = 1f) }, rng = Random(0))
        assertFailsWith<FakeBackendException> { be.persist("op1") }
    }
    @Test fun succeeds_when_failure_rate_zero() = runTest {
        val be = FakeBackend(settings = { FakeServiceConfig(failureRate = 0f, latencyMinMs = 0, latencyMaxMs = 0) }, rng = Random(0))
        be.persist("op1") // no throw
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: `FakeBackend.kt`.**

```kotlin
package org.reduxkotlin.sample.taskflow.backend

import kotlinx.coroutines.delay
import org.reduxkotlin.sample.taskflow.model.*
import kotlin.random.Random

class FakeBackendException(message: String) : Exception(message)

class FakeBackend(
    private val settings: () -> FakeServiceConfig,
    private val rng: Random = Random.Default,
) {
    private suspend fun simulate(op: String) {
        val cfg = settings()
        val span = (cfg.latencyMaxMs - cfg.latencyMinMs).coerceAtLeast(0)
        delay((cfg.latencyMinMs + if (span == 0) 0 else rng.nextInt(span)).toLong())
        if (rng.nextFloat() < cfg.failureRate) throw FakeBackendException("Simulated failure: $op")
    }

    suspend fun persist(op: String) = simulate(op)
    suspend fun loadBoardList(): List<BoardSummary> { simulate("loadBoardList"); return SeedData.boardSummaries() }
    suspend fun loadBoard(id: BoardId): BoardModel { simulate("loadBoard"); return SeedData.board(id) }
    suspend fun createBoard(name: String): BoardSummary { simulate("createBoard"); return SeedData.newBoard(name) }
}
```

- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "feat(taskflow): fake backend with settings-driven latency+failure"`

### Task 11: SeedData (accounts, boards, cards, asset URLs)

**Files:**
- Create: `.../backend/SeedData.kt`
- Test: `.../commonTest/.../backend/SeedDataTest.kt`

- [ ] **Step 1: Failing test.**

```kotlin
package org.reduxkotlin.sample.taskflow.backend
import org.reduxkotlin.sample.taskflow.model.*
import kotlin.test.*

class SeedDataTest {
    @Test fun three_demo_accounts_with_avatar_urls() {
        val accts = SeedData.accounts()
        assertEquals(3, accts.size)
        assertTrue(accts.all { it.avatarUrl.startsWith("https://i.pravatar.cc/") })
    }
    @Test fun seeded_board_is_normalized() {
        val b = SeedData.board(SeedData.accounts().first().let { SeedData.boardSummaries().first().id })
        val idsInColumns = b.columns.flatMap { it.cardIds }.toSet()
        assertEquals(b.cards.keys, idsInColumns) // every card referenced exactly once
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: `SeedData.kt`** — concrete seeded accounts/boards/cards with the asset URLs from the spec (`i.pravatar.cc/150?u={id}`, `picsum.photos/seed/{cardId}/600/400`, DiceBear bot). Build at least one board per account with cards across To Do / Doing / Done, some with markdown bodies, labels (the 6 semantic label colors), and image/link attachments. Keep ids deterministic; `Clock.System.now()` for timestamps. Provide `accounts()`, `accountDetail(id)`, `boardSummaries()`, `board(id)`, `newBoard(name)`. (Engineer fills the seed content; structure constrained by the test in Step 1 and the asset table in the Screens Spec.)

- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "feat(taskflow): seed accounts/boards/cards + stock asset URLs"`

---

## Phase 5 — Middleware

> Middleware uses the core `Middleware` type from `redux-kotlin` (re-exported by the bundle): `store -> next -> action -> Any`. Confirm the exact signature in `redux-kotlin/README.md` before writing.

### Task 12: EffectsMiddleware (optimistic Request→Success/Failure)

**Files:**
- Create: `.../middleware/EffectsMiddleware.kt`
- Test: `.../commonTest/.../middleware/EffectsMiddlewareTest.kt`

- [ ] **Step 1: Failing test** — a move request that fails dispatches `CardOpFailed` carrying the pre-move snapshot; a load request dispatches `LoadBoardSucceeded`. Use `runTest` + a fake store recording dispatched actions, injecting a `FakeBackend` with `failureRate=1f` then `0f`, and a `CoroutineScope(StandardTestDispatcher)`.

```kotlin
package org.reduxkotlin.sample.taskflow.middleware
import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.backend.*
import org.reduxkotlin.sample.taskflow.model.*
import kotlinx.coroutines.test.*
import kotlin.random.Random
import kotlin.test.*

class EffectsMiddlewareTest {
    @Test fun failed_move_dispatches_CardOpFailed_with_snapshot() = runTest {
        val dispatched = mutableListOf<Any>()
        val snapshot = BoardModel(BoardId("b1"), emptyList(), emptyMap())
        val be = FakeBackend(settings = { FakeServiceConfig(failureRate = 1f, latencyMinMs = 0, latencyMaxMs = 0) }, rng = Random(0))
        val mw = effectsMiddleware(be, scope = backgroundScope, boardSnapshot = { snapshot })
        // wiring detail: feed a CardMoveRequested through mw with a recording next; advanceUntilIdle()
        // assert dispatched contains a CardOpFailed whose revertTo == snapshot
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: `EffectsMiddleware.kt`.**

```kotlin
package org.reduxkotlin.sample.taskflow.middleware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.backend.*
import org.reduxkotlin.sample.taskflow.model.*

/**
 * Intercepts *Requested actions, lets the reducer apply the optimistic change first
 * (calls next(action)), then persists via the backend off-thread, dispatching
 * Succeeded/Failed. On failure it carries a snapshot for the reducer to revert to.
 */
fun effectsMiddleware(
    backend: FakeBackend,
    scope: CoroutineScope,
    boardSnapshot: () -> BoardModel?,
) = { store: Any /* Store<ModelState> */ ->
    { next: (Any) -> Any ->
        { action: Any ->
            when (action) {
                is CardMoveRequested -> {
                    val snap = boardSnapshot()
                    val result = next(action)                 // optimistic
                    val op = opIdOf(action)
                    scope.launch {
                        runCatching { backend.persist(op) }
                            .onSuccess { dispatchTo(store, CardOpSucceeded(op)) }
                            .onFailure { dispatchTo(store, CardOpFailed(op, it.message ?: "error", snap!!)) }
                    }
                    result
                }
                is LoadBoardRequested -> {
                    val result = next(action)
                    scope.launch {
                        runCatching { backend.loadBoard(action.boardId) }
                            .onSuccess { dispatchTo(store, LoadBoardSucceeded(it)) }
                            .onFailure { dispatchTo(store, LoadBoardFailed(action.boardId, it.message ?: "error")) }
                    }
                    result
                }
                is LoadBoardListRequested -> {
                    val result = next(action)
                    scope.launch {
                        runCatching { backend.loadBoardList() }
                            .onSuccess { dispatchTo(store, LoadBoardListSucceeded(it)) }
                            .onFailure { dispatchTo(store, LoadBoardListFailed(it.message ?: "error")) }
                    }
                    result
                }
                else -> next(action)
            }
        }
    }
}
```

> `dispatchTo(store, action)` and the exact `Middleware` lambda shape adapt to `redux-kotlin`'s real type — fix the signature in Step 1 once `redux-kotlin/README.md` is confirmed. The behavior (optimistic next() → async persist → success/failure dispatch, snapshot on move) is the contract under test.

- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "feat(taskflow): effects middleware (optimistic async + revert)"`

### Task 13: UndoMiddleware + ActivityLogger + BotCollaborator + combineModelReducers wiring

**Files:**
- Create: `.../middleware/UndoMiddleware.kt`, `.../middleware/ActivityLoggerMiddleware.kt`, `.../middleware/BotCollaborator.kt`
- Test: `.../commonTest/.../middleware/UndoMiddlewareTest.kt`, `.../middleware/ActivityLoggerTest.kt`

- [ ] **Step 1: Failing tests** — (a) undo middleware snapshots the board before an `Undoable` action and `Undo` restores it; (b) activity logger turns a `CardMoveRequested` into a `RecordActivity` with a human summary; (c) bot collaborator, when enabled, dispatches a card action within one interval (use virtual time + `advanceTimeBy`).

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement.**
  - `UndoMiddleware`: before `next(action)` when `action is Undoable`, capture `boardSnapshot()` and dispatch into the undo slice via `pushUndo`; on `Undo`/`Redo` compute `undoReducer(...)` and dispatch a `LoadBoardSucceeded(restored)` to apply the restored board (reusing the board reducer path) plus re-persist via effects.
  - `ActivityLoggerMiddleware`: maps select actions → `RecordActivity(ActivityEntry(...))` dispatched after `next`. Humanize: "moved \"<title>\" → <column>", "added \"<title>\"", etc.
  - `BotCollaborator`: a `fun startBot(scope, store, settings): Job` looping `while(isActive){ delay(settings().botIntervalMs); if(settings().botEnabled) store.dispatch(randomBotAction()) }`. Vary action by a counter (no `Random.Default` seed reliance in tests — pass an index/seed).

- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "feat(taskflow): undo + activity-logger middleware + bot collaborator"`

---

## Phase 6 — Store factories, registry, model inject/eject (the crux)

### Task 14: AppStore (root) + combined root reducer

**Files:**
- Create: `.../store/AppStore.kt`
- Test: `.../commonTest/.../store/AppStoreTest.kt`

- [ ] **Step 1: Failing test** — `createAppStore()` produces a store whose `ModelState` has `AccountsModel`, `AppSettingsModel`, `AuthFlowModel`; dispatching `AccountLoggedIn` updates `AccountsModel`.

```kotlin
package org.reduxkotlin.sample.taskflow.store
import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.model.*
import kotlin.test.*

class AppStoreTest {
    @Test fun root_store_holds_three_models_and_reduces() {
        val store = createAppStore()
        val ann = AccountSummary(AccountId("ann"), "Ann", "ann@x.dev", "u")
        store.dispatch(AccountLoggedIn(ann, AccountDetail(AccountId("ann"), "Ann", "ann@x.dev", "u")))
        val accounts = store.state.get(AccountsModel::class)
        assertEquals(AccountId("ann"), accounts.activeAccountId)
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: `AppStore.kt`** — use `createConcurrentModelStore { model(...) { ... } }` from the bundle (per `redux-kotlin-bundle/README.md`). Register the three root models with their routed reducers (adapt the plain `*Reducer` functions from Task 7 into the DSL's `on<Action>` blocks or `modelReducerOf`). Apply middleware via the `enhancer` parameter (`applyMiddleware(activityLogger, undo, effects)`).

- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "feat(taskflow): root AppStore via createConcurrentModelStore"`

### Task 15: AccountStore factory + AccountRegistry

**Files:**
- Create: `.../store/AccountStore.kt`, `.../store/AccountRegistry.kt`
- Test: `.../commonTest/.../store/AccountRegistryTest.kt`

- [ ] **Step 1: Failing test — isolation + dispose.**

```kotlin
package org.reduxkotlin.sample.taskflow.store
import org.reduxkotlin.sample.taskflow.action.*
import org.reduxkotlin.sample.taskflow.model.*
import kotlin.test.*

class AccountRegistryTest {
    @Test fun accounts_are_isolated_and_logout_disposes_only_one() {
        val reg = AccountRegistry()
        val ann = reg.getOrCreate(AccountId("ann"), AccountDetail(AccountId("ann"), "Ann", "a@x", "u"))
        val raj = reg.getOrCreate(AccountId("raj"), AccountDetail(AccountId("raj"), "Raj", "r@x", "u"))
        ann.dispatch(Navigate(Route.Settings))
        assertEquals(Route.Settings, ann.state.get(NavModel::class).route)
        assertEquals(Route.BoardList, raj.state.get(NavModel::class).route) // raj untouched
        reg.dispose(AccountId("ann"))
        assertNull(reg.peek(AccountId("ann")))
        assertNotNull(reg.peek(AccountId("raj")))
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement.** `createAccountStore(detail): Store<ModelState>` registers the always-present per-account models (Session, Nav, BoardList, plus Sync/Activity as needed) via `createConcurrentModelStore`. `AccountRegistry` wraps `StoreRegistry<AccountId, ModelState>` from the bundle: `getOrCreate(id, detail)` → `registry.getOrCreateConcurrentModelStore(id) { ...models... }`; `peek(id)`; `dispose(id)` removes + cancels that store's bot/effects `CoroutineScope`. Confirm `StoreRegistry` + `getOrCreateConcurrentModelStore` symbols via `redux-kotlin-bundle/README.md` and `redux-kotlin-registry`.

- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "feat(taskflow): per-account store factory + AccountRegistry isolation"`

### Task 16: Board model inject/eject

**Files:**
- Create: `.../store/BoardModelInjection.kt`
- Test: `.../commonTest/.../store/BoardModelInjectionTest.kt`

- [ ] **Step 1: Failing test — entering a board injects Board/Filter/Undo models; leaving ejects them.**

```kotlin
@Test fun inject_then_eject_board_models() {
    val store = createAccountStore(AccountDetail(AccountId("ann"), "Ann", "a@x", "u"))
    injectBoardModels(store, BoardId("b1"))
    assertNotNull(store.state.peek(BoardModel::class))     // peek = nullable get helper
    ejectBoardModels(store)
    assertNull(store.state.peek(BoardModel::class))
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement.** `injectBoardModels(store, boardId)` rebuilds the `ModelState` with `with(BoardModel::class, null-or-loading)` + Filter/Undo/Sync, and `store.replaceReducer(combineModelReducers(currentEntries + boardEntries))`, then dispatches `LoadBoardRequested(boardId)`. `ejectBoardModels(store)` rebuilds `ModelState.of(remainingModels)` without the board slice and `replaceReducer` with the base entries. Provide a `ModelState.peek(kclass)` extension (try/catch around `get`, or check the internal map via a small helper) since `ModelState` exposes `get` but not a nullable accessor. Confirm `ModelState.with` / `of` / `combineModelReducers` / `Store.replaceReducer` against `redux-kotlin-multimodel` + `redux-kotlin` APIs.

- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "feat(taskflow): dynamic board-model inject/eject via replaceReducer"`

---

## Phase 7 — Compose theme (tokens → MaterialExpressiveTheme)

### Task 17: Color, Type, Shape, Motion, Dimens, Theme

**Files:**
- Create: `.../ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, `Motion.kt`, `Dimens.kt`, `Theme.kt`

- [ ] **Step 1: `Color.kt`** — light & dark `ColorScheme`s built from the exact role values in `spec-data.js` (`lightScheme`/`darkScheme` arrays). Also expose the semantic label + WIP-state colors as a small `TaskFlowSemanticColors` holder via `staticCompositionLocalOf`.

```kotlin
package org.reduxkotlin.sample.taskflow.ui.theme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val LightColors = lightColorScheme(
    primary = Color(0xFF4A3FB8), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E0FF), onPrimaryContainer = Color(0xFF1C0F5B),
    secondary = Color(0xFF5D5D74), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E1F4), onSecondaryContainer = Color(0xFF1A1A2E),
    tertiary = Color(0xFF7E5260), tertiaryContainer = Color(0xFFFFD9E2), onTertiaryContainer = Color(0xFF31101D),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFBFE), onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE), onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EB), onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4CF),
    // surfaceContainer* per spec-data.js
)
// DarkColors = darkColorScheme(...) with the darkScheme values; fill identically.
```

- [ ] **Step 2: `Type.kt`** — a `Typography` mapping the 15-role scale in `spec-data.js` (`type` array): size/line/tracking/weight. Roboto Flex via `compose.components.resources` font, or `FontFamily.Default` if the variable font isn't bundled. (Bundling Roboto Flex is optional polish; default family is acceptable for v1.)

- [ ] **Step 3: `Shape.kt`** — `Shapes(extraSmall=4.dp, small=8.dp, medium=12.dp, large=16.dp, extraLarge=28.dp)` plus extra named shapes (largeIncreased=20.dp, extraLargeIncreased=32.dp, xxl=48.dp) as top-level vals per the shape scale.

- [ ] **Step 4: `Motion.kt`** — spring spec constants from the `springs` table (damping/stiffness pairs): `SpatialFast = spring(0.6f, 800f)`, `SpatialDefault = spring(0.8f, 380f)`, `SpatialSlow = spring(0.8f, 200f)`, `EffectsFast = spring(1f, 3800f)`, `EffectsDefault = spring(1f, 1600f)`, `EffectsSlow = spring(1f, 800f)`. Prefer `MotionScheme.expressive()` where the API exposes it; keep these as the documented fallback + for explicit `animate*` calls.

- [ ] **Step 5: `Dimens.kt`** — spacing scale (`space0..space16` = 0,4,8,12,16,20,24,28,32,40,48,64 dp) and breakpoints (`CompactMax = 600.dp`, `ExpandedMin = 840.dp`).

- [ ] **Step 6: `Theme.kt`** — `@Composable fun TaskFlowTheme(theme: Theme, content)`:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TaskFlowTheme(theme: Theme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        Theme.System -> isSystemInDarkTheme()
        Theme.Light -> false
        Theme.Dark -> true
    }
    val colors = if (dark) DarkColors else LightColors
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = TaskFlowShapes,
        typography = TaskFlowTypography,
        content = content,
    )
}
```

> If `MaterialExpressiveTheme`/`MotionScheme` are unresolved against the pinned alpha, fall back to `MaterialTheme(colorScheme, shapes, typography)` and use the `Motion.kt` springs directly (documented fallback). Record which path you took in the module README (Task 33).

- [ ] **Step 7: Verify compile + desktop render.** Run: `./gradlew :examples:taskflow:composeApp:compileKotlinJvm`. Then wrap `App()`'s body in `TaskFlowTheme(Theme.System) { ... }` and run `:run`; confirm no crash.

- [ ] **Step 8: Commit.** `git commit -m "feat(taskflow): M3 Expressive theme from hi-fi tokens"`

---

## Phase 8 — Components (the 14-item inventory)

> Each component is a focused composable in `.../ui/components/`. Build them against the `components` entries in `spec-data.js` (shape/elev/color/type/states/variants) and the screen redlines. Components are visually verified on the desktop target; the render-isolation behavior is covered by Phase 11 tests. Build in dependency order; one commit per component or small group.

### Task 18: Avatar + LabelChip

**Files:** Create `.../ui/components/Avatar.kt`, `.../ui/components/LabelChip.kt`

- [ ] **Step 1: `Avatar.kt`** — `@Composable fun Avatar(url: String?, monogram: String, accountId: String, size: Dp, modifier, presence: Boolean = false)`. Uses Coil `SubcomposeAsyncImage`; loading + error fall back to a `Box` with a deterministic tonal background (hash `accountId` → pick from primary/tertiary tones) and the monogram in `Title Medium`. Optional presence dot (positive green `#1E8A5B`). Shape `CircleShape` (squircle `RoundedCornerShape(28%)` for the profile variant). Sizes xs24/sm30/md34/lg56 per inventory.
- [ ] **Step 2: `LabelChip.kt`** — colored chip (semantic label colors from `spec-data.js semantic.labels`), `Small 8dp` shape, `Label Medium`; dashed "add" variant.
- [ ] **Step 3: Verify compile.** `./gradlew :examples:taskflow:composeApp:compileKotlinJvm`
- [ ] **Step 4: Commit.** `git commit -m "feat(taskflow): Avatar + LabelChip components"`

### Task 19: KanbanCard + ColumnHeader (WIP badge)

**Files:** Create `.../ui/components/KanbanCard.kt`, `.../ui/components/ColumnHeader.kt`

- [ ] **Step 1: `KanbanCard.kt`** — `@Composable fun KanbanCard(card: Card, selected: Boolean, optimistic: Boolean, filteredOut: Boolean, onClick)`. Shape `large`/`largeIncreased` (compact), `surfaceContainerLowest` on `surfaceContainer`, Level 1 elevation; `Title Medium` title + clamped markdown/`Body Small` meta footer (📎 count, 🔗 count, assignee `Avatar` xs). States: pressed (shape-morph + L2 — animate via `SpatialFast`), selected (2dp primary outline), optimistic (alpha 0.55 + sync chip), filteredOut (alpha 0.38 then `animateContentSize` collapse via `EffectsDefault`). Optional image attachment via Coil at top.
- [ ] **Step 2: `ColumnHeader.kt`** — title (`Title Small`) + count + WIP badge: container color crossfades ok(`surfaceContainerHigh`)→at-limit(`tertiaryContainer`)→over(`errorContainer`) via `EffectsFast`; one-shot scale pulse (`SpatialFast`) when crossing the limit. Badge shape `Full`, `Label Medium`.
- [ ] **Step 3: Verify compile. Step 4: Commit.** `git commit -m "feat(taskflow): KanbanCard + ColumnHeader/WIP badge"`

### Task 20: FilterBar + MoveToGroup (ButtonGroup) + FabMenu

**Files:** Create `.../ui/components/FilterBar.kt`, `MoveToGroup.kt`, `FabMenu.kt`

- [ ] **Step 1: `FilterBar.kt`** — Expressive `ButtonGroup` (fallback: `Row` of `SegmentedButton`) combining a search field (→ `SetFilterQuery`), a Filter menu, and undo/redo icon buttons (enabled from `UndoModel.past/future` non-empty). Selected segment expands via shape-morph (`SpatialFast`).
- [ ] **Step 2: `MoveToGroup.kt`** — connected `ButtonGroup` ◂ prev / next ▸ (overflow menu when >3 columns); edge columns disabled; dispatches `CardMoveRequested`.
- [ ] **Step 3: `FabMenu.kt`** — `FloatingActionButtonMenu` (fallback: FAB + `ModalBottomSheet`) with Add card / Add column items; trigger↔menu morph (`SpatialFast`), scrim fade (`EffectsDefault`).
- [ ] **Step 4: Verify compile. Step 5: Commit.** `git commit -m "feat(taskflow): FilterBar + MoveToGroup + FabMenu (Expressive ButtonGroup/FAB)"`

### Task 21: MarkdownView + MarkdownEditor + AttachmentChip

**Files:** Create `.../ui/components/MarkdownView.kt`, `MarkdownEditor.kt`, `AttachmentChip.kt`

- [ ] **Step 1: `MarkdownView.kt`** — wrap `com.mikepenz.markdown.m3.Markdown(...)`, passing `imageTransformer = Coil3ImageTransformerImpl`; `Body Large` body, `primary` links, `surfaceVariant` inline code. Clamped-preview variant for card fronts.
- [ ] **Step 2: `MarkdownEditor.kt`** — title `TextField` (`Extra Small 4dp`) + Write/Preview tabs; Write = mono `Body Large` `TextField`; Preview reuses `MarkdownView`. **All text in `remember { mutableStateOf(...) }` — never the store.** Toolbar buttons insert markdown tokens into the field. Exposes `onSave(title, description)`.
- [ ] **Step 3: `AttachmentChip.kt`** — image variant = Coil thumbnail (`Medium 12dp`, L1, shimmer→loaded→error→bundled fallback); link variant = preview card (title + host + optional thumb). Removable variant (edit mode).
- [ ] **Step 4: Verify compile. Step 5: Commit.** `git commit -m "feat(taskflow): MarkdownView + MarkdownEditor + AttachmentChip"`

### Task 22: AccountRow + BoardSummaryCard + SettingsSlider + SyncToast + AdaptiveNav

**Files:** Create `.../ui/components/AccountRow.kt`, `BoardSummaryCard.kt`, `SettingsSlider.kt`, `SyncToast.kt`, `AdaptiveNav.kt`; `.../ui/adaptive/WindowSize.kt`

- [ ] **Step 1: `WindowSize.kt`** — `enum class WindowSize { Compact, Medium, Expanded }` + `@Composable fun rememberWindowSize(maxWidth: Dp): WindowSize` (Compact <600, Medium 600–839, Expanded ≥840). Use `BoxWithConstraints` at the App root to avoid alpha adaptive deps.
- [ ] **Step 2: `AdaptiveNav.kt`** — `NavigationBar` (compact) / `NavigationRail` (expanded) over `NavModel.route`; selected = `secondaryContainer` pill + emphasized `Label Medium`; rail has avatar header + FAB foot.
- [ ] **Step 3: `AccountRow.kt`** — avatar + name (`Title Medium`) + status line (`Body Small`) + active state (`primaryContainer` + primary outline); add-account dashed variant.
- [ ] **Step 4: `BoardSummaryCard.kt`** — accent stripe (per-board `color`), name, counts, progress bar (done/total, positive green), updated-at; hover→L2 (web); loading-skeleton + create-dashed variants.
- [ ] **Step 5: `SettingsSlider.kt`** — M3 (Expressive) slider; primary track (latency range) / error track (failure %); inset value label on drag; writes via callback.
- [ ] **Step 6: `SyncToast.kt`** — `SnackbarHost` content for `SyncModel.lastError` with Retry (re-dispatch original request) + undo-confirm variant; `inverseSurface`/`inverseOnSurface`.
- [ ] **Step 7: Verify compile. Step 8: Commit.** `git commit -m "feat(taskflow): account/board/settings/toast/adaptive-nav components"`

---

## Phase 9 — Screens (build order: Login → Switcher → Board list → Board → Card detail → Profile → Settings)

> Each screen is a composable in `.../ui/screens/` taking the active store(s). Bind state with the bundle's Compose APIs: `rememberStableStore`, `fieldStateOf(KClass){ selector }`, `selectorState{ }`, `subscribeToModel`. Each screen renders compact + expanded per its redline section. Verify each on the desktop target before committing.

### Task 23: Login / Add account

**Files:** Create `.../ui/screens/LoginScreen.kt`

- [ ] **Step 1:** `@Composable fun LoginScreen(appStore: Store<ModelState>)`. Wordmark (`Display Small`, `onPrimaryContainer`) over a 165° `primaryContainer→surface` gradient. Account list card (`surfaceContainerLowest`, `extraLarge 28dp`, L1) of seeded accounts as `AccountRow`s (single-select). Continue button: dispatches `LoginRequested` then `AccountLoggedIn` (after fake latency via effects); label swaps to a 3-dot loader while `AuthFlowModel.inFlight` (animate `EffectsDefault`). Bind `inFlight`/`error` via `selectorState`. Expanded = centered 380dp card; account rows gain hover state.
- [ ] **Step 2: Verify desktop render. Step 3: Commit.** `git commit -m "feat(taskflow): Login/Add-account screen"`

### Task 24: Account switcher

**Files:** Create `.../ui/screens/AccountSwitcherScreen.kt`

- [ ] **Step 1:** Modal bottom sheet (compact, `extraLarge 28dp` top, scrim 32%, enters `SpatialSlow`) / anchored menu (`large 16dp`, L2) on expanded. `AccountRow` per `AccountsModel.accounts`; each row's status line shows that account's remembered screen (read `registry.peek(id)?.state?.get(NavModel::class)`) — proving per-store Nav isolation. Tap → dispatch `SwitchAccount(id)`. "Add account" dashed row → `StartLogin(AddAccount)`. Account-switch transition = fade-through (`EffectsDefault` + `SpatialDefault`) with the avatar as a shared element.
- [ ] **Step 2: Verify desktop render. Step 3: Commit.** `git commit -m "feat(taskflow): account switcher (registry isolation surface)"`

### Task 25: Board list

**Files:** Create `.../ui/screens/BoardListScreen.kt`

- [ ] **Step 1:** Title (`Headline Medium` emphasized, `onPrimaryContainer`). Grid of `BoardSummaryCard` from `BoardListModel` (bind via `fieldStateOf(BoardListModel::class){ it.order }`); compact 1-col + bottom `NavigationBar`, expanded 3-col (cap 1280dp) + `NavigationRail`. Tap a card → dispatch `Navigate(Route.Board(id))` (App handles injection — Task 30) → skeleton shimmer while `SyncModel` has `load:`. Create tile → `CreateBoard`. On first show dispatch `LoadBoardListRequested`.
- [ ] **Step 2: Verify desktop render. Step 3: Commit.** `git commit -m "feat(taskflow): Board list screen"`

### Task 26: Board (kanban) — hero

**Files:** Create `.../ui/screens/BoardScreen.kt`

- [ ] **Step 1:** Hero app bar (`primaryContainer`, board name `Headline Medium` emphasized) + `FilterBar` + `Avatar` (opens switcher). Columns: compact = horizontally-paged single column; expanded = side-by-side + persistent `Activity` rail (`ActivityModel`). **Each column binds its own slice:** `fieldStateOf(BoardModel::class){ board -> board.columns[i].cardIds }` and maps ids → `board.cards[id]`, so only the two columns touched by a move recompose. WIP badge via `selectorState{ count to limit }`. `KanbanCard` shows optimistic alpha when its op id is in `SyncModel.inFlight`. Apply `FilterModel` to dim/collapse non-matching cards. `FabMenu` (Add card/column). `MoveToGroup` available from a selected card. Motion per the Board choreography table (card move `SpatialDefault`, optimistic `EffectsDefault`, WIP pulse `SpatialFast`, filter `EffectsDefault`). `SyncToast` on `lastError` with Retry. Light + dark per tokens.
- [ ] **Step 2: Verify desktop render** (seed a board, move a card, toggle filter, crank failure to see revert). 
- [ ] **Step 3: Commit.** `git commit -m "feat(taskflow): Board kanban screen (granular per-column subscriptions)"`

### Task 27: Card detail (view / edit / create)

**Files:** Create `.../ui/screens/CardDetailScreen.kt`

- [ ] **Step 1:** Side sheet (expanded, 330dp, container-transform from card via `SpatialDefault`) / full screen (compact). Bind the open card via `subscribeToModel(BoardModel)` keyed on `NavModel.openCardId`. **View:** mode header (`primaryContainer` + ✎ Edit chip), title (`Headline Small`), `MarkdownView`, `AttachmentChip`s, `LabelChip`s, assignee `Avatar`, `MoveToGroup`. **Edit/Create:** `MarkdownEditor` (local state), label/attach/assign inline actions, Save → dispatch `EditCard`/`AddCard` (undoable) then `CloseCard`. IME-aware on Android; Esc closes on web.
- [ ] **Step 2: Verify desktop render (open, edit, save, undo). Step 3: Commit.** `git commit -m "feat(taskflow): Card detail (view/edit/create modes)"`

### Task 28: Profile

**Files:** Create `.../ui/screens/ProfileScreen.kt`

- [ ] **Step 1:** Cover band (`primaryContainer`, 64dp) + 56dp squircle `Avatar` breaking the band. Detail card (`surfaceContainerLowest`, `large 16dp`) with editable rows (✎ → `EditProfile`) split by `outlineVariant`. Stats. Log out = outlined `error` button → confirm dialog → dispatch `LogoutAccount(id)` (App disposes the store — Task 30). Expanded = two-column (detail + read-only activity 230dp). Bind via `subscribeToModel(SessionModel)`.
- [ ] **Step 2: Verify desktop render. Step 3: Commit.** `git commit -m "feat(taskflow): Profile screen"`

### Task 29: Settings

**Files:** Create `.../ui/screens/SettingsScreen.kt`

- [ ] **Step 1:** Bind `AppSettingsModel` from the **root** store via `selectorState`. APPEARANCE: theme `ButtonGroup` (System/Light/Dark → `SetTheme`; drives `TaskFlowTheme`; crossfade `EffectsSlow`). FAKE BACKEND: latency `SettingsSlider` (range → `SetLatency`), failure-rate `SettingsSlider` (error track → `SetFailureRate`), bot `Switch` (→ `SetBotEnabled`). Compact list / expanded centered 520–640dp column (keyboard-steppable sliders).
- [ ] **Step 2: Verify desktop render (toggle theme live; crank failure then watch Board revert). Step 3: Commit.** `git commit -m "feat(taskflow): Settings screen with live fake-backend knobs"`

---

## Phase 10 — App composition & navigation

### Task 30: Real `App()` — theme + active-store binding + nav routing + inject/eject

**Files:** Modify `.../App.kt`

- [ ] **Step 1:** Replace the placeholder with the real root:
  - `remember { createAppStore() }`; `rememberStableStore(appStore)`.
  - Read `AccountsModel.activeAccountId` + the `AccountRegistry` (held in `remember`); when null → `LoginScreen`. Otherwise `val accountStore = registry.getOrCreate(activeId, detail)`; `rememberStableStore(accountStore)`.
  - Wrap everything in `TaskFlowTheme(theme = appSettings.theme)`.
  - `BoxWithConstraints` → `rememberWindowSize` → pass to screens.
  - Route on the **active account's** `NavModel.route` via `selectorState`: `BoardList`→`BoardListScreen`, `Board(id)`→`BoardScreen`, `Profile`→`ProfileScreen`, `Settings`→`SettingsScreen`; `AdaptiveNav` shell around them; `CardDetailScreen` overlaid when `openCardId != null`.
  - **Inject/eject hook:** `LaunchedEffect(activeId, route)` — on entering `Route.Board(id)` call `injectBoardModels(accountStore, id)`; on leaving call `ejectBoardModels(accountStore)`.
  - **Logout hook:** observe account list; when an account disappears, `registry.dispose(id)`.
  - Start the bot collaborator per active account (`startBot`) scoped to the account store; cancel on dispose.
- [ ] **Step 2: Verify the full flow on desktop:** login → board list → open board (loads w/ shimmer) → move card → open card → edit → undo → switch/add account (isolation) → settings (theme + failure) → logout. Run `./gradlew :examples:taskflow:composeApp:run`.
- [ ] **Step 3: Verify web build:** `./gradlew :examples:taskflow:composeApp:wasmJsBrowserDistribution`.
- [ ] **Step 4: Commit.** `git commit -m "feat(taskflow): wire App() — theme, active-store binding, nav, inject/eject, bot"`

---

## Phase 11 — UI tests (the showcase claims, proven)

### Task 31: Render-isolation proof (compose.uiTest, jvm)

**Files:** Create `.../jvmTest/.../ui/RenderIsolationTest.kt`

- [ ] **Step 1: Failing test** — render `BoardScreen` against a store with a 3-column board; instrument recomposition counts per column (a `SideEffect { counts[colId]++ }` injected via a test tag/composition local). Dispatch `CardMoveRequested` moving a card between column A and B; assert columns A and B recomposed and column C did **not**.

```kotlin
@OptIn(ExperimentalTestApi::class)
@Test fun moving_a_card_recomposes_only_two_columns() = runComposeUiTest {
    // setContent { TaskFlowTheme(Theme.Light) { BoardScreen(testStore) } }
    // record counts; testStore.dispatch(CardMoveRequested(...)); waitForIdle()
    // assertEquals(initialC, counts[ColumnId("done")]) // unchanged
    // assertTrue(counts[ColumnId("todo")]!! > initialTodo && counts[ColumnId("doing")]!! > initialDoing)
}
```

- [ ] **Step 2: Run, expect FAIL** (until counting harness + screen wired). Run: `./gradlew :examples:taskflow:composeApp:jvmTest --tests "*RenderIsolationTest"`
- [ ] **Step 3: Implement the counting harness** (a `LocalRecompositionCounter` composition local the column composable increments) and make the assertion pass. This is the literal proof of the `fieldStateOf` granular-subscription claim.
- [ ] **Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "test(taskflow): prove per-column render isolation on card move"`

### Task 32: Account-switch restores each account's screen (compose.uiTest, jvm)

**Files:** Create `.../jvmTest/.../ui/AccountSwitchTest.kt`

- [ ] **Step 1: Failing test** — render `App()` with two seeded logged-in accounts; navigate account A to `Settings`, account B to `Board`; switch A→B→A; assert each shows its own remembered route (NavModel isolation).
- [ ] **Step 2: Run, expect FAIL. Step 3: Implement/verify. Step 4: Run, expect PASS. Step 5: Commit.** `git commit -m "test(taskflow): account switch restores per-account screen"`

---

## Phase 12 — Assets, docs, final gate

### Task 33: Bundled image fallbacks + README

**Files:** Create `composeResources/files/avatars/*.svg`, `composeResources/files/cards/*.jpg`; `examples/taskflow/README.md`

- [ ] **Step 1:** Add 3–4 bundled card JPEGs + a small avatar SVG set; wire Coil error/empty to the bundled fallback (per the asset policy: loading shimmer → loaded → error→bundled → success crossfade). Confirm web (CORS/offline) still shows images.
- [ ] **Step 2:** Write `examples/taskflow/README.md`: what it demonstrates (map each bundle API → where it's used), how to run each target (`:run` desktop, `wasmJsBrowserRun` web, Android via `androidApp`, iOS via the Xcode project), and which M3-Expressive path was taken (alpha artifact vs documented fallback).
- [ ] **Step 3: Commit.** `git commit -m "docs(taskflow): bundled image fallbacks + module README"`

### Task 34: iOS host project

**Files:** Create `examples/taskflow/iosApp/` (mirror `examples/counter/ios/Sources`)

- [ ] **Step 1:** Add a minimal Xcode project / Swift host embedding the `TaskFlowApp` framework and presenting `MainViewController()`. Mirror the structure of `examples/counter/ios`.
- [ ] **Step 2: Verify the framework links** (Mac + Xcode iOS SDK): `./gradlew :examples:taskflow:composeApp:linkDebugFrameworkIosSimulatorArm64` — Expected: BUILD SUCCESSFUL (environmental per CLAUDE.md; trust CI if the local SDK is absent).
- [ ] **Step 3: Commit.** `git commit -m "feat(taskflow): iOS host app embedding TaskFlowApp framework"`

### Task 35: Full build + detekt gate

- [ ] **Step 1: detekt.** Run: `./gradlew detektAll` — Expected: no violations (the commit hook also runs `detektAll --auto-correct`; if it rewrites files, re-stage and re-commit). Never `--no-verify`.
- [ ] **Step 2: All host-runnable targets.** Run: `./gradlew :examples:taskflow:composeApp:jvmTest :examples:taskflow:composeApp:wasmJsBrowserDistribution :examples:taskflow:composeApp:compileKotlinJvm` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Whole-repo build.** Run: `./gradlew build` — Expected: BUILD SUCCESSFUL (existing modules' `apiCheck` unaffected; the sample is `convention.control`, not published).
- [ ] **Step 4: Final commit if anything changed.** `git commit -am "chore(taskflow): pass detekt + full build gate"`

---

## Self-review (completed by plan author)

**Spec coverage:** All 7 screens (Tasks 23–29), two-layer topology (14–16), inject/eject (16), middleware pipeline activityLogger/undo/effects/bot (12–13), normalized rich cards (5,9), optimistic + revert (9,12), undo/redo (9,13), fake backend latency/failure (10), seeded stock images (11,33), M3 Expressive theme + 14 components (17–22), adaptive compact/expanded (22,30), motion springs (17 + per-screen), render-isolation + account-switch proofs (31–32), all platforms (3,30,34), detekt gate checkpoints (each commit + 35). Resolved deps lead the plan.

**Placeholder scan:** Logic tasks (Phases 1–6) carry complete code + TDD. UI tasks (8–9) specify signature + tokens + states + redline refs + a desktop-render verification rather than full pixel code — deliberate for a UI sample of this size; each cites `spec-data.js`/Screens Spec as the per-element source of truth, so there is no ambiguity about target appearance. Seed content (Task 11) and bundled assets (33) are constrained by tests/asset table rather than literal bytes.

**Type consistency:** Action names, model names, reducer fn names, and store/registry/injection symbols are used consistently across tasks (`CardMoveRequested`, `CardOpFailed.revertTo`, `opIdOf`, `injectBoardModels`/`ejectBoardModels`, `AccountRegistry.getOrCreate/peek/dispose`, `createAppStore`/`createAccountStore`).

**Known verification points for the executor (call out, don't guess):** exact public surfaces of `redux-kotlin` (`Middleware`, `Store.replaceReducer`, `dispatch`), `redux-kotlin-routing` (routed-reducer DSL), `redux-kotlin-multimodel` (`ModelState.with/of/get`, `combineModelReducers`), `redux-kotlin-registry`/bundle (`StoreRegistry`, `getOrCreateConcurrentModelStore`, `createConcurrentModelStore`), and `redux-kotlin-bundle-compose` (`rememberStableStore`, `fieldStateOf`, `selectorState`, `subscribeToModel`). Tasks 7, 12, 14–16 each say to confirm these against the module READMEs/`api/` dumps before wiring — do that first in each task.
