# TaskFlow Sample App — Implementation Plan (v3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build TaskFlow — a Compose Multiplatform (Android / iOS / Web-wasmJs / JVM-desktop) Kanban sample that showcases `redux-kotlin-bundle` + `redux-kotlin-bundle-compose` end-to-end, with **local SQLite persistence** and **deeply immutable** Redux state.

**Architecture:** Root `ConcurrentModelStore` (accounts, settings, auth) + `AccountRegistry: StoreRegistry<AccountId, ModelState>` (one isolated store per logged-in account). Per-account models are **fixed-slot** (declared up front; board slices start at a `NotLoaded` sentinel and reset on leave — no runtime model injection, which `ModelState` forbids). Reducers are authored in the **routing DSL** (`model{ on<Action>{} }`). Persistence and network are **two distinct layers**: a durable **LocalStore** (SQLDelight, no latency), a replaceable fake **RemoteApi** (latency/failure/offline toggle), and an offline-first **SyncEngine** between them (optimistic local write → persisted outbound queue → push/pull). All card mutations are optimistic + undoable + persisted-locally; a *rejected* remote push reverts via **per-op inverse** (never a whole-board snapshot); offline edits queue and drain on reconnect. Nav is Redux state. All collections are `kotlinx.collections.immutable`.

**Tech Stack (pinned, verified May 2026):** Kotlin 2.3.20; Compose Multiplatform **1.11.0** (Expressive via `compose.material3`); `kotlinx-collections-immutable 0.5.0-beta01`; `kotlinx-datetime 0.6.2`; Coil `coil-compose`+`coil-network-ktor3` `3.2.0` + Ktor `3.1.0` engines; `multiplatform-markdown-renderer-m3/-coil3 0.4x` (Compose-1.11 build); SQLDelight `2.3.2`.

---

## Source-of-truth artifacts (read first)

- `docs/superpowers/specs/2026-05-29-taskflow-bundle-sample-design.md` — architecture (authoritative; §3 fixed-slot topology, §4 data flow, §5 immutable schema, §6 assets, §13 persistence).
- `docs/superpowers/specs/Redux-kotlin kanban specs/spec-assets/spec-data.js` — tokens (colors light+dark, type, shape, spacing, elevation, motion springs), 14-component inventory.
- `docs/superpowers/specs/Redux-kotlin kanban specs/TaskFlow Screens Spec.html` — per-screen redlines, motion choreography.
- `CLAUDE.md` — detekt gate (`detektAll --auto-correct` on commit; never `--no-verify`), conventions, KMP targets.

## Verified library facts the plan depends on (don't relitigate; confirm signatures before wiring)

- `ModelState` (redux-kotlin-multimodel) is **immutable-keyed**: `with`/`withAll` throw on an undeclared class; widening ctor is `@PublishedApi internal`. → **No runtime inject/eject.** Declare all slots up front; "close board" = reset to sentinel.
- `createConcurrentModelStore { block: RoutingBuilder.() -> Unit }` and `StoreRegistry<K,ModelState>.getOrCreateConcurrentModelStore(...)` **exist on the bundle branch** (`redux-kotlin-bundle`), taking the routing DSL. Reducers are `model(Init()) { on<ConcreteAction> { state, action -> next } }`; `on<A>` routes by **exact leaf class**. The store is **not** built with `combineModelReducers`.
- `StoreRegistry` API: `getOrCreate(K){ creator }`, `get(K): TypedStore?`, `remove(K): Boolean` (there is no `dispose`/`peek`).
- Compose bindings (bundle-compose): `rememberStableStore(store): StableStore<S>` (unwrap `.value` to get the `TypedStore` before extensions), `selectorState { … }: State<T>`, `Store<ModelState>.fieldStateOf(modelClass, selector): State<T>`. `subscribeToModel(...)` returns an **unsubscribe fn (not `State`)** — use it only for imperative listeners.
- `Middleware<S> = (TypedStore<S,Any>) -> (next:(Any)->Any) -> (action:Any)->Any`; dispatch via `store.dispatch(action)`. Prefer the `middleware<ModelState> { store, next, action -> }` builder.

## Platform-parity rule (applies to every task)

Where a library/API is unavailable on web/iOS, **do not drop the feature on Android/iOS/jvm** — shim via `expect/actual`. Android/iOS/jvm get the full implementation; only the web target degrades, and only where unavoidable, documented in the README.

## v3 corrections — CROSS-CUTTING RULES (apply to every relevant task; from the 2nd multi-agent review)

These override anything below that conflicts. They were verified against source/release notes.

**A. Compose 1.11.0 target removal (build).** Compose MP 1.11.0 **deleted `iosX64` + `macosX64`** from all Compose modules. Therefore: remove `iosX64()` + `macosX64()` from **`convention.mpp-loved.gradle.kts`** (lines ~48–49) repo-wide; the sample targets **`iosArm64` + `iosSimulatorArm64` only** (no `iosX64`). After the bump, run **root `./gradlew apiDump`** (every module's klib `// Targets:` header changes — not just the 3 compose modules) and commit all `*.api`. `compileSdk = 36` (both composeApp `android{}` and `androidApp`). Bump `multiplatform-markdown-renderer` to the **0.4x** release built for Compose 1.11.0 (verify in its release notes; 0.39.0 targets 1.10.x and trips 1.11's runtime compat check).

**B. Real library-API names (verified — these break compile as previously written).**
- `ModelState.models` is `@PublishedApi internal` → **read via the public `get(KClass)`** only. `inline fun <reified M> ModelState.getModel(): M = get(M::class)`. There is **no** nullable `peek`; fixed slots mean `get` always succeeds.
- `StoreRegistry.getOrCreate(id){ creator }` returns `Store<S>` only — it **cannot** hold the `AccountStoreHandle` (scope/engine/bot). `AccountRegistry` keeps a **side `MutableMap<AccountId, AccountStoreHandle>`** alongside the registry; `remove(id)` = `handle.scope.cancel()` + side-map remove + `registry.remove(id)`.
- Compose binding shapes: single flat field → `store.fieldState(Model::field)` (reified `KProperty1`); derived/multi-field → `store.selectorState { it.get<M>().… }` (the lambda receives `ModelState` — **never** write `selectorState { SomeModel }`, that returns the class ref); whole-model slot → `store.fieldStateOf(M::class) { it }`. `subscribeToModel(...)` returns a `StoreSubscription` (imperative) — **never** use it for Compose binding; card detail binds `fieldStateOf(BoardModel::class){ it.board?.cards?.get(openCardId) }`.

**C. Compose ↔ store wiring discipline (every component + screen).**
- [ ] Read state ONLY via `rememberStableStore(store).value` then `fieldState`/`selectorState`/`fieldStateOf`. Never `store.state` in composition.
- [ ] Select the **minimal slice**; **no composable may select `board`, `board.cards`, or `board.columns` wholesale** (collapses render isolation).
- [ ] Per-column card lists bind **by `ColumnId`** (`it.board?.columnById(colId)?.cardIds ?: persistentListOf()`), **not by index**, and each column composable is wrapped in `key(colId)` — in the **real Board screen**, not only the isolation test.
- [ ] Selectors return **referentially-stable** values when the slice is unchanged (primitives, the existing `Persistent*`/`Card` instance, or value-equal `data class`) — never a freshly `filter`/`map`ped collection or a closure-per-emission. (Selectors re-run on **every** dispatch; only `===`/`==`-stable returns prevent recomposition.)
- [ ] **All derivation** (filtered/visible lists, WIP counts, per-card optimistic flag) lives in `selectorState{}` selectors or reducers — never `.filter`/`.count`/membership in the composable body.
- [ ] Components take `@Stable`/`Persistent*` params + **remembered** callbacks; **never pass a raw `Store`/`TypedStore`** into a child (pass `StableStore` or finished data) — else skippability breaks.
- [ ] Every user interaction fires `store.dispatch(action)` (move buttons, toggles, Save, retry, nav, offline switch). No repository/SyncEngine/Coil-imperative call from a composable; only transient editor text lives in local `remember`.

**D. State-model best-practice (single source of truth + purity).**
- `SyncModel.inFlight` is a **`PersistentSet<CardId>`** (not `OpId`) so the board can compute the optimistic alpha per card. `online` is a **one-way projection** of `AppSettingsModel.fakeService.online` (updated only via `SyncStatusChanged`) — or drop it and read root settings; never write it independently.
- **Collapse 3-way identity duplication:** `CollaboratorsModel` (which includes self) is the per-account source for display name/email/avatar; `SessionModel` holds only `AccountId` + session-only `bio`. `EditProfile` propagates to root `AccountsModel` **and** `CollaboratorsModel` (else the switcher/cards go stale).
- `BoardSummary` counts/`updatedAt`: **derive in a selector** for the open board; for the list, treat `BoardListModel` counts as a DB-aggregate cache refreshed on `LoadBoardListSucceeded`. **Never call `Clock.now()` in a reducer** — `updatedAt` comes from the action's pre-minted `now`.
- Reducers stay pure: all `OpId`/`CardId`/`Instant` minting and all logging/persistence/sync/bot/time live in middleware or the dispatch site (via an injected `IdGenerator` + `Clock`), never a reducer.

**E. Threading.** Construct each `ConcurrentModelStore` with a **main-thread `NotificationContext`** (default is `Inline` = dispatch thread). Effects/sync/bot run on a background scope but must `withContext(Main)` before `store.dispatch` — subscribers write Compose state, which is UB off-main on wasm/iOS.

**F. Sync performance.** `RemoteApi.pull` **early-returns** without dispatch on an empty page; `applyRemote` merges by **changed key only** (`PersistentMap.put` on changed rows; reuse unchanged `Card`/`Column` instances). Gate `SyncStatusChanged` to actual deltas. Periodic `Refresh` tick ≥ 10 s (a new `FakeServiceConfig.syncIntervalMs`), keyed so it cancels on `BoardClosed`. Use `AsyncImage` (not `SubcomposeAsyncImage`) for list items with fixed dimensions.

**G. New required artifacts (don't let an autonomous run guess).**
- **`pending_op.payload` codec:** add `org.jetbrains.kotlin.plugin.serialization` + `kotlinx-serialization-json` to the catalog; define a `@Serializable sealed interface SyncOp` (one variant per card mutation, carrying everything needed to push **and** the `InverseOp`), serialized to the `pending_op.payload` TEXT column. The engine reconstructs the inverse from the queued `SyncOp` on `Rejected` (the inverse **rides the queued op**, not a middleware-side map).
- **`IdGenerator`** interface (`newOpId(): OpId`, `newCardId(): CardId`, `newBoardId()`, `newColumnId()`) over `platform/newUuid()`; a `fakeIdGenerator(seq)` for tests; injected into `App()` and passed to screens via a `CompositionLocal`. Screens build `AddCard(col, idGen.newCardId(), …, idGen.newOpId(), clock.now())` at the click handler.
- **`IdGenerator`/`Clock` are NOT defaults on actions** — mint explicitly at the dispatch site.
- **Process-death rehydration:** the bootstrap effect loads `activeAccountId` (persist it in `app_settings`) + each account's `account_nav` route/openCard from the DB into the store on launch.
- **Enumerate ALL actions in Task 7 up front** (incl. `SyncStatusChanged`, `Refresh`, `SetOnline`, `RetryOp(opId)`, `AddColumn`, `BoardRestored`, `StartCreateCard`/`CancelCreateCard`, `LoadAccountsFailed`) — no "// ..." and no later back-edits.
- **SeedData is concrete:** ann/raj/mia + bot; the 6 semantic labels with `spec-data.js` colors; per account one board / three columns with **one column whose `wipLimit` is at its limit in seed** (so the Rejected-conflict test reproduces); ≥1 markdown card, ≥1 image, ≥1 link, ≥1 labelled; a fixed `Instant` constant. `LocalStore` seed and `FakeRemoteApi` server snapshot consume the **same** `SeedData`.

**H. Misc completeness.** `Theme.System` resolves via `isSystemInDarkTheme()` (Compose-common). Add `contentDescription` to `Avatar`/icon buttons/`FabMenu` (a11y, P0 UX). Decide strings: literals-only for v1, noted in README. iOS host = **framework-link + a `swiftc` smoke compile, Mac/CI-gated**; the full `.xcodeproj` app is a documented manual follow-up (do not author a `project.pbxproj` autonomously). iOS resource bundling uses `embedAndSignAppleFrameworkForXcode` (auto-syncs compose resources) — **no** hand-rolled copy script.

## File structure

```
examples/taskflow/
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/org/reduxkotlin/sample/taskflow/
│       │   │   ├── App.kt
│       │   │   ├── platform/                  # expect declarations
│       │   │   │   ├── DriverFactory.kt        # expect class DriverFactory
│       │   │   │   ├── HttpEngine.kt           # expect fun ktorEngine(): HttpClientEngine? (null on wasm)
│       │   │   │   ├── DynamicColor.kt         # expect fun dynamicColorScheme(dark): ColorScheme?
│       │   │   │   └── Ids.kt                  # expect fun newUuid(): String  (IdGenerator backing)
│       │   │   ├── model/ Ids.kt RootModels.kt AccountModels.kt BoardModels.kt BoardSlice.kt
│       │   │   ├── action/Actions.kt
│       │   │   ├── reducer/ RootReducers.kt AccountReducers.kt BoardReducers.kt
│       │   │   ├── db/ TaskFlowDb.sq (commonMain/sqldelight/...) Adapters.kt
│       │   │   ├── data/local/ LocalStore.kt SqlDelightLocalStore.kt   # durable, no latency, pending-op queue
│       │   │   ├── data/remote/ RemoteApi.kt FakeRemoteApi.kt SyncOp.kt PushResult.kt  # fake network; real-backend seam
│       │   │   ├── data/sync/ SyncEngine.kt SyncRepository.kt          # offline-first orchestration
│       │   │   ├── data/SeedData.kt
│       │   │   ├── middleware/ EffectsMiddleware.kt UndoMiddleware.kt ActivityLoggerMiddleware.kt BotCollaborator.kt
│       │   │   ├── store/ AppStore.kt AccountStore.kt AccountRegistry.kt StoreExt.kt
│       │   │   └── ui/ theme/ adaptive/ components/ screens/ image/ImageLoader.kt
│       │   └── composeResources/files/{avatars,cards}/
│       ├── commonTest/kotlin/...              # reducers, middleware, repository, store, isolation
│       ├── androidMain/kotlin/...             # actual DriverFactory/HttpEngine/DynamicColor/newUuid
│       ├── jvmMain/kotlin/{main.kt, actual ...}
│       ├── iosMain/kotlin/{MainViewController.kt, actual ...}
│       ├── wasmJsMain/{kotlin/{main.kt, actual ...}, resources/index.html}
│       └── jvmTest/kotlin/...                 # compose.uiTest
├── androidApp/                                # com.android.application (hosts MainActivity)
└── iosApp/                                    # framework-link + swiftc smoke (full .xcodeproj = manual follow-up)
```

---

## Phase 0 — Repo prep, deps, and a build-the-stack spike

### Task 0: Sync to master (bundle), bump Compose 1.11.0, drop x64 targets, regen ALL api dumps

**Files:** merge from `origin/master`, `gradle/libs.versions.toml`, `build-conventions/src/main/kotlin/convention.mpp-loved.gradle.kts` (delete `iosX64()`/`macosX64()` — the actual target-declaration site), regenerated `*.api` dumps for **all** modules.

- [ ] **Step 1: Sync onto master (DONE — bundle merged).** `origin/master` already merged into this branch; `:redux-kotlin-bundle` + `:redux-kotlin-bundle-compose` are in `settings.gradle.kts` with `src/` and `./gradlew :redux-kotlin-bundle-compose:jvmJar` passes. (Do NOT merge `feat/redux-kotlin-bundle` — stale.)
- [ ] **Step 2: Bump Compose.** `compose-multiplatform = "1.11.0"` in the catalog.
- [ ] **Step 3: Remove the x64 Compose targets repo-wide.** Compose 1.11.0 deleted `iosX64` + `macosX64`. In **`build-conventions/src/main/kotlin/convention.mpp-loved.gradle.kts`** delete `iosX64()` (line ~48) and `macosX64()` (line ~49) (keep `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `linuxX64`, `mingwX64`). `convention.mpp-all` only adds `linuxArm64()` — nothing to remove there. `convention.mpp-loved` is the base for ALL loved modules + (via mpp-all) the core, so this is the intended uniform repo-wide removal.
- [ ] **Step 4: Add markdown-renderer to the catalog.** It is NOT yet a catalog key — ADD `markdown-renderer = "<0.4x>"` to `[versions]` (the latest `0.4x` whose release notes state Compose MP 1.11.0; NOT 0.39.0).
- [ ] **Step 5: Regenerate EVERY module's API dump** (the target removal changes all klib `// Targets:` headers, not just compose): `./gradlew apiDump` (root). Then `./gradlew build` → must be green (incl. `apiCheck`). Commit all updated `*.api`.
- [ ] **Step 6: Commit.** `git add -A && git commit -m "build: merge master(bundle); Compose 1.11.0; drop iosX64/macosX64; regen all api dumps"`

### Task 1: Version catalog + settings registration

**Files:** `gradle/libs.versions.toml`, `settings.gradle.kts`

- [ ] **Step 1: Add versions** to `[versions]`:

```toml
kotlinx-collections-immutable = "0.5.0-beta01"
kotlinx-datetime = "0.6.2"
kotlinx-serialization = "1.7.3"   # verify vs Kotlin 2.3.20; for pending_op payload codec
coil = "3.2.0"
ktor = "3.1.0"
markdown-renderer = "0.40.0"   # ADD: verify the exact 0.4x release built for Compose MP 1.11.0 (NOT 0.39.0)
androidx-activity-compose = "1.11.0"   # dedicated; not coupled to androidx-activity (activity-ktx)
sqldelight = "2.3.2"
```

- [ ] **Step 2: Add libraries** to `[libraries]`:

```toml
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinx-collections-immutable" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor3 = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-java = { module = "io.ktor:ktor-client-java", version.ref = "ktor" }
ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }
markdown-renderer-m3 = { module = "com.mikepenz:multiplatform-markdown-renderer-m3", version.ref = "markdown-renderer" }
markdown-renderer-coil3 = { module = "com.mikepenz:multiplatform-markdown-renderer-coil3", version.ref = "markdown-renderer" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity-compose" }
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-web-worker-driver = { module = "app.cash.sqldelight:web-worker-driver", version.ref = "sqldelight" }
```

- [ ] **Step 3: Add plugins** to `[plugins]`:

```toml
android-kotlin-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "android-gradle-plugin" }
android-application = { id = "com.android.application", version.ref = "android-gradle-plugin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

> Also add `devNpm("copy-webpack-plugin", "9.1.0")` to `wasmJsMain` (Task 2) — required for the SQLDelight sql.js worker asset copy (Task 2 webpack step).

- [ ] **Step 4: Register modules.** In `settings.gradle.kts` `include(...)`, near the other `:examples:*` entries add `":examples:taskflow:composeApp",`. Then gate the app module (it needs the Android SDK) — after the `include(...)` block:

```kotlin
val hasAndroidSdk = file("local.properties").let { it.exists() && it.readText().contains("sdk.dir=") } ||
    System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) include(":examples:taskflow:androidApp")
```

- [ ] **Step 5: Commit.** `git add settings.gradle.kts gradle/libs.versions.toml && git commit -m "build(taskflow): pin deps + register modules (androidApp SDK-gated)"`

### Task 2: composeApp build file (all targets + deps + sqldelight)

**Files:** Create `examples/taskflow/composeApp/build.gradle.kts`

- [ ] **Step 1: Write it.** Android via the AGP-9 KMP library plugin using the **`android {}`** DSL (the renamed block; `androidLibrary{}` is deprecated at 9.1+), SDK-gated, `compileSdk = 36`; wasmJs executable; **iOS = `iosArm64` + `iosSimulatorArm64` only (no `iosX64` — Compose 1.11.0 dropped it)**; SQLDelight `generateAsync`; desktop `application` block; Ktor engines per target; kotlinx-serialization for the sync-op payload; **no `js{}` target**.

```kotlin
import org.jetbrains.compose.ExperimentalComposeLibrary

val hasAndroidSdk: Boolean = run {
    val p = rootProject.file("local.properties")
    (p.exists() && p.readText().lineSequence().any { it.trim().startsWith("sdk.dir=") }) ||
        !System.getenv("ANDROID_HOME").isNullOrBlank() || !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
}

plugins {
    id("convention.control")
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
    // NOTE: do NOT put the android plugin here — it can't be conditional in plugins{}.
}
// The `kotlin { android { } }` block is provided ONLY by this plugin; apply it (gated) outside plugins{}.
if (hasAndroidSdk) apply(plugin = "com.android.kotlin.multiplatform.library")

sqldelight {
    databases.create("TaskFlowDb") {
        packageName.set("org.reduxkotlin.sample.taskflow.db")
        generateAsync.set(true)   // required for the wasmJs web-worker driver
    }
}

kotlin {
    jvm()
    wasmJs { browser(); binaries.executable() }
    if (hasAndroidSdk) {
        // AGP 9 KMP library plugin — block renamed to android{}; androidLibrary{} is deprecated
        android {
            namespace = "org.reduxkotlin.sample.taskflow"
            compileSdk = 36          // Compose 1.11 androidx artifacts require API 36
            minSdk = 24
            androidResources { enable = true }   // else composeResources fallbacks don't package on Android
        }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {     // no iosX64 (removed in Compose 1.11.0)
        it.binaries.framework { baseName = "TaskFlowApp"; isStatic = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":redux-kotlin-bundle-compose"))
            implementation(compose.runtime); implementation(compose.foundation)
            implementation(compose.ui); implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.markdown.renderer.coil3)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)   // pending_op payload codec
        }
        commonTest.dependencies {
            implementation(kotlin("test")); implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver); implementation(libs.ktor.client.android)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.sqldelight.sqlite.driver); implementation(libs.ktor.client.java)
        }
        // Kotlin's default hierarchy template already provides the iosMain intermediate
        // (iosArm64 + iosSimulatorArm64) — use `by getting`, NOT `by creating` (creating one
        // manually disables/collides with the template).
        val iosMain by getting {
            dependencies { implementation(libs.sqldelight.native.driver); implementation(libs.ktor.client.darwin) }
        }
        wasmJsMain.dependencies {
            implementation(libs.sqldelight.web.worker.driver)
            implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.0.2"))
            implementation(npm("sql.js", "1.8.0"))
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))   // copies sql-wasm.wasm + worker into dist
            // wasmJs needs NO explicit Ktor engine (Ktor service-loads its built-in JS engine)
        }
        val jvmTest by getting {
            dependencies {
                @OptIn(ExperimentalComposeLibrary::class) implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop { application { mainClass = "org.reduxkotlin.sample.taskflow.MainKt" } }
```

- [ ] **Step 2: Create `composeApp/webpack.config.d/sqljs.config.js`** so sql.js can fetch its `.wasm` in the browser (without this the wasmJs DB fails to init at runtime):

```javascript
// composeApp/webpack.config.d/sqljs.config.js
const CopyWebpackPlugin = require("copy-webpack-plugin");
config.plugins.push(new CopyWebpackPlugin({
  patterns: [{ from: "../../node_modules/sql.js/dist/sql-wasm.wasm", to: "." }]
}));
config.resolve = config.resolve || {}; config.resolve.fallback = { fs: false, path: false, crypto: false };
```

- [ ] **Step 3: Verify the stack resolves on every host-runnable target** (R1/R4 spike — before features). Each must SUCCEED:
  - `./gradlew :examples:taskflow:composeApp:compileKotlinJvm`
  - `./gradlew :examples:taskflow:composeApp:compileKotlinWasmJs`  ← proves material3/coil/markdown/sqldelight/immutable/serialization all have wasmJs variants
  - `./gradlew :examples:taskflow:composeApp:dependencies --configuration commonMainApi 2>&1 | grep -Ei "material3|coil|ktor|sqldelight|immutable|markdown|serialization"`
  - Re-run `compileKotlinJvm` with `--configuration-cache` to confirm the SQLDelight 2.3.2 + Compose plugins are config-cache-safe; if it breaks, document `org.gradle.configuration-cache=false` for the sample.
  If any wasmJs compile fails for a missing variant, STOP and report (do not silently drop the dep).
- [ ] **Step 4: Commit.** `git add examples/taskflow/composeApp/build.gradle.kts examples/taskflow/composeApp/webpack.config.d && git commit -m "build(taskflow): composeApp targets + deps + sqldelight(wasm webpack) + desktop app"`

### Task 3: Entry points + minimal App + iOS/Android hosts; link all targets

**Files:** `App.kt` (stub), `jvmMain/kotlin/main.kt`, `wasmJsMain/kotlin/main.kt`, `wasmJsMain/resources/index.html`, `iosMain/kotlin/MainViewController.kt`, `androidApp/**`, `iosApp/**`.

- [ ] **Step 1: `App.kt` stub** (replaced in Task 30):

```kotlin
package org.reduxkotlin.sample.taskflow
import androidx.compose.material3.* ; import androidx.compose.runtime.Composable
@Composable fun App() { MaterialTheme { Surface { Text("TaskFlow") } } }
```

- [ ] **Step 2: jvm `main.kt`** — `fun main() = application { Window(::exitApplication, title="TaskFlow"){ App() } }` (package `org.reduxkotlin.sample.taskflow`, object `MainKt`).
- [ ] **Step 3: wasmJs `main.kt`** — `@OptIn(ExperimentalComposeUiApi::class) fun main(){ ComposeViewport(kotlinx.browser.document.body!!){ App() } }`.
- [ ] **Step 4: `index.html`** — **empty body** (ComposeViewport creates its own canvas), viewport meta:

```html
<!doctype html><html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>TaskFlow</title>
<style>html,body{margin:0;height:100%}</style></head>
<body><script src="composeApp.js"></script></body></html>
```

- [ ] **Step 5: iOS `MainViewController.kt`** — `fun MainViewController() = ComposeUIViewController { App() }`.
- [ ] **Step 6: `androidApp` module** — `com.android.application`, `compose.compiler` + `buildFeatures.compose=true`, `compileOptions` Java 21, `packaging.resources.excludes` (mirror `examples/counter/android`). `MainActivity` lives here (NOT in composeApp):

```kotlin
// androidApp/build.gradle.kts
plugins { alias(libs.plugins.android.application); kotlin("android"); alias(libs.plugins.compose.compiler) }
android {
    namespace = "org.reduxkotlin.sample.taskflow.app"; compileSdk = 36   // Compose 1.11 requires API 36
    defaultConfig { applicationId = "org.reduxkotlin.sample.taskflow"; minSdk = 24; targetSdk = 36; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(project(":examples:taskflow:composeApp"))
    implementation(libs.androidx.activity.compose); implementation(compose.runtime)
}
```

```kotlin
// androidApp/src/main/kotlin/.../MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) { super.onCreate(s); enableEdgeToEdge(); setContent { App() } }
}
```

```xml
<!-- androidApp/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-permission android:name="android.permission.INTERNET"/>
  <application android:label="TaskFlow" android:theme="@style/Theme.TaskFlow">
    <activity android:name=".MainActivity" android:exported="true"
              android:enableOnBackInvokedCallback="true">
      <intent-filter><action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
  </application>
</manifest>
```
A Compose-only app needs no Material XML theme — ship a **zero-dep framework theme** in `androidApp/src/main/res/values/themes.xml`: `<style name="Theme.TaskFlow" parent="android:Theme.Material.Light.NoActionBar"/>` (avoids pulling appcompat/material just for a theme; `@style/Theme.Material3.DayNight.NoActionBar` is unresolvable without those deps). Compose draws its own M3 Expressive surfaces.

- [ ] **Step 7: iOS host (`iosApp/`) — Swift sources + README only (no autogen `.xcodeproj`).** Commit the host Swift + an `iosApp/README.md`; the full Xcode project is a **documented manual follow-up** (do not author a `project.pbxproj` autonomously). Host:

```swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
struct ContentView: View { var body: some View { ComposeView().ignoresSafeArea() } }
```
README documents: wire the framework via **`embedAndSignAppleFrameworkForXcode`** (a Run Script phase) — which **auto-syncs the compose-resources bundle** (do NOT add a hand-rolled copy script); `Info.plist` keys (bundle id, `UILaunchScreen`, orientations, `CADisableMinimumFrameDurationOnPhone=true`); User Script Sandboxing OFF. Gradle gates only the framework link (Step 8) + a `swiftc` smoke compile — the Xcode app build is manual/Mac-only.

- [ ] **Step 8: Link/build all targets:** `./gradlew :examples:taskflow:composeApp:jvmJar :examples:taskflow:composeApp:wasmJsBrowserDistribution :examples:taskflow:composeApp:linkDebugFrameworkIosSimulatorArm64` (last is environmental — trust CI if no Mac SDK). After `wasmJsBrowserDistribution`, inspect `build/dist/wasmJs/productionExecutable/` and confirm the emitted bootstrap JS matches `composeApp.js` in `index.html`; pin `wasmJs { browser { webpackTask { mainOutputFileName.set("composeApp.js") } } }` if it differs.
- [ ] **Step 9: Commit.** `git add examples/taskflow && git commit -m "feat(taskflow): entry points + android/ios hosts; all targets link"`

---

## Phase 1 — Platform shims (expect/actual) — build these before features depend on them

### Task 4: DriverFactory, Ktor engine, dynamic color, uuid — expect + actuals

**Files:** `platform/{DriverFactory,HttpEngine,DynamicColor,Ids}.kt` (commonMain expect) + one `actual` per source set.

- [ ] **Step 1: commonMain expects.**

```kotlin
// platform/DriverFactory.kt
expect class DriverFactory { suspend fun createDriver(): app.cash.sqldelight.db.SqlDriver }
// platform/HttpEngine.kt — null on wasmJs (Coil uses browser fetch)
expect fun ktorEngineOrNull(): io.ktor.client.engine.HttpClientEngineFactory<*>?
// platform/DynamicColor.kt — Android-only Material You; null elsewhere
expect fun dynamicColorScheme(dark: Boolean): androidx.compose.material3.ColorScheme?
// platform/Ids.kt — backing for the IdGenerator (OpId/CardId minting outside reducers)
expect fun newUuid(): String
```

- [ ] **Step 2: actuals** (per §13 + research):
  - **android:** `AndroidSqliteDriver(TaskFlowDb.Schema.synchronous(), context, "taskflow.db")` (context via an `androidx.startup`/Application-provided holder); `ktorEngineOrNull() = io.ktor.client.engine.android.Android`; `dynamicColorScheme` = `dynamicDark/LightColorScheme(context)` when `Build.VERSION.SDK_INT>=31` else null; `newUuid() = java.util.UUID.randomUUID().toString()`.
  - **jvm:** `JdbcSqliteDriver("jdbc:sqlite:${appDataDir()}/taskflow.db").also { TaskFlowDb.Schema.synchronous().create(it) /* guarded */ }`; engine = `io.ktor.client.engine.java.Java`; dynamicColor = null; uuid = `UUID.randomUUID()`.
  - **iosMain:** `NativeSqliteDriver(TaskFlowDb.Schema.synchronous(), "taskflow.db")`; engine = `io.ktor.client.engine.darwin.Darwin`; dynamicColor = null; uuid = `platform.Foundation.NSUUID().UUIDString`.
  - **wasmJs:** driver = `WebWorkerDriver(Worker(...))` + `TaskFlowDb.Schema.create(driver).await()` (async; see §13 worker URL); `ktorEngineOrNull() = null`; dynamicColor = null; uuid = `kotlin.js`-side `crypto.randomUUID()` shim (or a counter+timestamp fallback).
  Android needs an app `Context`; expose it via a tiny `actual` init the Android `MainActivity`/Application sets (e.g. `AndroidContextHolder`).
- [ ] **Step 3: Verify compile all targets** (`compileKotlinJvm`, `compileKotlinWasmJs`, ios link). **Commit.** `git commit -m "feat(taskflow): expect/actual platform shims (db driver, ktor engine, dynamic color, uuid)"`

---

## Phase 2 — State & types (immutable)

> No `explicitApi()` under `convention.control` → no KDoc burden; detekt formatting still applies.

### Task 5: Ids + root models

**Files:** `model/Ids.kt`, `model/RootModels.kt`; Test `commonTest/.../model/IdsTest.kt`

- [ ] **Step 1: Failing test** (value-class identity, as v1 Task 4) + `OpId`. **Step 2: run FAIL.**
- [ ] **Step 3: `Ids.kt`** — value classes `AccountId, BoardId, ColumnId, CardId, LabelId, OpId` (all `@JvmInline value class X(val v: String)`).
- [ ] **Step 4: `RootModels.kt`** — `AccountsModel` (`PersistentMap<AccountId, AccountSummary>` + `activeAccountId`), `AccountSummary`, `AppSettingsModel`, `Theme`, `FakeServiceConfig`, `AuthFlowModel`, `AuthMode` — **exactly as design §5** (immutable collections, `persistentMapOf()` defaults).
- [ ] **Step 5: run PASS. Commit.** `git commit -m "feat(taskflow): typed ids + immutable root models"`

### Task 6: Per-account + board models

**Files:** `model/AccountModels.kt`, `model/BoardModels.kt`, `model/BoardSlice.kt`

- [ ] **Step 1:** Author `SessionModel(accountId: AccountId, bio: String? = null)` (NO name/email/avatar — those live once in `CollaboratorsModel`), `AccountDetail` (a seed/DTO type only, NOT a store model), `NavModel` (`route`, `openCardId`, `composing: ColumnId?`), `Route`, `BoardListModel`, `BoardSummary`, `CollaboratorsModel(byId: PersistentMap<AccountId, AccountSummary>)`, `BoardModel(board: Board? = null)` sentinel wrapper, `Board`, `Column`, `Card`, `Attachment`, `Label`, `FilterModel`, `UndoModel` (cap 15), `SyncModel(inFlight: PersistentSet<CardId>, pendingCount, online, lastSyncedAt, lastError)`, `ActivityModel`, `ActivityEntry` — **exactly as design §5** (all collections `Persistent*`).
- [ ] **Step 2: Verify compile** (`compileKotlinJvm`). **Commit.** `git commit -m "feat(taskflow): immutable per-account + board models (fixed-slot, sentinel)"`

---

## Phase 3 — Action catalog

### Task 7: Actions

**Files:** `action/Actions.kt`; Test `commonTest/.../action/ActionsTest.kt`

- [ ] **Step 1: Failing test** — `Undoable` set is exactly user card-mutations; bot actions + async results + nav are NOT `Undoable`:

```kotlin
assertTrue(AddCard(ColumnId("a"), CardId("c"), "t", "d", OpId("o"), now) is Undoable)
assertTrue(CardMoveRequested(CardId("c"), ColumnId("a"), ColumnId("b"), 0, OpId("o")) is Undoable)
assertFalse(BotMovedCard(CardId("c"), ColumnId("b"), 0) is Undoable)
assertFalse(CardOpFailed(OpId("o"), "e", InverseOp.MoveBack(CardId("c"), ColumnId("a"), 0)) is Undoable)
assertFalse(Navigate(Route.Profile) is Undoable)
```

- [ ] **Step 2: run FAIL. Step 3: `Actions.kt`.** Full catalog. Key differences from v1: every card-mutation `*Requested` carries an `OpId` and (for add/edit) a pre-minted `CardId`/`Instant`; results are `CardOpSucceeded(opId)` / `CardOpFailed(opId, error, inverse: InverseOp)`; bot actions are distinct (`BotMovedCard`, `BotAddedCard`) and **not** `Undoable`; add `AddColumn(title, ColumnId)`, `BoardClosed`, `BoardRestored(board: Board)`, `StartCreateCard(ColumnId)`/`CancelCreateCard`, and a sealed `InverseOp { MoveBack; DeleteAdded; RestoreEdited(prev: Card); ReAddDeleted(card: Card, column: ColumnId, index: Int) }`.

```kotlin
sealed interface Undoable                          // user card mutations only
sealed interface Action
// card mutations (optimistic + undoable + persisted) — carry OpId, pre-minted ids/clock
data class CardMoveRequested(val cardId: CardId, val from: ColumnId, val to: ColumnId, val toIndex: Int, val opId: OpId) : Action, Undoable
data class AddCard(val columnId: ColumnId, val cardId: CardId, val title: String, val description: String, val opId: OpId, val now: Instant) : Action, Undoable
data class EditCard(val cardId: CardId, val title: String, val description: String, val opId: OpId, val now: Instant) : Action, Undoable
data class DeleteCard(val cardId: CardId, val opId: OpId) : Action, Undoable
// carry cardId so syncReducer can clear SyncModel.inFlight (a Set<CardId>)
data class CardOpSucceeded(val opId: OpId, val cardId: CardId) : Action
data class CardOpFailed(val opId: OpId, val cardId: CardId, val error: String, val inverse: InverseOp) : Action
data class RetryOp(val opId: OpId) : Action     // re-pushes the still-queued op (SyncEngine resolves it)
sealed interface InverseOp {
    data class MoveBack(val cardId: CardId, val to: ColumnId, val index: Int) : InverseOp
    data class DeleteAdded(val cardId: CardId) : InverseOp
    data class RestoreEdited(val prev: Card) : InverseOp
    data class ReAddDeleted(val card: Card, val column: ColumnId, val index: Int) : InverseOp
}
// bot (server-truth; NOT undoable, no revert)
data class BotMovedCard(val cardId: CardId, val to: ColumnId, val toIndex: Int) : Action
data class BotAddedCard(val columnId: ColumnId, val card: Card) : Action
// board lifecycle
data object BoardClosed : Action
data class BoardRestored(val board: Board) : Action
data class StartCreateCard(val columnId: ColumnId) : Action
data object CancelCreateCard : Action
data class AddColumn(val id: ColumnId, val title: String) : Action
data class CreateBoard(val boardId: BoardId, val name: String, val now: Instant) : Action
// nav
data class Navigate(val route: Route) : Action
data class OpenCard(val cardId: CardId) : Action
data object CloseCard : Action
// undo / filter
data object Undo : Action
data object Redo : Action
data class SetFilterQuery(val query: String) : Action
data class SetFilterAssignee(val accountId: AccountId?) : Action
data class ToggleFilterLabel(val labelId: LabelId) : Action
// board/account loads (per-account)
data class LoadBoardRequested(val boardId: BoardId) : Action
data class LoadBoardSucceeded(val board: Board) : Action
data class LoadBoardFailed(val boardId: BoardId, val error: String) : Action
data object LoadBoardListRequested : Action
data class LoadBoardListSucceeded(val summaries: PersistentList<BoardSummary>) : Action
data class LoadBoardListFailed(val error: String) : Action
// sync (per-account) — folded into SyncModel by syncReducer
data class SyncStatusChanged(val online: Boolean, val pendingCount: Int, val inFlight: PersistentSet<CardId>, val lastSyncedAt: Instant?, val lastError: String?) : Action
data object Refresh : Action
// profile / activity (per-account)
data class EditProfile(val displayName: String, val email: String, val avatarUrl: String, val bio: String?) : Action
data class RecordActivity(val entry: ActivityEntry) : Action
// auth / accounts / settings (root)
data class StartLogin(val mode: AuthMode) : Action
data object LoginRequested : Action
data class AccountLoggedIn(val summary: AccountSummary) : Action   // (Succeeded-equivalent; documented deviation)
data class LoginFailed(val error: String) : Action
data object LoadAccountsRequested : Action
data class LoadAccountsSucceeded(val accounts: PersistentList<AccountSummary>, val activeAccountId: AccountId?) : Action
data class LoadAccountsFailed(val error: String) : Action
data class SwitchAccount(val accountId: AccountId) : Action
data class LogoutAccount(val accountId: AccountId) : Action
data class SetTheme(val theme: Theme) : Action
data class SetLatency(val minMs: Int, val maxMs: Int) : Action
data class SetFailureRate(val rate: Float) : Action
data class SetBotEnabled(val enabled: Boolean) : Action
data class SetOnline(val online: Boolean) : Action               // writes AppSettingsModel.fakeService.online (root)
```

- [ ] **Step 4: run PASS. Commit.** `git commit -m "feat(taskflow): action catalog (OpId, InverseOp, bot+lifecycle actions)"`

---

## Phase 4 — Reducers (pure helpers; later mounted as `on<Action>`)

> Author each model's transitions as pure `fun fooReducer(state, action): State` helpers for unit testing. Task 18 mounts them inside `createConcurrentModelStore { model(Init()) { on<X> { s,a -> fooReducer(s,a) } } }`. Because `on<A>` routes by exact leaf class, **register one `on<>` per concrete action** the model handles (no `is Parent` grouping).

### Task 8: Root reducers — as v1 Task 7 (accounts/settings/auth), `Persistent*` collections. TDD; commit.

### Task 9: Account reducers — nav (incl `composing` via `StartCreateCard`/`CancelCreateCard`/`Navigate` clears it), session (id+bio; `EditProfile`), boardList (counts cached from `LoadBoardListSucceeded`; `CreateBoard` appends — **no `Clock.now()`/recompute in the reducer**; open-board counts are selector-derived), collaborators (`EditProfile` updates self). TDD; commit.

### Task 10: Board reducers — the core

**Files:** `reducer/BoardReducers.kt`; Test `commonTest/.../reducer/BoardReducersTest.kt`

- [ ] **Step 1: Failing tests:** (a) move updates exactly two columns and is **integrity-preserving** (remove from ALL columns then insert once; stale `from` can't orphan); (b) `boardReducer(BoardModel(null), LoadBoardSucceeded(board))` loads; `BoardClosed` resets to `BoardModel(null)`; (c) `CardOpFailed` applies its `InverseOp` (move-back / delete-added / restore-edited / re-add-deleted) — **not** a whole-board snapshot; (d) `BotMovedCard` mutates the board like a move; (e) integrity invariant `cards.keys == ∪ column.cardIds`, no dups, after each op; (f) `undoReducer`/`pushUndo` stacks (push present, restore past, cap 15 drops oldest, redo, empty no-op); (g) `BoardRestored` replaces the board; (h) `syncReducer` keys `inFlight` by **`CardId`**: add the card on every `*Requested`/`AddCard`/`EditCard`/`DeleteCard`, remove on `CardOpSucceeded`/`CardOpFailed` (both carry `cardId`); `SyncStatusChanged` folds online/pendingCount/lastSyncedAt/lastError.

- [ ] **Step 2: run FAIL. Step 3: implement** `boardReducer(state: BoardModel, action): BoardModel` operating on `state.board` (null = NotLoaded), with integrity-preserving `moveCard` (remove id from every column, insert once at clamped index), `addCard` using the action's pre-minted `CardId`+`now` (no `cards.size`), `editCard`, `deleteCard`, `applyInverse(InverseOp)`, `BotMovedCard`/`BotAddedCard` (same paths, no undo/sync), `LoadBoardSucceeded`, `BoardClosed -> BoardModel(null)`, `BoardRestored`. Plus `filterReducer`, `syncReducer` (**`CardId`**-keyed `inFlight` add/remove + `SyncStatusChanged` fold), `activityReducer`, and undo helpers `pushUndo(model, snapshot)` + `undoReducer(model, action, present): UndoResult(model, restored: Board?)`.
- [ ] **Step 4: run PASS. Commit.** `git commit -m "feat(taskflow): board reducers (integrity moves, inverse-op revert, undo, bot, sentinel)"`

---

## Phase 5 — Persistence (local) & Sync (fake network) — two distinct layers

### Task 11: Schema + adapters (LocalStore tables + pending-op queue)

**Files:** `commonMain/sqldelight/org/reduxkotlin/sample/taskflow/db/TaskFlowDb.sq`, `db/Adapters.kt`

- [ ] **Step 1:** Author the `.sq` schema from design §13a (`account`, `account_nav`, `app_settings`, `board`, `board_column`, `card`, `attachment`, `label`, `card_label`, `activity`, `collaborator`, **`pending_op`** [opId, kind, payload, createdAt, attempts], **`sync_meta`** [lastSyncedAt cursor]) + the queries LocalStore needs (selectBoardList, selectBoard graph, move/insert/update/delete card, settings get/set, nav get/set, enqueue/pendingOps/markSynced/clearOp, applyRemote upserts, ensureSeeded count). `Instant`↔INTEGER, value-class↔TEXT adapters in `Adapters.kt`.
- [ ] **Step 2: Verify codegen + compile:** `./gradlew :examples:taskflow:composeApp:generateCommonMainTaskFlowDbInterface :examples:taskflow:composeApp:compileKotlinJvm`. **Commit.**

### Task 12: LocalStore (durable, no latency)

**Files:** `data/local/LocalStore.kt`, `data/local/SqlDelightLocalStore.kt`, `data/SeedData.kt`; Test `commonTest/.../data/LocalStoreTest.kt` (in-memory driver)

- [ ] **Step 1: Failing tests:** in-memory `JdbcSqliteDriver(IN_MEMORY)`-backed `SqlDelightLocalStore`: seed→loadBoardList→loadBoard round-trips, normalized reassembly into immutable `Board`, move/add/edit/delete persist, `ensureSeeded` idempotent (3 accounts, normalized board, ≥1 markdown/image/link/labels per §6), `enqueue`→`pendingOps`→`markSynced` queue lifecycle, `applyRemote(changes)` merges (last-write-wins). **No latency anywhere.** **Step 2: run FAIL.**
- [ ] **Step 3: implement.** `interface LocalStore` (suspend, immutable returns) — reads (`loadAccounts/loadSettings/loadBoardList/loadBoard/loadNav`), writes (`saveSettings/saveNav/moveCard/addCard/editCard/deleteCard/recordActivity`), queue (`enqueue(SyncOp)/pendingOps(): List<SyncOp>/markSynced(OpId)`), `applyRemote(List<RemoteChange>)`, `ensureSeeded()`. `SqlDelightLocalStore(db)` maps rows↔models in `db.transaction {}`. `SeedData` holds the §6 seed (fixed `Instant`). **Step 4: run PASS. Commit.** `git commit -m "feat(taskflow): SQLDelight LocalStore + pending-op queue + seed"`

### Task 13: RemoteApi + FakeRemoteApi (fake network: latency, failure, offline)

**Files:** `data/remote/{RemoteApi,FakeRemoteApi,SyncOp,PushResult}.kt`; Test `commonTest/.../data/FakeRemoteApiTest.kt`

- [ ] **Step 1: Failing tests (virtual time):** `FakeRemoteApi` reading `{ online=false }` → `push`/`pull` throw `OfflineException`; `{ failureRate=1f }` (online) → transient failure; `{ failureRate=0f, online=true }` → `push` returns `Accepted`, except a deterministic conflict op (move into a full-WIP column) returns `Rejected`; latency honored under `runTest`. **Step 2: run FAIL.**
- [ ] **Step 3: implement.** `interface RemoteApi { suspend fun push(ops: List<SyncOp>): PushResult; suspend fun pull(since: Instant?): RemotePage }` (the **real-backend seam** — no DB/Compose types). `sealed PushResult { Accepted; data class Rejected(opId, reason); }` (+ exceptions = transient/offline → treated as Deferred by the engine). `FakeRemoteApi(serverState, config: () -> FakeServiceConfig, rng)` holds an in-memory server snapshot (seeded identically), applies latency + failure + the `online` gate, validates pushes, and emits bot "server-side" edits via `pull`. **Step 4: run PASS. Commit.** `git commit -m "feat(taskflow): FakeRemoteApi network sim (latency/failure/offline) behind RemoteApi seam"`

### Task 14: SyncEngine + SyncRepository (offline-first)

**Files:** `data/sync/{SyncEngine,SyncRepository}.kt`; Test `commonTest/.../data/SyncEngineTest.kt`

- [ ] **Step 1: Failing tests (virtual time, `backgroundScope`):** a queued op + online → `push` Accepted → `markSynced`, `pendingCount` drops to 0, `SyncStatus.lastSyncedAt` set; offline → op stays queued, `pendingCount` grows, no revert; toggle online → queue drains; `Rejected` push → engine emits a `CardOpFailed(opId, reason, inverse)` (revert path); transient failure → retained + retried (attempts++). `pull` merges remote changes into LocalStore. **Step 2: run FAIL.**
- [ ] **Step 3: implement.** `SyncRepository(local: LocalStore, remote: RemoteApi, scope)` — local-first: mutations write `local` + `local.enqueue(op)` immediately (durable, instant), then `engine.kick()`. `SyncEngine` drains `local.pendingOps()` → `remote.push`: `Accepted`→`markSynced`; `Rejected`→callback to dispatch `CardOpFailed(opId, reason, inverse)`; `Deferred`/`OfflineException`→retain + backoff. After a push, `remote.pull(sync_meta.lastSyncedAt)`→`local.applyRemote`→update cursor. Exposes a `SyncStatus` flow (`online/pendingCount/inFlight/lastSyncedAt/lastError`) the effects layer maps into `SyncModel`. Triggers: post-mutation, on `online`→true, manual `Refresh`, periodic tick. **Step 4: run PASS. Commit.** `git commit -m "feat(taskflow): offline-first SyncEngine + SyncRepository"`

---

## Phase 6 — Middleware

### Task 15: EffectsMiddleware (local-write + enqueue + sync; per-op inverse on Rejected)

**Files:** `middleware/EffectsMiddleware.kt`; Test `commonTest/.../middleware/EffectsMiddlewareTest.kt`

- [ ] **Step 1: Failing test (real wiring, test scheduler).** Inject `backgroundScope`; a `SyncRepository` over an in-memory `LocalStore` + a `FakeRemoteApi` set to reject:

```kotlin
@Test fun rejected_push_dispatches_CardOpFailed_with_inverse() = runTest {
    val dispatched = mutableListOf<Any>()
    val syncRepo = SyncRepository(inMemoryLocalStore(), rejectingRemote(), scope = backgroundScope)
    val mw = effectsMiddleware(syncRepo, scope = backgroundScope)
    val store = recordingStore { dispatched += it }
    val chain = mw(store) { a -> dispatched += a; a }
    chain(CardMoveRequested(CardId("c1"), ColumnId("a"), ColumnId("b"), 0, OpId("op1")))
    advanceUntilIdle()
    val fail = dispatched.filterIsInstance<CardOpFailed>().single()
    assertEquals(OpId("op1"), fail.opId); assertTrue(fail.inverse is InverseOp.MoveBack)
}
```

- [ ] **Step 2: run FAIL. Step 3: implement** with `middleware<ModelState> { store, next, action -> }`. On each card mutation: compute the `InverseOp` from current state, `next(action)` (optimistic store update), then `scope.launch { syncRepo.<mutate>(...) }` — `SyncRepository` writes LocalStore + enqueues (instant), and the `SyncEngine`'s `Rejected` callback dispatches `CardOpFailed(opId, reason, inverse)`. Map `SyncStatus` → dispatch `SyncStatusChanged(...)`. On `Load*Requested` → `local.load*` (instant) + dispatch succeeded; on `Refresh`/online-toggle → `engine.kick()`. **Guard:** drop late dispatches if the board was left (sentinel / boardId mismatch). **Step 4: run PASS. Commit.** `git commit -m "feat(taskflow): effects middleware over SyncRepository (local-first, inverse on reject)"`

> Note: `SyncStatusChanged`/`Refresh`/`SetOnline` are already in the Task 7 catalog and the Task 10 `syncReducer`. `SetOnline` writes `AppSettingsModel.fakeService.online` (root) via the Settings screen; the `SyncEngine` emits `SyncStatusChanged` into the account store.

### Task 16: UndoMiddleware + ActivityLogger

**Files:** `middleware/UndoMiddleware.kt`, `middleware/ActivityLoggerMiddleware.kt`; Tests.

- [ ] **Step 1: Failing tests:** undo middleware pushes a board snapshot **before** an `Undoable` action and **skips** bot/non-undoable actions; `Undo` dispatches `BoardRestored(restored)`; `BoardClosed` clears stacks; bot-during-undo doesn't corrupt stacks; activity logger maps move/add/edit/delete/bot → `RecordActivity` with a human summary. **Step 2: run FAIL. Step 3: implement** (snapshot via `pushUndo`, apply via `BoardRestored`, `Undoable`-only). **Step 4: PASS. Commit.**

### Task 17: BotCollaborator

**Files:** `middleware/BotCollaborator.kt`; Test `commonTest/.../middleware/BotCollaboratorTest.kt`

- [ ] **Step 1: Failing tests (virtual time, `backgroundScope`):** with `botEnabled=true`, after `advanceTimeBy(interval); runCurrent()` the store received exactly one `BotMovedCard`/`BotAddedCard`; with `botEnabled=false`, advancing several intervals yields **zero** bot dispatches; cancelling the returned `Job` stops it. **Step 2: run FAIL. Step 3:** `fun startBot(scope, store, settings: () -> FakeServiceConfig, rngSeed): Job` looping `delay(interval)` then dispatching a deterministic bot action against the loaded board; launched in a cancellable scope. **Step 4: PASS. Commit.**

---

## Phase 7 — Store factories & registry (routing DSL; fixed-slot; no inject/eject)

### Task 18: Root AppStore

**Files:** `store/AppStore.kt`, `store/StoreExt.kt`; Test `commonTest/.../store/AppStoreTest.kt`

- [ ] **Step 1: Failing test** — `createAppStore()` reduces `AccountLoggedIn` into `AccountsModel`; `SetTheme` into `AppSettingsModel` (dispatched **through** the assembled store). **Step 2: run FAIL.**
- [ ] **Step 3: implement** with the routing DSL:

```kotlin
fun createAppStore(notificationContext: CoroutineContext = mainNotificationContext()): Store<ModelState> =
    createConcurrentModelStore(
        notificationContext = notificationContext,   // main-thread fan-out (Rule E); default Inline is wrong
    ) {
        model(AccountsModel())   { on<AccountLoggedIn> { s, a -> accountsReducer(s, a) }; on<SwitchAccount> { s,a -> accountsReducer(s,a) }; on<LogoutAccount> { s,a -> accountsReducer(s,a) }; on<LoadAccountsSucceeded> { s,a -> accountsReducer(s,a) }; on<EditProfile> { s,a -> accountsReducer(s,a) } }
        model(AppSettingsModel()){ on<SetTheme> { s,a -> appSettingsReducer(s,a) }; on<SetLatency>{s,a->appSettingsReducer(s,a)}; on<SetFailureRate>{s,a->appSettingsReducer(s,a)}; on<SetBotEnabled>{s,a->appSettingsReducer(s,a)}; on<SetOnline>{s,a->appSettingsReducer(s,a)} }
        model(AuthFlowModel())   { on<StartLogin>{s,a->authFlowReducer(s,a)}; on<LoginRequested>{s,_->authFlowReducer(s,LoginRequested)}; on<AccountLoggedIn>{s,a->authFlowReducer(s,a)}; on<LoginFailed>{s,a->authFlowReducer(s,a)} }
    }
```
> `createConcurrentModelStore` forwards `notificationContext` (per the bundle README). `mainNotificationContext()` is an `expect/actual` returning a main-dispatcher `CoroutineContext` (Dispatchers.Main on android/ios/jvm; the wasm main context). Tests pass an immediate/`UnconfinedTestDispatcher` context.
`StoreExt.kt`: **`ModelState.models` is `@PublishedApi internal` — do NOT use it.** Only `inline fun <reified M : Any> ModelState.getModel(): M = get(M::class)` (public `get`; fixed slots mean it never throws). No nullable `peek`. **Step 4: PASS. Commit.**

### Task 19: AccountStore + AccountRegistry

**Files:** `store/AccountStore.kt`, `store/AccountRegistry.kt`; Test `commonTest/.../store/AccountRegistryTest.kt`

- [ ] **Step 1: Failing test (isolation + remove):** two accounts; `Navigate(Settings)` on A doesn't change B's `NavModel`; `registry.remove(A)` leaves B; the removed account's bot/effects scope is cancelled. **Step 2: run FAIL.**
- [ ] **Step 3: implement.** `createAccountStore(detail: AccountDetail, rootStore, localStore, remoteApi): AccountStoreHandle` — builds a per-account `CoroutineScope` (whose store-dispatch path is on `Dispatchers.Main`; DB/net on IO/Default + `withContext(Main)` before dispatch — Rule E); a `SyncEngine` + `SyncRepository(localStore, FakeRemoteApi(server, { rootStore.getModel<AppSettingsModel>().fakeService }, rng), scope)` (online/latency/failure all read from root `AppSettingsModel`); and
  `createConcurrentModelStore(notificationContext = mainNotificationContext(), enhancer = applyMiddleware(activityLogger, undoMiddleware{boardOf(store)}, effectsMiddleware(syncRepo, scope))) {`
  `model(SessionModel(detail.accountId, detail.bio)){ on<EditProfile>{…} };`  ← id + bio only; identity lives in Collaborators
  `model(NavModel()){ on<Navigate>{…}; on<OpenCard>{…}; on<CloseCard>{…}; on<StartCreateCard>{…}; on<CancelCreateCard>{…} };`
  `model(BoardListModel()){ on<LoadBoardListSucceeded>{…}; on<CreateBoard>{…} };`
  `model(CollaboratorsModel(seedCollaborators(detail))){ on<EditProfile>{…} };`  ← seeded with self + bot + assignees; display source
  `model(BoardModel()){ on<LoadBoardSucceeded>; on<CardMoveRequested>; on<AddCard>; on<EditCard>; on<DeleteCard>; on<CardOpFailed>; on<BotMovedCard>; on<BotAddedCard>; on<BoardClosed>; on<BoardRestored> };`
  `model(FilterModel()){…}; model(UndoModel()){…}; model(SyncModel()){ on<CardMoveRequested>; on<AddCard>; on<EditCard>; on<DeleteCard>; on<CardOpSucceeded>; on<CardOpFailed>; on<SyncStatusChanged> }; model(ActivityModel()){ on<RecordActivity>{…} } }`.
  **`AccountRegistry`** wraps `StoreRegistry<AccountId, ModelState>` **plus a side `MutableMap<AccountId, AccountStoreHandle>`** (the registry stores only `Store<ModelState>`; scope/engine/bot live in the handle): `getOrCreate(id, detail)` → `registry.getOrCreate(id) { handle.store }` + record the handle; `get(id)` → `registry.get(id)`; `remove(id)` → `handles[id]?.scope?.cancel(); handles.remove(id); registry.remove(id)`; `startBot(id)`/`stopBot(id)` toggle the handle's bot `Job`. **Step 4: PASS. Commit.** `git commit -m "feat(taskflow): account store (fixed-slot routing DSL, main-thread notify) + AccountRegistry side-map isolation"`

---

## Phase 8 — Theme (M3 Expressive, tokens)

### Task 20: Color/Type/Shape/Motion/Dimens/Theme — as v1 Task 17, with:
- `TaskFlowTheme(theme, dynamic: Boolean = true)` prefers `dynamicColorScheme(dark)` (the Android `actual`; null elsewhere) when `dynamic`, else the token `Light/DarkColors`.
- Wrap in `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(), shapes, typography)` (`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`); fall back to `MaterialTheme` + `Motion.kt` springs only if a symbol is unresolved (record in README).
- Fill **all 29 color roles** from `spec-data.js` (incl. `surfaceContainer*`, `inverseSurface`, `scrim`).
- [ ] Verify compile + desktop render. Commit.

---

## Phase 9 — Components (the inventory)

> Build against `spec-data.js` component entries + screen redlines. Verify each on desktop. Where the design lists thin wrappers, you may merge (e.g. one `InfoChip` for label+attachment), but keep `KanbanCard`, `ColumnHeader`, `FilterBar`, `MoveToGroup`, `FabMenu`, `AdaptiveNav`, `MarkdownView`, `MarkdownEditor`, `Avatar`, `AccountRow`, `BoardSummaryCard`, `SettingsSlider`, `SyncToast` as distinct. All collections passed in are `Persistent*`.

### Task 21: Image loader + Avatar + LabelChip
- [ ] `ui/image/ImageLoader.kt` — `setSingletonImageLoaderFactory { ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory(httpClient(ktorEngineOrNull()))) }.build() }`, with a fallback `ImageLoader` for tests (no network). Wire **bundled `composeResources` fallback** on error/empty.
- [ ] `Avatar` (Coil **`AsyncImage`** with explicit fixed size + deterministic monogram fallback (`error`/`placeholder`), presence dot — NOT `SubcomposeAsyncImage`, which is heavier for fixed-dim list items, Rule F) + `LabelChip` (semantic colors). `contentDescription` on both (a11y). Verify compile. Commit.

### Task 22: KanbanCard + ColumnHeader(WIP) — as v1 Task 19 (omit dead "dragging" state). Commit.
### Task 23: FilterBar + MoveToGroup + FabMenu — Expressive `ButtonGroup`/`FloatingActionButtonMenu` (fallbacks documented). Commit.
### Task 24: MarkdownView + MarkdownEditor + AttachmentChip — markdown via `com.mikepenz.markdown.m3.Markdown(..., imageTransformer = Coil3ImageTransformerImpl)`; editor text in local `remember`. Commit.
### Task 25: AccountRow + BoardSummaryCard + SettingsSlider + SyncToast + SyncIndicator + AdaptiveNav + `adaptive/WindowSize.kt` (`BoxWithConstraints` breakpoints 600/840). Commit.

---

## Phase 10 — Screens (Login → Switcher → Board list → Board → Card detail → Profile → Settings)

> Bind via `rememberStableStore(store).value` then `selectorState{…}` / `fieldStateOf(ModelClass::class){…}`. Each screen renders compact + expanded per its redline. Verify on desktop before commit. **Tasks 26–32** (Login, Account switcher, Board list, Board, Card detail, Profile, Settings) with these deltas:
- Create-card uses `NavModel.composing` (FabMenu "Add card" → `StartCreateCard(col)`; CardDetail renders create mode when `composing != null`; Save → `AddCard(col, newCardId, …, newOpId, now)` then `CancelCreateCard`).
- "Add column" → `AddColumn`.
- Assignee/creator/bot avatars resolve from `CollaboratorsModel` (not the root store).
- Empty states for board-list and empty board/columns.
- Board screen: each column composable wrapped in **`key(colId)`**, binding its own slice **by ColumnId** — `fieldStateOf(BoardModel::class){ it.board?.columnById(colId)?.cardIds ?: persistentListOf() }` (NOT by index — Rule C); WIP via `selectorState{ … }` returning an `Int`/value-equal type; optimistic alpha when **`cardId ∈ SyncModel.inFlight`** (a `Set<CardId>`); `SyncToast` Retry → `dispatch(RetryOp(opId))`; a **`SyncIndicator`** in the header binds `fieldStateOf(SyncModel::class){ it }` (NOT `selectorState{ SyncModel }` — that returns the class ref) showing online/offline + `pendingCount` + `dispatch(Refresh)`. Filtered/visible lists computed in `selectorState`, never in the composable body.
- Settings screen: add an **Offline toggle** (`Switch` → `dispatch(SetOnline(false/true))`, writing `AppSettingsModel.fakeService.online`) alongside latency/failure/bot — flip offline, make edits, flip online, watch the queue drain.

Each task: build the screen → verify desktop render → commit.

---

## Phase 11 — App composition

### Task 33: Real `App()` — theme, active-store binding, nav, board lifecycle, bot, image loader, periodic sync

**Files:** Modify `App.kt`

- [ ] **Step 1:** 
  - Init the singleton Coil `ImageLoader` once (Ktor engine via `ktorEngineOrNull()`, bundled fallback).
  - `val appStore = remember { createAppStore() }`; a shared `LocalStore` (from `DriverFactory`) + a per-account `FakeRemoteApi`; `val registry = remember { AccountRegistry(appStore, localStore, ...) }`; bootstrap effect: `LaunchedEffect(Unit){ localStore.ensureSeeded(); appStore.dispatch(LoadAccounts...) }`.
  - **Sync triggers:** a periodic `LaunchedEffect` ticks `store.dispatch(Refresh)` while a board is open; flipping `AppSettingsModel.fakeService.online` true also kicks sync (the effect observes `SetOnline`).
  - `activeAccountId` null → `LoginScreen(appStore)`. Else `val handle = registry.getOrCreate(activeId, SeedData.accountDetail(activeId))`; `val store = rememberStableStore(handle.store).value`.
  - `TaskFlowTheme(theme = appSettings.theme)`; `BoxWithConstraints` → `rememberWindowSize` → screens; `AdaptiveNav` shell; route on the **active account's** `NavModel.route`; `CardDetailScreen` overlay when `openCardId != null || composing != null`.
  - **Board lifecycle:** `LaunchedEffect(activeId, route)`: entering `Route.Board(id)` → `store.dispatch(LoadBoardRequested(id))` + `registry.startBot(activeId)`; leaving → `store.dispatch(BoardClosed)` + `registry.stopBot(activeId)`.
  - **Logout:** when an account disappears from `AccountsModel`, `registry.remove(id)`.
- [ ] **Step 2: Verify full flow on desktop** (login → board list → open board (shimmer) → move card → open/edit card → undo → switch/add account isolation → settings theme+failure→watch revert → logout). `./gradlew :examples:taskflow:composeApp:run`.
- [ ] **Step 3: Verify web build** `:wasmJsBrowserDistribution` (+ `:wasmJsBrowserRun` manual smoke). **Commit.**

---

## Phase 12 — UI tests

### Task 34: Render-isolation proof (robust)
**Files:** `jvmTest/.../ui/RenderIsolationTest.kt`
- [ ] Use `org.junit.Test` + `runComposeUiTest` (mirror `redux-kotlin-compose`'s `FieldStateTest`). A `LocalRecompositionCounter` (a `mutableStateMapOf<ColumnId,Int>`) incremented inside the innermost per-column card-list composable, which is wrapped in `key(colId)` and takes only stable (`PersistentList<CardId>`, `PersistentMap<CardId,Card>`)-derived params. Set a **fake no-network `ImageLoader`**. Capture baseline **after** the first `waitForIdle()`; dispatch `CardMoveRequested` A→B; assert A,B counts increased and **C unchanged**; add a control dispatch (`SetFilterQuery` matching nothing in C) and assert C still flat. Commit.

### Task 35: Account-switch restores each account's screen — as v1 Task 32 (fake ImageLoader). Commit.
### Task 36: Offline-sync E2E (commonTest) — go offline, mutate (assert local persists + `pendingCount` grows + no revert), go online, `advanceUntilIdle()` (assert queue drains, `pendingCount==0`, `lastSyncedAt` set); a `Rejected` push reverts via inverse. Commit.

---

## Phase 13 — Assets, iOS host finishing, docs, gate

### Task 37: Bundled image fallbacks + README — bundled **PNG/JPEG** (no SVG) in `composeResources/files/{avatars,cards}/`; wire Coil error/empty → bundled; README maps each bundle API → usage, run commands per target, the M3-Expressive path taken, the persistence-vs-sync split, and the web IndexedDB-durability caveat. Commit.

### Task 38: iOS host + insets — commit the Swift host sources + `iosApp/README.md` (per Task 3 Step 7; embedAndSign auto-syncs resources, NO copy script; full `.xcodeproj` = manual follow-up). Apply `WindowInsets.safeDrawing` at the **app-shell/Scaffold root** (not per-screen, to avoid double-insets with the nav rail/bar) and `imePadding()` on the **CardDetail overlay** (where the keyboard occludes) + `MarkdownEditor` — on **iOS and Android**. Gate: `./gradlew :examples:taskflow:composeApp:linkDebugFrameworkIosSimulatorArm64` + a `swiftc` smoke compile of the host against the framework (Mac/CI; environmental). Commit.

### Task 39: Full build + detekt gate
- [ ] `./gradlew detektAll` (hook also auto-corrects; re-stage if rewritten; never `--no-verify`).
- [ ] `./gradlew :examples:taskflow:composeApp:jvmTest :examples:taskflow:composeApp:compileKotlinWasmJs :examples:taskflow:composeApp:wasmJsBrowserDistribution`.
- [ ] `./gradlew build` (existing modules' `apiCheck` green after the Compose bump + regenerated dumps).
- [ ] Commit any residue.

---

## Self-review (plan author)

> **Task numbers run sequentially in document order; execute by order, not by hunting the integer.** The persistence/sync layers (Tasks 13–14) were inserted after the v2 review, shifting later numbers.

**Blockers from the multi-agent review, each resolved here:** B1 bundle branch (Task 0); B2 ModelState immutability → fixed-slot+sentinel (Tasks 6/10/19, design §3/§5); B3 routing-DSL authoring (Tasks 8–10 helpers + 18/19 mount; no `combineModelReducers`); B4 `coil-network-ktor3` + per-target Ktor engines + wasm none (Tasks 1/2/4); B5 DiceBear PNG/picsum CORS scheme + bundled fallback (design §6, Tasks 21/37); B6 Android `androidLibrary`+`com.android.application`, `activity-compose`, Java 21, Material3 theme/INTERNET/edge-to-edge/predictive-back, MainActivity in androidApp (Tasks 2/3); B7 Compose 1.11.0 repo-wide + apiDump regen (Task 0); B8 real iOS Xcode host + resources copy (Tasks 3/38).

**Persistence ≠ network (per user):** durable **LocalStore** (SQLDelight, no latency — Tasks 11/12); replaceable fake **RemoteApi** with latency/failure/**offline** (Task 13); offline-first **SyncEngine/SyncRepository** — local-first write + persisted outbound queue + push/pull, reconnect drains queue (Task 14); effects go local-first, `Rejected` push → inverse revert (Task 15); offline-sync E2E test (Task 36); design §4/§13.

**Major logic/schema, resolved:** unique `OpId` per dispatch (Task 7); pre-minted `CardId`+`now` in actions (Tasks 7/10); per-op `InverseOp` revert not whole-board snapshot (Tasks 7/10/15); all card mutations persisted + sync-tracked (Tasks 10/14/15); `BoardRestored` instead of overloading `LoadBoardSucceeded` (Tasks 7/16); board-close/logout-in-flight guarded + scope-cancel (Tasks 15/19); integrity-preserving moves + tested invariant (Task 10); bot actions non-undoable (Tasks 7/16/17); collaborator catalog (Tasks 6/9); UndoModel cap 15 + structural-sharing note (design §4/§5); BoardSummary maintenance (Task 9); immutable collections everywhere (Tasks 5/6, design §5).

**Testing hardening:** test-scheduler scope for effects/sync/bot (Tasks 14/15/17); render-isolation baseline-after-idle + key-isolation + control dispatch + fake ImageLoader (Task 34); in-memory LocalStore tests (Task 12); offline-sync + edge cases (Tasks 14/16/36).

**Build:** coroutines-core catalog entry before use (Task 1); `binaries.executable()` + no `js{}` (Task 2); `compose.desktop{application}` (Task 2); empty-body index.html + verify js name (Task 3); `compileKotlinWasmJs` spike before features (Task 2); `kotlin("test")` used (verify; fall back to counter's per-target deps if jvmTest doesn't pick it up).

**Verification points (confirm before wiring, per task):** exact public surfaces of `redux-kotlin` (`Middleware`, `applyMiddleware`, `dispatch`), routing DSL (`createConcurrentModelStore`, `model`, `on`), `ModelState` (`get`, `models`), registry (`getOrCreateConcurrentModelStore`/`getOrCreate`/`get`/`remove`), bundle-compose bindings (`rememberStableStore.value`, `selectorState`, `fieldStateOf(modelClass, selector)`), SQLDelight async `Schema`/`WebWorkerDriver`, and the exact `MaterialExpressiveTheme`/`ButtonGroup`/`FloatingActionButtonMenu` signatures in the 1.11.0 material3.
