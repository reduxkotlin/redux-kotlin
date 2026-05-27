# RSS Reader → Redux-Kotlin Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ingest JetBrains' `kmp-production-sample` (RSS Reader) as a new example module under `examples/rss-reader/`, then replace its hand-rolled `NanoRedux` store with `org.reduxkotlin.createStore` + a small thunk middleware, with Compose UI continuing to work end-to-end on Android (and on Compose Multiplatform's `commonMain` source set).

**Architecture:**
- Phase 0 ingests the upstream sample **verbatim** (NanoRedux still in place) and verifies the project builds inside this repo's Gradle setup. This pins a known-good baseline before any redux-kotlin work.
- Phase 1–4 swap the internals: pure `FeedReducer`, a thunk middleware for `RssReader` coroutines, a side-effect middleware that feeds errors into a `SharedFlow`, and a `Store<FeedState> → StateFlow<FeedState>` bridge so Compose still does `collectAsState()`.
- Phase 5–6 rewire Compose composables + the Koin module, dispatching thunks instead of action data classes for the async ones.
- Phase 7 deletes `NanoRedux.kt`, `FeedStore.kt`, `IosReduxUtils.kt` and confirms the app still runs.

The iOS framework target, desktop app, and background `RefreshWorker` from upstream are **out of scope** for v1 (see "Deferred"). Reason: redux-kotlin's existing examples are Android + KMP common only; matching that scope keeps the diff reviewable and the Gradle plugin surface small.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.0, AGP 9.1.0, Koin 4.2.1, Ktor 3.4.3, kotlinx-coroutines 1.10.2, redux-kotlin (this repo, `:redux-kotlin` module).

---

## File Structure

After this plan completes, `examples/rss-reader/` looks like:

```
examples/rss-reader/
├── shared/
│   ├── build.gradle.kts                                                  (Phase 0 / Phase 1)
│   └── src/
│       ├── androidMain/kotlin/com/github/jetbrains/rssreader/
│       │   └── core/                                                     (copied from upstream)
│       ├── commonMain/kotlin/com/github/jetbrains/rssreader/
│       │   ├── RssReaderApp.kt                                           (Phase 5: side-effect bridge)
│       │   ├── Settings.kt                                               (copied from upstream)
│       │   ├── app/
│       │   │   ├── FeedAction.kt          NEW (Phase 2)                  sealed class FeedAction
│       │   │   ├── FeedState.kt           NEW (Phase 2)                  data class FeedState
│       │   │   ├── FeedReducer.kt         NEW (Phase 2)                  pure (State, FeedAction) → State
│       │   │   ├── FeedSideEffect.kt      NEW (Phase 3)                  sealed class FeedSideEffect
│       │   │   ├── FeedThunks.kt          NEW (Phase 3)                  refresh/add/delete async functions
│       │   │   ├── ThunkMiddleware.kt     NEW (Phase 4)                  Middleware<FeedState> + Thunk alias
│       │   │   ├── SideEffectMiddleware.kt NEW (Phase 3)                 routes FeedSideEffect to SharedFlow
│       │   │   ├── StoreFactory.kt        NEW (Phase 4)                  createFeedStore() + StateFlow adapter
│       │   │   └── (NanoRedux.kt, FeedStore.kt removed in Phase 7)
│       │   ├── core/                                                     (copied from upstream)
│       │   ├── datasource/                                               (copied from upstream)
│       │   ├── domain/                                                   (copied from upstream)
│       │   └── ui/                                                       (Phase 5: edits to call sites)
│       └── commonTest/kotlin/com/github/jetbrains/rssreader/app/
│           ├── FeedReducerTest.kt         NEW (Phase 2, written first)
│           └── ThunkMiddlewareTest.kt     NEW (Phase 4, written first)
└── androidApp/
    ├── build.gradle.kts                                                  (Phase 0)
    └── src/main/
        ├── AndroidManifest.xml                                           (copied from upstream)
        ├── kotlin/com/github/jetbrains/rssreader/
        │   ├── App.kt                                                    (Phase 6: Koin module)
        │   ├── AppActivity.kt                                           (copied from upstream)
        │   └── (sync/RefreshWorker.kt removed in Phase 0)
        └── res/                                                          (copied from upstream)
```

**Why these splits:** The upstream packs `State`/`Action`/`Effect`/`Store` into a single `FeedStore.kt`. Splitting into reducer, thunks, middleware, and store factory lets each file have one responsibility and matches the structure used in `examples/todos/common/`. Tests sit next to the things they test in `commonTest`.

---

## Deferred (out of scope for this plan)

These exist in upstream but are not part of v1; add follow-up plans if/when wanted:

- `iosApp/` — needs Xcode toolchain, `CFlow` ObjC bridge, separate review.
- `desktopApp/` — straightforward but adds a third app module + JVM Compose plugin wiring.
- `androidApp/.../sync/RefreshWorker.kt` — WorkManager background sync; depends on the Koin global `FeedStore` lookup. Re-add once we have a single canonical store and a thunk-friendly API.
- `androidApp/release` signing config (`signingConfigs.create("release")`) — deleted in Phase 0; not needed for a sample.

---

## Phase 0 — Ingest upstream verbatim and get it building

### Task 0.1: Add Gradle version-catalog entries for the new sample

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Append RSS-reader-specific versions and libraries**

Open `gradle/libs.versions.toml`. Under `[versions]`, append (group at bottom under a `# rss-reader sample` comment):

```toml
# rss-reader sample
rssreader-compose = "1.11.0"
rssreader-material3 = "1.11.0-alpha07"
rssreader-koin = "4.2.1"
rssreader-ktor = "3.4.3"
rssreader-coil = "3.4.0"
rssreader-napier = "2.7.1"
rssreader-multiplatform-settings = "1.3.0"
rssreader-kotlinx-datetime = "0.8.0"
rssreader-kotlinx-serialization = "1.11.0"
rssreader-xml-serialization = "0.91.3"
rssreader-navigation-compose = "2.9.2"
rssreader-androidx-lifecycle = "2.10.0"
rssreader-activity-compose = "1.13.0"
rssreader-material-icons-core = "1.7.3"
rssreader-core-splashscreen = "1.2.0"
rssreader-android-compile-sdk = "36"
rssreader-android-min-sdk = "26"
rssreader-android-target-sdk = "36"
```

Under `[libraries]`, also append `kotlinx-coroutines-core` (the existing catalog only has `kotlinx-coroutines-test`):

```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
```

Under `[libraries]`, append:

```toml
rssreader-compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "rssreader-compose" }
rssreader-compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "rssreader-compose" }
rssreader-compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "rssreader-material3" }
rssreader-compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "rssreader-compose" }
rssreader-compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "rssreader-compose" }
rssreader-compose-ui-tooling-preview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "rssreader-compose" }
rssreader-material-icons-core = { module = "org.jetbrains.compose.material:material-icons-core", version.ref = "rssreader-material-icons-core" }
rssreader-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "rssreader-navigation-compose" }
rssreader-androidx-lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "rssreader-androidx-lifecycle" }
rssreader-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "rssreader-activity-compose" }
rssreader-koin-core = { module = "io.insert-koin:koin-core", version.ref = "rssreader-koin" }
rssreader-koin-android = { module = "io.insert-koin:koin-android", version.ref = "rssreader-koin" }
rssreader-koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "rssreader-koin" }
rssreader-ktor-core = { module = "io.ktor:ktor-client-core", version.ref = "rssreader-ktor" }
rssreader-ktor-logging = { module = "io.ktor:ktor-client-logging", version.ref = "rssreader-ktor" }
rssreader-ktor-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "rssreader-ktor" }
rssreader-ktor-xml = { module = "io.ktor:ktor-serialization-kotlinx-xml", version.ref = "rssreader-ktor" }
rssreader-ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "rssreader-ktor" }
rssreader-coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "rssreader-coil" }
rssreader-coil-network-ktor3 = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "rssreader-coil" }
rssreader-napier = { module = "io.github.aakira:napier", version.ref = "rssreader-napier" }
rssreader-multiplatform-settings = { module = "com.russhwolf:multiplatform-settings", version.ref = "rssreader-multiplatform-settings" }
rssreader-kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "rssreader-kotlinx-datetime" }
rssreader-kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "rssreader-kotlinx-serialization" }
rssreader-xml-serialization = { module = "io.github.pdvrieze.xmlutil:serialization", version.ref = "rssreader-xml-serialization" }
rssreader-xml-serialization-core = { module = "io.github.pdvrieze.xmlutil:core", version.ref = "rssreader-xml-serialization" }
rssreader-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "rssreader-core-splashscreen" }
```

The existing catalog has **no `[plugins]` section**. Create one (append at end of file):

```toml
[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
rssreader-android-application = { id = "com.android.application", version.ref = "android-gradle-plugin" }
rssreader-android-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "android-gradle-plugin" }
rssreader-kotlinx-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
rssreader-compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "rssreader-compose" }
rssreader-compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Verify catalog parses**

```bash
./gradlew help -q
```

Expected: exits 0 with no `Catalog file 'libs.versions.toml' has unexpected element` errors.

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build(examples): add gradle catalog entries for rss-reader sample"
```

### Task 0.2: (removed)

No build-conventions changes required. The rss-reader module applies its plugins directly via the version catalog (`alias(libs.plugins.*)`) — `gradlePluginPortal()` + `google()` resolve them at module-apply time without needing build-conventions to declare them on its classpath.

### Task 0.3: Create the shared module skeleton and copy upstream sources verbatim

**Files:**
- Create: `examples/rss-reader/shared/build.gradle.kts`
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/**` (copied)
- Create: `examples/rss-reader/shared/src/androidMain/kotlin/com/github/jetbrains/rssreader/**` (copied)

Upstream lives at `/tmp/kmp-prod-sample`. If not present, clone once:
```bash
git clone --depth=1 https://github.com/Kotlin/kmp-production-sample.git /tmp/kmp-prod-sample
```

- [ ] **Step 1: Copy upstream `shared/src/commonMain` directory only**

```bash
mkdir -p examples/rss-reader/shared/src
cp -R /tmp/kmp-prod-sample/shared/src/commonMain examples/rss-reader/shared/src/commonMain
```

Upstream has no `androidMain` source directory (verified). Don't copy `iosMain` or `jvmMain`. v1 is Android-only.

- [ ] **Step 2: Confirm no `expect`/`actual` declarations**

```bash
grep -rn "^expect\|^internal expect\|^public expect" examples/rss-reader/shared/src/commonMain/
```

Expected: no matches (upstream is pure commonMain, all platform stuff is in Ktor's transitive engine deps).

- [ ] **Step 3: Create `examples/rss-reader/shared/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.rssreader.android.multiplatform.library)
    alias(libs.plugins.rssreader.kotlinx.serialization)
    alias(libs.plugins.rssreader.compose.multiplatform)
    alias(libs.plugins.rssreader.compose.compiler)
}

kotlin {
    androidLibrary {
        namespace = "com.github.jetbrains.rssreader.shared"
        compileSdk = libs.versions.rssreader.android.compile.sdk.get().toInt()
        minSdk = libs.versions.rssreader.android.min.sdk.get().toInt()

        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":redux-kotlin"))
            implementation(libs.rssreader.compose.runtime)
            implementation(libs.rssreader.compose.foundation)
            implementation(libs.rssreader.compose.material3)
            implementation(libs.rssreader.compose.ui)
            implementation(libs.rssreader.compose.components.resources)
            implementation(libs.rssreader.compose.ui.tooling.preview)
            implementation(libs.rssreader.coil.compose)
            implementation(libs.rssreader.coil.network.ktor3)
            implementation(libs.rssreader.androidx.lifecycle.runtime.compose)
            implementation(libs.rssreader.koin.compose)
            implementation(libs.rssreader.navigation.compose)
            implementation(libs.rssreader.material.icons.core)
            implementation(libs.rssreader.ktor.core)
            implementation(libs.rssreader.ktor.logging)
            implementation(libs.rssreader.ktor.content.negotiation)
            implementation(libs.rssreader.ktor.xml)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.rssreader.napier)
            implementation(libs.rssreader.kotlinx.serialization.json)
            implementation(libs.rssreader.multiplatform.settings)
            api(libs.rssreader.koin.core)
            implementation(libs.rssreader.kotlinx.datetime)
            implementation(libs.rssreader.xml.serialization)
            implementation(libs.rssreader.xml.serialization.core)
        }
        androidMain.dependencies {
            implementation(libs.rssreader.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.jetbrains.rssreader"
    generateResClass = auto
}
```

(`kotlinx-coroutines-core` was added to the catalog in Task 0.1.)

- [ ] **Step 4: Wire the module into `settings.gradle.kts`**

In `settings.gradle.kts`, inside the `include(...)` call, append:

```kotlin
    ":examples:rss-reader:shared",
```

- [ ] **Step 5: Build the new module**

```bash
./gradlew :examples:rss-reader:shared:assemble
```

Expected: BUILD SUCCESSFUL. Compose code in `commonMain/ui/` compiles, `NanoRedux.kt` and `FeedStore.kt` compile unchanged. Fix any import/resource errors that come from missing `Res` generation by re-running.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml examples/rss-reader/shared settings.gradle.kts
git commit -m "feat(examples): scaffold rss-reader shared module (upstream verbatim)"
```

### Task 0.4: Create the androidApp module skeleton and copy upstream sources verbatim

**Files:**
- Create: `examples/rss-reader/androidApp/build.gradle.kts`
- Create: `examples/rss-reader/androidApp/src/main/AndroidManifest.xml` (copied)
- Create: `examples/rss-reader/androidApp/src/main/kotlin/...` (copied, minus `sync/`)
- Create: `examples/rss-reader/androidApp/src/main/res/...` (copied)

- [ ] **Step 1: Copy upstream android sources, skipping background-sync**

```bash
mkdir -p examples/rss-reader/androidApp/src/main
cp -R /tmp/kmp-prod-sample/androidApp/src/main/kotlin examples/rss-reader/androidApp/src/main/kotlin
cp -R /tmp/kmp-prod-sample/androidApp/src/main/res examples/rss-reader/androidApp/src/main/res
cp /tmp/kmp-prod-sample/androidApp/src/main/AndroidManifest.xml examples/rss-reader/androidApp/src/main/AndroidManifest.xml
rm -rf examples/rss-reader/androidApp/src/main/kotlin/com/github/jetbrains/rssreader/sync
```

Upstream files copied (verified):
- `App.kt` (Koin module + Application)
- `AppActivity.kt` (NOT `MainActivity` — the activity class is `AppActivity`)
- `core/RssReader.kt` (the Android-side `buildRssReader(Context, Boolean)` factory)
- `sync/RefreshWorker.kt` — explicitly removed by the `rm -rf` above

- [ ] **Step 2: Strip `RefreshWorker` references from `App.kt`**

Open `examples/rss-reader/androidApp/src/main/kotlin/com/github/jetbrains/rssreader/App.kt`. Delete the import `com.github.jetbrains.rssreader.sync.RefreshWorker` and the call `launchBackgroundSync()` along with the function definition. The file should end up:

```kotlin
package com.github.jetbrains.rssreader

import android.app.Application
import android.content.Context
import com.github.jetbrains.rssreader.app.FeedStore
import com.github.jetbrains.rssreader.core.buildRssReader
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin()
    }

    private val appModule = module {
        single { buildRssReader(get<Context>(), BuildConfig.DEBUG) }
        single { FeedStore(get()) }
    }

    private fun initKoin() {
        startKoin {
            if (BuildConfig.DEBUG) androidLogger(Level.ERROR)
            androidContext(this@App)
            modules(appModule)
        }
    }
}
```

- [ ] **Step 3: Verify manifest has no WorkManager references**

```bash
grep -E "WAKE_LOCK|workmanager|WorkManager" examples/rss-reader/androidApp/src/main/AndroidManifest.xml
```

Expected: no matches. (Verified — upstream manifest only declares INTERNET permission.)

- [ ] **Step 4: Create `examples/rss-reader/androidApp/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.rssreader.android.application)
    alias(libs.plugins.rssreader.compose.multiplatform)
    alias(libs.plugins.rssreader.compose.compiler)
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    dependencies {
        implementation(project(":examples:rss-reader:shared"))
        implementation(libs.rssreader.activity.compose)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.rssreader.koin.core)
        implementation(libs.rssreader.koin.android)
        implementation(libs.rssreader.napier)
        implementation(libs.rssreader.multiplatform.settings)
        implementation(libs.rssreader.kotlinx.serialization.json)
        implementation(libs.rssreader.ktor.core)
        implementation(libs.rssreader.core.splashscreen) // AppActivity uses installSplashScreen()
    }
}

android {
    namespace = "com.github.jetbrains.rssreader"
    compileSdk = libs.versions.rssreader.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.github.jetbrains.rssreader"
        minSdk = libs.versions.rssreader.android.min.sdk.get().toInt()
        targetSdk = libs.versions.rssreader.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-sample"
    }

    buildTypes {
        getByName("debug") { isMinifyEnabled = false }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
```

(Release signing, ProGuard, splash screen — all deferred. Debug-only build is plenty for the sample.)

- [ ] **Step 5: Add module to `settings.gradle.kts`**

Add inside `include(...)`:

```kotlin
    ":examples:rss-reader:androidApp",
```

- [ ] **Step 6: Build the Android app**

```bash
./gradlew :examples:rss-reader:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. APK lives under `examples/rss-reader/androidApp/build/outputs/apk/debug/`.

- [ ] **Step 7: Commit**

```bash
git add examples/rss-reader/androidApp settings.gradle.kts
git commit -m "feat(examples): scaffold rss-reader androidApp (upstream verbatim, sync stripped)"
```

### Task 0.5: Smoke-test the unchanged sample on a device or emulator

This task requires a connected device/emulator; the autonomous controller cannot drive `adb`. **The human runs this**, reports back, then the controller tags the baseline.

- [ ] **Step 1 (HUMAN): Install and launch**

```bash
./gradlew :examples:rss-reader:androidApp:installDebug
adb shell am start -n com.github.jetbrains.rssreader/.AppActivity
```

Expected: app launches, default JetBrains Kotlin blog feed loads, scrolling works, FAB → Add Feed dialog opens. **If this fails, stop and debug — the redux-kotlin migration needs a working baseline.**

- [ ] **Step 2 (CONTROLLER): Tag the baseline once the human confirms green**

```bash
git tag baseline-nanoredux
```

---

## Phase 1 — Wire the `:redux-kotlin` dependency into the shared module

### Task 1.1: Add redux-kotlin to shared module dependencies

**Files:**
- Modify: `examples/rss-reader/shared/build.gradle.kts`

(Already done in Task 0.3 Step 3 — `implementation(project(":redux-kotlin"))`.) Verify with:

- [ ] **Step 1: Confirm dependency is present**

```bash
./gradlew :examples:rss-reader:shared:dependencies --configuration commonMainImplementation | grep redux-kotlin
```

Expected: line `\--- project :redux-kotlin`. If missing, add it now and rebuild.

- [ ] **Step 2: No commit** (dependency already added).

---

## Phase 2 — Replace NanoRedux types and write the pure reducer (TDD)

### Task 2.1: Extract `FeedState` and `FeedAction` to their own files (no behavior change)

**Files:**
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedState.kt`
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedAction.kt`
- Modify: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedStore.kt`

- [ ] **Step 1: Create `FeedState.kt`**

```kotlin
package com.github.jetbrains.rssreader.app

import com.github.jetbrains.rssreader.domain.RssFeed

data class FeedState(
    val progress: Boolean,
    val feeds: List<RssFeed>,
    val selectedFeed: RssFeed? = null // null means selected all
)

fun FeedState.mainFeedPosts() =
    (selectedFeed?.channel?.item ?: feeds.flatMap { it.channel?.item ?: emptyList() })
        .sortedByDescending { it.pubDate }
```

Note: the `: State` marker interface is dropped — redux-kotlin doesn't require it.

- [ ] **Step 2: Create `FeedAction.kt`**

```kotlin
package com.github.jetbrains.rssreader.app

import com.github.jetbrains.rssreader.domain.RssFeed

sealed class FeedAction {
    data class Refresh(val forceLoad: Boolean) : FeedAction()
    data class Add(val url: String) : FeedAction()
    data class Delete(val url: String) : FeedAction()
    data class SelectFeed(val feed: RssFeed?) : FeedAction()
    data class Data(val feeds: List<RssFeed>) : FeedAction()
    data class Error(val error: Exception) : FeedAction()
}
```

- [ ] **Step 3: Delete the now-duplicate declarations from `FeedStore.kt`**

In `FeedStore.kt`, delete the `data class FeedState` (lines 14–20 originally) and the `sealed class FeedAction` (lines 22–29). The file keeps `FeedSideEffect` and `class FeedStore` for now.

- [ ] **Step 4: Build**

```bash
./gradlew :examples:rss-reader:shared:assemble
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/
git commit -m "refactor(rss-reader): split FeedState and FeedAction into their own files"
```

### Task 2.2: Write failing test for the pure reducer

**Files:**
- Create: `examples/rss-reader/shared/src/commonTest/kotlin/com/github/jetbrains/rssreader/app/FeedReducerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.jetbrains.rssreader.app

import com.github.jetbrains.rssreader.domain.RssChannel
import com.github.jetbrains.rssreader.domain.RssFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedReducerTest {

    private val initial = FeedState(progress = false, feeds = emptyList())
    private val feedA = RssFeed(sourceUrl = "https://a/feed", channel = RssChannel(null, null, null, emptyList()), isDefault = false)
    private val feedB = RssFeed(sourceUrl = "https://b/feed", channel = RssChannel(null, null, null, emptyList()), isDefault = false)

    @Test
    fun `Refresh while idle flips progress true and keeps feeds`() {
        val next = feedReducer(initial.copy(feeds = listOf(feedA)), FeedAction.Refresh(forceLoad = false))
        assertTrue(next.progress)
        assertEquals(listOf(feedA), next.feeds)
    }

    @Test
    fun `Refresh while in-progress is a no-op`() {
        val state = FeedState(progress = true, feeds = listOf(feedA))
        val next = feedReducer(state, FeedAction.Refresh(forceLoad = false))
        assertEquals(state, next)
    }

    @Test
    fun `Data clears progress and replaces feeds; preserves selection if still present`() {
        val state = FeedState(progress = true, feeds = listOf(feedA), selectedFeed = feedA)
        val next = feedReducer(state, FeedAction.Data(listOf(feedA, feedB)))
        assertEquals(false, next.progress)
        assertEquals(listOf(feedA, feedB), next.feeds)
        assertEquals(feedA, next.selectedFeed)
    }

    @Test
    fun `Data drops selection if previously-selected feed disappeared`() {
        val state = FeedState(progress = true, feeds = listOf(feedA), selectedFeed = feedA)
        val next = feedReducer(state, FeedAction.Data(listOf(feedB)))
        assertNull(next.selectedFeed)
    }

    @Test
    fun `Error while in-progress clears progress; while idle is no-op`() {
        val inProgress = FeedState(progress = true, feeds = listOf(feedA))
        val nextInProgress = feedReducer(inProgress, FeedAction.Error(RuntimeException("x")))
        assertEquals(false, nextInProgress.progress)
        assertEquals(listOf(feedA), nextInProgress.feeds)

        val idle = FeedState(progress = false, feeds = listOf(feedA))
        val nextIdle = feedReducer(idle, FeedAction.Error(RuntimeException("x")))
        assertEquals(idle, nextIdle)
    }

    @Test
    fun `SelectFeed updates selectedFeed when feed exists`() {
        val state = FeedState(progress = false, feeds = listOf(feedA, feedB))
        val next = feedReducer(state, FeedAction.SelectFeed(feedB))
        assertEquals(feedB, next.selectedFeed)
    }

    @Test
    fun `SelectFeed null clears selection`() {
        val state = FeedState(progress = false, feeds = listOf(feedA), selectedFeed = feedA)
        val next = feedReducer(state, FeedAction.SelectFeed(null))
        assertNull(next.selectedFeed)
    }
}
```

- [ ] **Step 2: Run test, confirm it fails because `feedReducer` does not exist**

```bash
./gradlew :examples:rss-reader:shared:jvmTest --tests "com.github.jetbrains.rssreader.app.FeedReducerTest"
```

Expected: compile error `Unresolved reference: feedReducer`.

### Task 2.3: Implement the pure `FeedReducer`

**Files:**
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedReducer.kt`

- [ ] **Step 1: Write the reducer**

```kotlin
package com.github.jetbrains.rssreader.app

import org.reduxkotlin.TypedReducer

/**
 * Pure reducer. All side effects (HTTP, storage, error broadcasting) are handled
 * by [com.github.jetbrains.rssreader.app.feedThunkRefresh] and the side-effect
 * middleware — never here.
 *
 * Note: an Action that is invalid for the current state (e.g. Refresh while progress=true,
 * or Data while progress=false) is treated as a no-op here. The thunk layer is responsible
 * for not dispatching such actions; if one slips through, the original NanoRedux store
 * also emitted a FeedSideEffect.Error("Unexpected action") — we preserve that behavior in
 * [com.github.jetbrains.rssreader.app.feedSideEffectMiddleware] in Phase 3.
 */
val feedReducer: TypedReducer<FeedState, FeedAction> = { state, action ->
    when (action) {
        is FeedAction.Refresh -> if (state.progress) state else state.copy(progress = true)
        is FeedAction.Add -> if (state.progress) state else state.copy(progress = true)
        is FeedAction.Delete -> if (state.progress) state else state.copy(progress = true)
        is FeedAction.SelectFeed -> when {
            action.feed == null -> state.copy(selectedFeed = null)
            state.feeds.contains(action.feed) -> state.copy(selectedFeed = action.feed)
            else -> state
        }
        is FeedAction.Data -> if (state.progress) {
            val preservedSelection = state.selectedFeed?.takeIf { it in action.feeds }
            state.copy(progress = false, feeds = action.feeds, selectedFeed = preservedSelection)
        } else state
        is FeedAction.Error -> if (state.progress) state.copy(progress = false) else state
    }
}
```

- [ ] **Step 2: Run tests, confirm they pass**

```bash
./gradlew :examples:rss-reader:shared:jvmTest --tests "com.github.jetbrains.rssreader.app.FeedReducerTest"
```

Expected: 7 tests passing, 0 failures.

- [ ] **Step 3: Commit**

```bash
git add examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedReducer.kt examples/rss-reader/shared/src/commonTest/kotlin/com/github/jetbrains/rssreader/app/FeedReducerTest.kt
git commit -m "feat(rss-reader): add pure FeedReducer + tests"
```

---

## Phase 3 — Thunks and side-effect middleware

### Task 3.1: Move `FeedSideEffect` to its own file

**Files:**
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedSideEffect.kt`
- Modify: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedStore.kt`

- [ ] **Step 1: Create `FeedSideEffect.kt`**

```kotlin
package com.github.jetbrains.rssreader.app

sealed class FeedSideEffect {
    data class Error(val error: Exception) : FeedSideEffect()
}
```

- [ ] **Step 2: Delete the `sealed class FeedSideEffect` block from `FeedStore.kt`**

(Leave `class FeedStore` itself in place for now — it disappears in Phase 7.)

- [ ] **Step 3: Build**

```bash
./gradlew :examples:rss-reader:shared:assemble
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedSideEffect.kt examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedStore.kt
git commit -m "refactor(rss-reader): split FeedSideEffect into its own file"
```

### Task 3.2: Implement the side-effect middleware

**Files:**
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/SideEffectMiddleware.kt`

- [ ] **Step 1: Write it**

```kotlin
package com.github.jetbrains.rssreader.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.reduxkotlin.Middleware

/**
 * Side-effect bus. The store dispatches a [FeedSideEffect] like any other action; this
 * middleware intercepts it, emits to [effects], and does NOT pass it to the reducer
 * (returns it directly without calling next).
 *
 * Replay = 0 means a missing subscriber drops the effect — fine for transient UI snackbars.
 * extraBufferCapacity = 16 prevents `tryEmit` failures under bursts.
 */
class SideEffectBus {
    private val _effects = MutableSharedFlow<FeedSideEffect>(replay = 0, extraBufferCapacity = 16)
    val effects: SharedFlow<FeedSideEffect> = _effects.asSharedFlow()

    fun middleware(): Middleware<FeedState> = { _ ->
        { next ->
            { action ->
                if (action is FeedSideEffect) {
                    _effects.tryEmit(action)
                    action
                } else {
                    next(action)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :examples:rss-reader:shared:assemble
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/SideEffectMiddleware.kt
git commit -m "feat(rss-reader): add side-effect middleware + bus"
```

### Task 3.3: Implement thunks for async work

**Files:**
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedThunks.kt`

- [ ] **Step 1: Write thunks**

```kotlin
package com.github.jetbrains.rssreader.app

import com.github.jetbrains.rssreader.core.RssReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.reduxkotlin.Dispatcher
import org.reduxkotlin.GetState

/**
 * A [Thunk] is a function dispatched in place of an action. The thunk middleware
 * recognizes it, invokes it with the store's dispatch + getState, and short-circuits
 * the reducer. See [com.github.jetbrains.rssreader.app.ThunkMiddleware].
 */
typealias Thunk = (dispatch: Dispatcher, getState: GetState<FeedState>) -> Any

/**
 * Coroutine scope used by every thunk. In a richer app we'd inject this (and the
 * coroutine context) for testability; for the sample, a top-level Main-dispatched
 * scope mirrors the original [FeedStore].
 */
private val thunkScope = CoroutineScope(Dispatchers.Main)

fun refresh(rssReader: RssReader, forceLoad: Boolean): Thunk = { dispatch, getState ->
    if (getState().progress) {
        dispatch(FeedSideEffect.Error(IllegalStateException("In progress")))
    } else {
        dispatch(FeedAction.Refresh(forceLoad))
        thunkScope.launch {
            try {
                val feeds = rssReader.getAllFeeds(forceLoad)
                dispatch(FeedAction.Data(feeds))
            } catch (e: Exception) {
                dispatch(FeedAction.Error(e))
                dispatch(FeedSideEffect.Error(e))
            }
        }
    }
}

fun addFeed(rssReader: RssReader, url: String): Thunk = { dispatch, getState ->
    if (getState().progress) {
        dispatch(FeedSideEffect.Error(IllegalStateException("In progress")))
    } else {
        dispatch(FeedAction.Add(url))
        thunkScope.launch {
            try {
                rssReader.addFeed(url)
                val feeds = rssReader.getAllFeeds(forceUpdate = false)
                dispatch(FeedAction.Data(feeds))
            } catch (e: Exception) {
                dispatch(FeedAction.Error(e))
                dispatch(FeedSideEffect.Error(e))
            }
        }
    }
}

fun deleteFeed(rssReader: RssReader, url: String): Thunk = { dispatch, getState ->
    if (getState().progress) {
        dispatch(FeedSideEffect.Error(IllegalStateException("In progress")))
    } else {
        dispatch(FeedAction.Delete(url))
        thunkScope.launch {
            try {
                rssReader.deleteFeed(url)
                val feeds = rssReader.getAllFeeds(forceUpdate = false)
                dispatch(FeedAction.Data(feeds))
            } catch (e: Exception) {
                dispatch(FeedAction.Error(e))
                dispatch(FeedSideEffect.Error(e))
            }
        }
    }
}
```

- [ ] **Step 2: Build (will fail — Thunk middleware not yet present)**

```bash
./gradlew :examples:rss-reader:shared:assemble
```

Expected: BUILD SUCCESSFUL (no middleware reference yet — thunks are just function values). If you see "smart cast impossible", inline `getState().progress` into a local val.

- [ ] **Step 3: Commit**

```bash
git add examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedThunks.kt
git commit -m "feat(rss-reader): add Thunk type alias + refresh/add/delete thunks"
```

---

## Phase 4 — Thunk middleware and store factory

### Task 4.1: Write failing test for thunk middleware

**Files:**
- Create: `examples/rss-reader/shared/src/commonTest/kotlin/com/github/jetbrains/rssreader/app/ThunkMiddlewareTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.github.jetbrains.rssreader.app

import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals

class ThunkMiddlewareTest {

    @Test
    fun `thunk is invoked with dispatch + getState and not passed to reducer`() {
        var reducerSawAction: Any? = null
        val store = createStore<FeedState>(
            reducer = { state, action -> reducerSawAction = action; state },
            preloadedState = FeedState(progress = false, feeds = emptyList()),
            enhancer = applyMiddleware(thunkMiddleware()),
        )

        var thunkRan = false
        val noopThunk: Thunk = { _, getState ->
            thunkRan = true
            assertEquals(false, getState().progress)
            Unit
        }

        store.dispatch(noopThunk)

        assertEquals(true, thunkRan)
        // The store's INIT action is the only thing the reducer should have seen.
        assertEquals("INIT", (reducerSawAction as? org.reduxkotlin.ActionTypes)?.name ?: "INIT")
    }

    @Test
    fun `non-thunk actions pass through to reducer`() {
        var lastAction: Any? = null
        val store = createStore<FeedState>(
            reducer = { state, action -> lastAction = action; state },
            preloadedState = FeedState(progress = false, feeds = emptyList()),
            enhancer = applyMiddleware(thunkMiddleware()),
        )
        store.dispatch(FeedAction.SelectFeed(null))
        assertEquals(FeedAction.SelectFeed(null), lastAction)
    }
}
```

- [ ] **Step 2: Run, confirm it fails because `thunkMiddleware` doesn't exist**

```bash
./gradlew :examples:rss-reader:shared:jvmTest --tests "com.github.jetbrains.rssreader.app.ThunkMiddlewareTest"
```

Expected: `Unresolved reference: thunkMiddleware`.

### Task 4.2: Implement `thunkMiddleware`

**Files:**
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/ThunkMiddleware.kt`

- [ ] **Step 1: Write it**

```kotlin
package com.github.jetbrains.rssreader.app

import org.reduxkotlin.Middleware

/**
 * Recognizes a [Thunk] dispatched in place of an action: invokes it with the store's
 * dispatch + getState, returns its result, and does NOT pass the thunk on to the reducer.
 * Anything that isn't a Thunk passes through unchanged.
 *
 * This is a hand-rolled equivalent of the redux-kotlin-thunk middleware, inlined to keep
 * the sample dep-free.
 */
fun thunkMiddleware(): Middleware<FeedState> = { store ->
    { next ->
        { action ->
            @Suppress("UNCHECKED_CAST")
            val asThunk = action as? Thunk
            if (asThunk != null) asThunk(store.dispatch, store.getState)
            else next(action)
        }
    }
}
```

- [ ] **Step 2: Run tests, confirm they pass**

```bash
./gradlew :examples:rss-reader:shared:jvmTest --tests "com.github.jetbrains.rssreader.app.ThunkMiddlewareTest"
```

Expected: 2 tests passing.

- [ ] **Step 3: Commit**

```bash
git add examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/ThunkMiddleware.kt examples/rss-reader/shared/src/commonTest/kotlin/com/github/jetbrains/rssreader/app/ThunkMiddlewareTest.kt
git commit -m "feat(rss-reader): add thunk middleware + tests"
```

### Task 4.3: Store factory + `StateFlow` adapter

**Files:**
- Create: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/StoreFactory.kt`

- [ ] **Step 1: Write it**

```kotlin
package com.github.jetbrains.rssreader.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.reduxkotlin.Store
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore

/**
 * Holds the redux-kotlin [Store], plus the side-effect [SharedFlow] and a derived
 * [StateFlow] that Compose can `collectAsState()` against.
 *
 * One instance per app via Koin (Phase 6).
 */
class FeedStoreHolder(
    private val sideEffectBus: SideEffectBus = SideEffectBus(),
) {
    val store: Store<FeedState> = createStore(
        reducer = { state, action ->
            // typedReducer would do this for us but we use a TypedReducer<FeedState, FeedAction>
            // directly and let unknown actions fall through unchanged.
            when (action) {
                is FeedAction -> feedReducer(state, action)
                else -> state
            }
        },
        preloadedState = FeedState(progress = false, feeds = emptyList()),
        enhancer = applyMiddleware(
            sideEffectBus.middleware(), // intercept FeedSideEffect first
            thunkMiddleware(),          // then handle Thunks
        ),
    )

    val state: StateFlow<FeedState> = store.toStateFlow()

    val sideEffects: SharedFlow<FeedSideEffect> = sideEffectBus.effects
}

/**
 * Bridges a redux-kotlin [Store] to a Kotlin [StateFlow] so Compose can subscribe with
 * `collectAsState()`. The subscription lives for the lifetime of the store (process).
 */
private fun <S> Store<S>.toStateFlow(): StateFlow<S> {
    val flow = MutableStateFlow(state)
    subscribe { flow.value = state }
    return flow.asStateFlow()
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :examples:rss-reader:shared:assemble
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/StoreFactory.kt
git commit -m "feat(rss-reader): add FeedStoreHolder (Store + StateFlow + side-effect bus)"
```

---

## Phase 5 — Rewire Compose UI to consume `FeedStoreHolder`

### Task 5.1: Update `MainFeed.kt` to take `FeedStoreHolder` and dispatch thunks

**Files:**
- Modify: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/ui/MainFeed.kt`

- [ ] **Step 1: Replace `FeedStore` with `FeedStoreHolder`, update imports and dispatch**

```kotlin
package com.github.jetbrains.rssreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jetbrains.rssreader.app.FeedAction
import com.github.jetbrains.rssreader.app.FeedStoreHolder
import com.github.jetbrains.rssreader.domain.Item
import com.github.jetbrains.rssreader.domain.RssFeed
import kotlinx.coroutines.launch

@Composable
fun MainFeed(
    storeHolder: FeedStoreHolder,
    onPostClick: (Item) -> Unit,
    onEditClick: () -> Unit,
) {
    val state = storeHolder.state.collectAsState()
    val posts = remember(state.value.feeds, state.value.selectedFeed) {
        (state.value.selectedFeed?.channel?.item
            ?: state.value.feeds.flatMap { it.channel?.item ?: emptyList() })
            .sortedByDescending { it.pubDate }
    }
    Column {
        val coroutineScope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        PostList(
            modifier = Modifier.weight(1f),
            posts = posts,
            listState = listState,
        ) { post -> onPostClick(post) }
        MainFeedBottomBar(
            feeds = state.value.feeds,
            selectedFeed = state.value.selectedFeed,
            onFeedClick = { feed ->
                coroutineScope.launch { listState.scrollToItem(0) }
                storeHolder.store.dispatch(FeedAction.SelectFeed(feed))
            },
            onEditClick = onEditClick,
        )
        Spacer(
            Modifier
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .fillMaxWidth()
        )
    }
}

private sealed class Icons {
    object All : Icons()
    class FeedIcon(val feed: RssFeed) : Icons()
    object Edit : Icons()
}

@Composable
fun MainFeedBottomBar(
    feeds: List<RssFeed>,
    selectedFeed: RssFeed?,
    onFeedClick: (RssFeed?) -> Unit,
    onEditClick: () -> Unit,
) {
    val items = buildList {
        add(Icons.All)
        addAll(feeds.map { Icons.FeedIcon(it) })
        add(Icons.Edit)
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        this.items(items) { item ->
            when (item) {
                is Icons.All -> FeedIcon(
                    feed = null,
                    isSelected = selectedFeed == null,
                    onClick = { onFeedClick(null) },
                )
                is Icons.FeedIcon -> FeedIcon(
                    feed = item.feed,
                    isSelected = selectedFeed == item.feed,
                    onClick = { onFeedClick(item.feed) },
                )
                is Icons.Edit -> EditIcon(onClick = onEditClick)
            }
            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}
```

- [ ] **Step 2: Don't commit yet** — multiple UI files change together, commit at end of Task 5.4.

### Task 5.2: Update `FeedList.kt` to dispatch thunks for add/delete

**Files:**
- Modify: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/ui/FeedList.kt`

- [ ] **Step 1: Update**

```kotlin
package com.github.jetbrains.rssreader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jetbrains.rssreader.app.FeedStoreHolder
import com.github.jetbrains.rssreader.app.addFeed
import com.github.jetbrains.rssreader.app.deleteFeed
import com.github.jetbrains.rssreader.core.RssReader
import com.github.jetbrains.rssreader.domain.RssFeed
import org.koin.compose.koinInject

@Composable
fun FeedList(storeHolder: FeedStoreHolder) {
    val rssReader: RssReader = koinInject()
    Box(modifier = Modifier.fillMaxSize()) {
        val state = storeHolder.state.collectAsState()
        val showAddDialog = remember { mutableStateOf(false) }
        val feedForDelete = remember<MutableState<RssFeed?>> { mutableStateOf(null) }
        FeedItemList(feeds = state.value.feeds) { feedForDelete.value = it }
        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding()
                .imePadding(),
            onClick = { showAddDialog.value = true },
        ) {
            Image(
                imageVector = Icons.Default.Add,
                modifier = Modifier.align(Alignment.Center),
                contentDescription = null,
            )
        }
        if (showAddDialog.value) {
            AddFeedDialog(
                onAdd = {
                    storeHolder.store.dispatch(addFeed(rssReader, it))
                    showAddDialog.value = false
                },
                onDismiss = { showAddDialog.value = false },
            )
        }
        feedForDelete.value?.let { feed ->
            DeleteFeedDialog(
                feed = feed,
                onDelete = {
                    storeHolder.store.dispatch(deleteFeed(rssReader, feed.sourceUrl))
                    feedForDelete.value = null
                },
                onDismiss = { feedForDelete.value = null },
            )
        }
    }
}

@Composable
fun FeedItemList(feeds: List<RssFeed>, onClick: (RssFeed) -> Unit) {
    LazyColumn {
        itemsIndexed(feeds) { i, feed ->
            if (i == 0) Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            FeedItem(feed) { onClick(feed) }
        }
    }
}

@Composable
fun FeedItem(feed: RssFeed, onClick: () -> Unit) {
    Row(
        Modifier
            .clickable(onClick = onClick, enabled = !feed.isDefault)
            .padding(16.dp),
    ) {
        FeedIcon(feed = feed)
        Spacer(modifier = Modifier.size(16.dp))
        Column {
            feed.channel?.title?.let { title ->
                Text(style = MaterialTheme.typography.bodyMedium, text = title)
            }
            feed.channel?.description?.let { description ->
                Text(style = MaterialTheme.typography.bodySmall, text = description)
            }
        }
    }
}
```

### Task 5.3: Update `MainScreen` and `FeedListScreen` to inject `FeedStoreHolder`

**Files:**
- Modify: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/ui/Screen.kt`

- [ ] **Step 1: Read the file first to confirm signatures** (it defines `enum class Screen`, `RssFeedAppBar`, `MainScreen`, `FeedListScreen`). Update `MainScreen` and `FeedListScreen` to:

```kotlin
@Composable
fun MainScreen(
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val storeHolder: FeedStoreHolder = koinInject()
    MainFeed(
        storeHolder = storeHolder,
        onPostClick = { /* TODO: navigate to post detail — same as upstream */ },
        onEditClick = onEditClick,
    )
}

@Composable
fun FeedListScreen() {
    val storeHolder: FeedStoreHolder = koinInject()
    FeedList(storeHolder)
}
```

Add imports for `FeedStoreHolder` and `koinInject`.

### Task 5.4: Update `RssReaderApp.kt` to use `FeedStoreHolder.sideEffects` for snackbar

**Files:**
- Modify: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/RssReaderApp.kt`

- [ ] **Step 1: Replace the side-effect observation block**

Change the bottom of the `Scaffold` content block from:

```kotlin
            val store: FeedStore = koinInject<FeedStore>()
            val error = store.observeSideEffect()
                .filterIsInstance<FeedSideEffect.Error>()
                .collectAsState(null)
            LaunchedEffect(error.value) {
                error.value?.let {
                    snackbarHostState.showSnackbar(it.error.message.toString())
                }
            }
```

to:

```kotlin
            val storeHolder: FeedStoreHolder = koinInject()
            val error = storeHolder.sideEffects
                .filterIsInstance<FeedSideEffect.Error>()
                .collectAsState(null)
            LaunchedEffect(error.value) {
                error.value?.let {
                    snackbarHostState.showSnackbar(it.error.message.toString())
                }
            }
```

Update imports: remove `FeedStore`, add `FeedStoreHolder`.

- [ ] **Step 2: Build (will fail until Koin is updated in Phase 6 — fine)**

```bash
./gradlew :examples:rss-reader:shared:assemble
```

Expected: BUILD SUCCESSFUL (Koin lookups are runtime, not compile-time).

- [ ] **Step 3: Commit UI changes together**

```bash
git add examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/ui/MainFeed.kt examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/ui/FeedList.kt examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/ui/Screen.kt examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/RssReaderApp.kt
git commit -m "refactor(rss-reader): UI consumes FeedStoreHolder + dispatches thunks"
```

---

## Phase 6 — Koin module

### Task 6.1: Replace `FeedStore` with `FeedStoreHolder` in the Koin module

**Files:**
- Modify: `examples/rss-reader/androidApp/src/main/kotlin/com/github/jetbrains/rssreader/App.kt`

- [ ] **Step 1: Update `appModule`**

```kotlin
    private val appModule = module {
        single { buildRssReader(get<Context>(), BuildConfig.DEBUG) }
        single { FeedStoreHolder() }
    }
```

Update imports: remove `FeedStore`, add `FeedStoreHolder`.

- [ ] **Step 2: Build**

```bash
./gradlew :examples:rss-reader:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3 (HUMAN): Smoke-test on device**

```bash
./gradlew :examples:rss-reader:androidApp:installDebug
adb shell am start -n com.github.jetbrains.rssreader/.AppActivity
```

Expected: app launches. Feeds still load (initial refresh now needs to be triggered explicitly — see next task).

### Task 6.2: Trigger initial refresh on app start

**Files:**
- Modify: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/RssReaderApp.kt`

- [ ] **Step 1: Add a `LaunchedEffect(Unit)` that dispatches the refresh thunk once**

Near the top of the `Scaffold` content block, add:

```kotlin
            val rssReader: RssReader = koinInject()
            LaunchedEffect(Unit) {
                storeHolder.store.dispatch(refresh(rssReader, forceLoad = false))
            }
```

Add imports for `RssReader` and `refresh`.

Reason: NanoRedux `FeedStore` was idle until something dispatched `FeedAction.Refresh`. Upstream's `AppActivity` doesn't trigger that (it only `setContent { RssReaderApp() }`). Putting an explicit `LaunchedEffect(Unit)` here makes the initial load deterministic and survives config changes correctly.

- [ ] **Step 2: Confirm `AppActivity.kt` does not duplicate the dispatch**

```bash
grep -n "FeedAction\.Refresh\|store\.dispatch" examples/rss-reader/androidApp/src/main/kotlin/com/github/jetbrains/rssreader/AppActivity.kt
```

Expected: no matches.

- [ ] **Step 3: Build, then HUMAN smoke-tests on device**

```bash
./gradlew :examples:rss-reader:androidApp:assembleDebug
```

Then human runs:
```bash
./gradlew :examples:rss-reader:androidApp:installDebug
adb shell am start -n com.github.jetbrains.rssreader/.AppActivity
```

Expected: app launches, feed loads automatically.

- [ ] **Step 4: Commit**

```bash
git add examples/rss-reader/androidApp/src/main/kotlin/com/github/jetbrains/rssreader/App.kt examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/RssReaderApp.kt
git commit -m "feat(rss-reader): provide FeedStoreHolder via Koin + dispatch initial refresh"
```

---

## Phase 7 — Delete NanoRedux and verify

### Task 7.1: Delete the dead files

**Files:**
- Delete: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/NanoRedux.kt`
- Delete: `examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedStore.kt`

- [ ] **Step 1: Verify no remaining references**

```bash
grep -rn "NanoRedux\|class FeedStore\|: State\b\|: Action\b\|: Effect\b" examples/rss-reader/shared/src/
```

Expected: no matches.

- [ ] **Step 2: Delete the files**

```bash
git rm examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/NanoRedux.kt examples/rss-reader/shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/FeedStore.kt
```

- [ ] **Step 3: Build + test (controller), then HUMAN smoke-tests**

```bash
./gradlew :examples:rss-reader:shared:jvmTest :examples:rss-reader:androidApp:assembleDebug
```

Then human runs:
```bash
./gradlew :examples:rss-reader:androidApp:installDebug
adb shell am start -n com.github.jetbrains.rssreader/.AppActivity
```

Expected: all green, app still works — feeds load on launch, FAB → add dialog works, deleting a feed works, error path triggers a snackbar (force one by adding an invalid URL).

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(rss-reader): remove NanoRedux — migration to redux-kotlin complete"
```

### Task 7.2: Add a README pointing readers at the migration

**Files:**
- Create: `examples/rss-reader/README.md`

- [ ] **Step 1: Write it**

```markdown
# RSS Reader (redux-kotlin sample)

This example is a fork of JetBrains'
[`kmp-production-sample`](https://github.com/Kotlin/kmp-production-sample)
RSS reader, migrated from its hand-rolled `NanoRedux` store to `org.reduxkotlin.createStore`
plus a small thunk middleware for async work and a side-effect middleware for transient
UI events (snackbars). The Compose UI is unchanged in shape — only the data plumbing
moved.

See `git log baseline-nanoredux..HEAD -- examples/rss-reader/` for the migration diff.

## Architecture (commonMain/app/)

| File                      | Purpose                                                         |
| ------------------------- | --------------------------------------------------------------- |
| `FeedState.kt`            | Immutable state shape                                           |
| `FeedAction.kt`           | Sealed action hierarchy (dispatched by reducer)                 |
| `FeedReducer.kt`          | Pure `(State, Action) → State`                                  |
| `FeedThunks.kt`           | Async functions dispatched in place of actions                  |
| `FeedSideEffect.kt`       | Transient one-shot effects (errors)                             |
| `ThunkMiddleware.kt`      | Runs thunks; passes plain actions through                       |
| `SideEffectMiddleware.kt` | Pulls `FeedSideEffect` out of the action stream into a SharedFlow |
| `StoreFactory.kt`         | Builds the `Store<FeedState>` and exposes a `StateFlow`         |

## Running

```bash
./gradlew :examples:rss-reader:androidApp:installDebug
```

License: MIT (inherited from upstream — see `LICENSE` at repo root for redux-kotlin, and
the upstream MIT notice retained in source headers).
```

- [ ] **Step 2: Commit**

```bash
git add examples/rss-reader/README.md
git commit -m "docs(rss-reader): explain the redux-kotlin migration"
```

### Task 7.3: Attribution / LICENSE

**Files:**
- Create: `examples/rss-reader/NOTICE`

- [ ] **Step 1: Add upstream attribution**

```
This example incorporates code from JetBrains' kmp-production-sample
(https://github.com/Kotlin/kmp-production-sample), licensed under MIT.

Copyright (c) JetBrains s.r.o.
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
```

- [ ] **Step 2: Commit**

```bash
git add examples/rss-reader/NOTICE
git commit -m "docs(rss-reader): add NOTICE crediting upstream JetBrains MIT sample"
```

---

## Self-Review checklist (run after writing)

- **Spec coverage:** Every Phase from the architecture section above has at least one task. ✓
- **No placeholders:** Searched for "TODO", "TBD" — one `TODO` remains in Task 5.3 for the post-detail navigation, which mirrors upstream and is out of scope. Labeled, not pretending to be done.
- **Type consistency:** `FeedStoreHolder` is used consistently (Phase 4, 5, 6). `Thunk` typealias is defined in Phase 3 before use. `feedReducer` is `TypedReducer<FeedState, FeedAction>` and called in `StoreFactory.kt` with the `when (action) { is FeedAction -> ... }` wrapper to fit redux-kotlin's untyped `Reducer<State>` shape. ✓
- **Async strategy is explicit:** Thunks own all coroutine work; the reducer is pure. Errors flow through both a reducer-visible `FeedAction.Error` (to clear `progress`) AND a `FeedSideEffect.Error` (for the snackbar). ✓
- **Build verification at every phase boundary:** every commit step is preceded by a Gradle command + expected output. ✓
