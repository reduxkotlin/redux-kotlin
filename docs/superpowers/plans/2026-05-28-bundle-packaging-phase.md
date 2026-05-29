# redux-kotlin bundle / BOM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ship a "one and all" dependency: `redux-kotlin-bundle` (one dep → threadsafe + registry + routing + granular + multimodel APIs, plus convenience store factories), `redux-kotlin-bundle-compose` (base + Compose bindings), and `redux-kotlin-bom` (version-alignment platform for à-la-carte users).

**Architecture:** The bundles are aggregator modules that re-export their members via `api(...)`; the base bundle adds one hand-written file of convenience helpers that compose existing public APIs (`ThreadSafeStore(createModelStore(...))` + registry get-or-create). The BOM is a `java-platform` module of version constraints published via vanniktech `JavaPlatform`.

**Tech Stack:** Kotlin 2.3.20 KMP, existing convention plugins (`convention.library-mpp-loved`, `convention.publishing-mpp`), vanniktech maven-publish 0.36.0, detekt (`explicitApi` + KDoc), binary-compatibility-validator.

**Spec:** `docs/superpowers/specs/2026-05-28-bundle-packaging-design.md`.

**Branch:** `feat/redux-kotlin-bundle`, stacked on `feat/redux-kotlin-routing` (PR #303). Do not switch branches.

---

## Verified facts (don't re-derive)

- `ThreadSafeStore<State>(store: Store<State>)` is a **public** constructor wrapping any `Store` (threadsafe module).
- `createModelStore(enhancer: StoreEnhancer<ModelState>? = null, devChecks = false, onWrite = null, block): Store<ModelState>` (routing module).
- `StoreRegistry<K, S>.getOrCreate(id: K, creator: () -> Store<S>): Store<S>` and `TypedStoreRegistry.getOrCreate(key: StoreKey<K,S>, creator)` are concurrency-safe (double-checked `synchronized`). `storeKey<reified S>(id): StoreKey<K,S>` is the public key factory.
- `api`-dep closure: bundle `api`s threadsafe + registry + routing(+core,+multimodel) + multimodel-granular(+granular,+multimodel) → consumer gets core, threadsafe, granular, multimodel, multimodel-granular, registry, routing.
- multimodel-granular public symbol for the surface test: `org.reduxkotlin.multimodel.granular.subscribeToModel`.
- Both `convention.publishing` and `convention.publishing-mpp` hard-bind to vanniktech `KotlinMultiplatform`, so the BOM (java-platform) needs a separate publishing convention.
- Library modules need an `android { namespace }` block guarded by a `hasAndroidSdk` check (mirror `redux-kotlin-compose-multimodel`).

---

## File structure

- `redux-kotlin-bundle/` — `build.gradle.kts`; `src/commonMain/kotlin/org/reduxkotlin/bundle/StoreFactory.kt` (`createThreadSafeModelStore`); `.../RegistryExtensions.kt` (the two `getOrCreateThreadSafeModelStore`); `src/commonTest/.../*Test.kt` + `Fixtures.kt`; `src/jvmTest/.../concurrency/`; `api/`; `README.md`.
- `redux-kotlin-bundle-compose/` — `build.gradle.kts`; `src/commonTest/.../BundleComposeSurfaceTest.kt`; `api/`; `README.md`.
- `redux-kotlin-bom/` — `build.gradle.kts` (java-platform constraints).
- `build-conventions/src/main/kotlin/convention.publishing-platform.gradle.kts` — vanniktech `JavaPlatform` publishing for the BOM.
- Modify: `settings.gradle.kts`.

---

## Conventions (every task)
- **TDD** where code exists. detekt on commit (`detektAll --auto-correct`); NEVER `--no-verify`; re-stage if rewritten.
- `explicitApi()` is on for the bundle modules → public helpers need explicit `public` + KDoc (provided below).
- Module fast loop: `./gradlew :redux-kotlin-bundle:jvmTest`.

---

### Task 0: Register the three modules in settings

**Files:** `settings.gradle.kts`

- [ ] **Step 1:** In `settings.gradle.kts` `include(...)`, add after `":redux-kotlin-routing",`:
```kotlin
    ":redux-kotlin-bundle",
    ":redux-kotlin-bundle-compose",
    ":redux-kotlin-bom",
```

- [ ] **Step 2:** Verify configuration: `./gradlew help --quiet` → no "project not found" / settings error (the modules have no build files yet, so this only validates settings parses; expect success or a benign "no build file" — if it errors on missing build.gradle.kts, proceed to Task 1 which adds them and re-validate there).

- [ ] **Step 3: Commit**
```bash
git add settings.gradle.kts
git commit -m "build(bundle): register bundle, bundle-compose, bom modules"
```

---

### Task 1: Scaffold redux-kotlin-bundle (aggregation only)

**Files:** `redux-kotlin-bundle/build.gradle.kts`, `redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/.gitkeep`

- [ ] **Step 1: Create `redux-kotlin-bundle/build.gradle.kts`** (mirrors `redux-kotlin-compose-multimodel` minus Compose):
```kotlin
plugins {
    id("convention.library-mpp-loved")
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
            namespace = "org.reduxkotlin.bundle"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-threadsafe"))
                api(project(":redux-kotlin-registry"))
                api(project(":redux-kotlin-routing"))
                api(project(":redux-kotlin-multimodel-granular"))
            }
        }
    }
}
```

- [ ] **Step 2:** Create `redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/.gitkeep` (empty) so the source set exists.

- [ ] **Step 3:** Verify it configures + compiles empty: `./gradlew :redux-kotlin-bundle:compileKotlinJvm` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
```bash
git add settings.gradle.kts redux-kotlin-bundle/build.gradle.kts redux-kotlin-bundle/src
git commit -m "build(bundle): scaffold redux-kotlin-bundle aggregator"
```

---

### Task 2: createThreadSafeModelStore factory

**Files:** `redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/StoreFactory.kt`, `redux-kotlin-bundle/src/commonTest/kotlin/org/reduxkotlin/bundle/Fixtures.kt`, `.../StoreFactoryTest.kt`

- [ ] **Step 1: Create test fixtures** `Fixtures.kt`:
```kotlin
package org.reduxkotlin.bundle

import org.reduxkotlin.routing.Reduce
import org.reduxkotlin.routing.ReduxInitial

internal data class CounterModel(val count: Int = 0)

internal data class Increment(val by: Int)
internal object Reset

internal fun counterInitial(): CounterModel = CounterModel()
internal fun onIncrement(s: CounterModel, a: Increment): CounterModel = s.copy(count = s.count + a.by)
internal fun onReset(s: CounterModel, a: Reset): CounterModel = CounterModel()
```
(The `@Reduce`/`@ReduxInitial` imports prove the routing annotations are reachable through the bundle; the functions are wired by hand in tests via the DSL, so the annotations are unused here — drop the two imports if detekt flags `UnusedImport`. The fixtures use the hand DSL, not codegen.)

- [ ] **Step 2: Write the failing test** `StoreFactoryTest.kt`:
```kotlin
package org.reduxkotlin.bundle

import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreFactoryTest {
    private fun store() = createThreadSafeModelStore {
        model(counterInitial()) {
            on<Increment> { s, a -> onIncrement(s, a) }
            on<Reset> { s, a -> onReset(s, a) }
        }
    }

    @Test
    fun thread_safe_model_store_dispatches() {
        val s = store()
        s.dispatch(Increment(3))
        s.dispatch(Increment(4))
        assertEquals(7, s.state.get<CounterModel>().count)
        s.dispatch(Reset)
        assertEquals(0, s.state.get<CounterModel>().count)
    }
}
```
(`model`/`on`/`state.get` resolve through the bundle's `api(routing)`/`api(multimodel)`.)

- [ ] **Step 3: Run → fails** (`createThreadSafeModelStore` unresolved): `./gradlew :redux-kotlin-bundle:jvmTest --tests "org.reduxkotlin.bundle.StoreFactoryTest"` → FAIL.

- [ ] **Step 4: Implement `StoreFactory.kt`:**
```kotlin
package org.reduxkotlin.bundle

import org.reduxkotlin.Store
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.routing.OnWrite
import org.reduxkotlin.routing.RoutingBuilder
import org.reduxkotlin.routing.createModelStore
import org.reduxkotlin.threadsafe.ThreadSafeStore

/**
 * Builds a routed [ModelState] store (see `createModelStore`) wrapped in a
 * thread-safe store for cross-thread access. The optional [enhancer]
 * (e.g. `applyMiddleware(...)`) is applied to the routed store before
 * wrapping, so middleware runs inside the synchronized dispatch.
 *
 * @param enhancer optional store enhancer forwarded to `createModelStore`.
 * @param devChecks forwarded: throws on a wasteful structurally-equal write.
 * @param onWrite forwarded: observes effective model writes.
 * @param block registers models and handlers via the routing DSL.
 */
public fun createThreadSafeModelStore(
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> =
    ThreadSafeStore(
        createModelStore(enhancer = enhancer, devChecks = devChecks, onWrite = onWrite, block = block),
    )
```

- [ ] **Step 5: Run → passes**: `./gradlew :redux-kotlin-bundle:jvmTest --tests "org.reduxkotlin.bundle.StoreFactoryTest"` → PASS.

- [ ] **Step 6: Commit**
```bash
git add redux-kotlin-bundle/src
git commit -m "feat(bundle): add createThreadSafeModelStore convenience factory"
```

---

### Task 3: Registry get-or-create extensions

**Files:** `redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/RegistryExtensions.kt`, `.../RegistryExtensionsTest.kt`

- [ ] **Step 1: Write the failing test** `RegistryExtensionsTest.kt`:
```kotlin
package org.reduxkotlin.bundle

import org.reduxkotlin.registry.StoreRegistry
import org.reduxkotlin.registry.TypedStoreRegistry
import org.reduxkotlin.registry.storeKey
import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RegistryExtensionsTest {
    private fun blockFor(): org.reduxkotlin.routing.RoutingBuilder.() -> Unit = {
        model(counterInitial()) { on<Increment> { s, a -> onIncrement(s, a) } }
    }

    @Test
    fun store_registry_get_or_create_is_cached_per_id() {
        val registry = StoreRegistry<String, ModelState>()
        val a1 = registry.getOrCreateThreadSafeModelStore("a", block = blockFor())
        val a2 = registry.getOrCreateThreadSafeModelStore("a", block = blockFor())
        val b = registry.getOrCreateThreadSafeModelStore("b", block = blockFor())
        assertSame(a1, a2)               // same id → same instance
        assertSame(b, b)
        a1.dispatch(Increment(5))
        assertEquals(5, a1.state.get<CounterModel>().count)
        assertEquals(0, b.state.get<CounterModel>().count) // distinct store
    }

    @Test
    fun typed_store_registry_get_or_create_works() {
        val registry = TypedStoreRegistry()
        val key = storeKey<String, ModelState>("counter")
        val s1 = registry.getOrCreateThreadSafeModelStore(key, block = blockFor())
        val s2 = registry.getOrCreateThreadSafeModelStore(key, block = blockFor())
        assertSame(s1, s2)
    }
}
```
NOTE: confirm the `StoreRegistry`/`TypedStoreRegistry` constructors and `storeKey` generic form against the registry module before running; adjust the construction lines if the public constructors differ (the extension methods themselves are what's under test).

- [ ] **Step 2: Run → fails**: `./gradlew :redux-kotlin-bundle:jvmTest --tests "org.reduxkotlin.bundle.RegistryExtensionsTest"` → FAIL (unresolved `getOrCreateThreadSafeModelStore`).

- [ ] **Step 3: Implement `RegistryExtensions.kt`:**
```kotlin
package org.reduxkotlin.bundle

import org.reduxkotlin.Store
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.registry.StoreKey
import org.reduxkotlin.registry.StoreRegistry
import org.reduxkotlin.registry.TypedStoreRegistry
import org.reduxkotlin.routing.OnWrite
import org.reduxkotlin.routing.RoutingBuilder

/**
 * Returns the routed thread-safe store registered under [id], creating and
 * registering it on first access (lazy, concurrency-safe via the registry).
 */
public fun <K : Any> StoreRegistry<K, ModelState>.getOrCreateThreadSafeModelStore(
    id: K,
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> =
    getOrCreate(id) { createThreadSafeModelStore(enhancer, devChecks, onWrite, block) }

/**
 * [TypedStoreRegistry] variant keyed by a typed [StoreKey]
 * (build one with `storeKey<ModelState>(id)`).
 */
public fun <K : Any> TypedStoreRegistry.getOrCreateThreadSafeModelStore(
    key: StoreKey<K, ModelState>,
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> =
    getOrCreate(key) { createThreadSafeModelStore(enhancer, devChecks, onWrite, block) }
```

- [ ] **Step 4: Run → passes**: `./gradlew :redux-kotlin-bundle:jvmTest --tests "org.reduxkotlin.bundle.RegistryExtensionsTest"` → PASS.

- [ ] **Step 5: Commit**
```bash
git add redux-kotlin-bundle/src
git commit -m "feat(bundle): add registry getOrCreateThreadSafeModelStore extensions"
```

---

### Task 4: Surface-reachable compile test

Proves the single bundle dependency re-exports every member module's public API.

**Files:** `redux-kotlin-bundle/src/commonTest/kotlin/org/reduxkotlin/bundle/SurfaceReachableTest.kt`

- [ ] **Step 1: Write the test** (it only needs to COMPILE; a trivial runtime assert keeps it a real test):
```kotlin
package org.reduxkotlin.bundle

import org.reduxkotlin.Store                                   // core
import org.reduxkotlin.createStore                             // core
import org.reduxkotlin.threadsafe.createThreadSafeStore        // threadsafe
import org.reduxkotlin.granular.subscribeTo                    // granular
import org.reduxkotlin.multimodel.ModelState                   // multimodel
import org.reduxkotlin.multimodel.granular.subscribeToModel    // multimodel-granular
import org.reduxkotlin.registry.StoreRegistry                  // registry
import org.reduxkotlin.routing.createModelStore                // routing
import kotlin.test.Test
import kotlin.test.assertTrue

class SurfaceReachableTest {
    @Test
    fun every_bundled_module_symbol_is_reachable_via_the_bundle() {
        // Referencing each imported symbol proves the api re-export; no need to invoke them.
        val symbols = listOf<Any>(
            ::createStore, ::createThreadSafeStore, ::createModelStore,
            Store::class, ModelState::class, StoreRegistry::class,
            Store<ModelState>::subscribeTo, Store<ModelState>::subscribeToModel,
        )
        assertTrue(symbols.isNotEmpty())
    }
}
```
NOTE: if any unbound `::` reference doesn't compile (overload ambiguity on `subscribeTo`/`subscribeToModel`), replace that entry with a type reference you can resolve (e.g. just keep the `import` and reference the module's simplest public class/typealias). The goal is that all 8 `import` lines resolve — that alone proves re-export.

- [ ] **Step 2: Run → passes** (compiles + trivial assert): `./gradlew :redux-kotlin-bundle:jvmTest --tests "org.reduxkotlin.bundle.SurfaceReachableTest"` → PASS. If an import fails to resolve, the bundle's `api(...)` wiring is wrong — fix `build.gradle.kts` deps.

- [ ] **Step 3: Commit**
```bash
git add redux-kotlin-bundle/src
git commit -m "test(bundle): assert all bundled module APIs are reachable"
```

---

### Task 5: JVM concurrency stress for the factory

**Files:** `redux-kotlin-bundle/src/jvmTest/kotlin/org/reduxkotlin/bundle/concurrency/ThreadSafeModelStoreStressTest.kt`

- [ ] **Step 1: Write the test:**
```kotlin
package org.reduxkotlin.bundle.concurrency

import org.reduxkotlin.bundle.CounterModel
import org.reduxkotlin.bundle.Increment
import org.reduxkotlin.bundle.createThreadSafeModelStore
import org.reduxkotlin.bundle.counterInitial
import org.reduxkotlin.bundle.onIncrement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadSafeModelStoreStressTest {
    @Test
    fun concurrent_dispatch_through_thread_safe_wrapper_does_not_lose_writes() {
        val store = createThreadSafeModelStore {
            model(counterInitial()) { on<Increment> { s, a -> onIncrement(s, a) } }
        }
        val threads = 8
        val perThread = 1000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.submit {
                start.await()
                repeat(perThread) { store.dispatch(Increment(1)) }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()
        assertEquals(threads * perThread, store.state.get<CounterModel>().count)
    }
}
```
(The thread-safe wrapper serializes dispatch — no external lock needed, unlike the routing module's raw-store stress test.)

- [ ] **Step 2: Run → passes**: `./gradlew :redux-kotlin-bundle:jvmTest --tests "org.reduxkotlin.bundle.concurrency.ThreadSafeModelStoreStressTest"` → PASS.

- [ ] **Step 3: Commit**
```bash
git add redux-kotlin-bundle/src
git commit -m "test(bundle): JVM concurrency stress for thread-safe model store"
```

---

### Task 6: Bundle README + API dump + gate

**Files:** `redux-kotlin-bundle/README.md`, generated `redux-kotlin-bundle/api/*`

- [ ] **Step 1: Write `redux-kotlin-bundle/README.md`** documenting: the one-dependency value (lists what's transitively included); a quick-start using `createThreadSafeModelStore { model(initial){ on<A>{} } }`; the registry helpers + `storeKey`; a note that Compose users use `redux-kotlin-bundle-compose` and codegen users add `redux-kotlin-routing-codegen` separately (it's a KSP processor, not bundled).

- [ ] **Step 2: Generate the ABI dump**: `./gradlew :redux-kotlin-bundle:apiDump` (if the module-scoped task is `updateLegacyAbi`, use that). Confirm `redux-kotlin-bundle/api/` lists ONLY the bundle's own public symbols: `createThreadSafeModelStore` + the two `getOrCreateThreadSafeModelStore` extensions (the re-exported `api` deps do NOT appear — they're in their own modules' dumps).

- [ ] **Step 3: Module gate**: `./gradlew :redux-kotlin-bundle:build` → BUILD SUCCESSFUL (compile + tests + detekt explicitApi/KDoc + apiCheck). Fix any missing KDoc on the three public helpers.

- [ ] **Step 4: Commit**
```bash
git add redux-kotlin-bundle/README.md redux-kotlin-bundle/api
git commit -m "docs(bundle): add README and public API baseline"
```

---

### Task 7: redux-kotlin-bundle-compose

**Files:** `redux-kotlin-bundle-compose/build.gradle.kts`, `src/commonTest/.../BundleComposeSurfaceTest.kt`, `README.md`, generated `api/`

- [ ] **Step 1: Create `redux-kotlin-bundle-compose/build.gradle.kts`** (mirror `redux-kotlin-compose-multimodel`, deps = base bundle + compose-multimodel):
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
            namespace = "org.reduxkotlin.bundle.compose"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-bundle"))
                api(project(":redux-kotlin-compose-multimodel"))
            }
        }
    }
}
```

- [ ] **Step 2: Surface test** `src/commonTest/kotlin/org/reduxkotlin/bundle/compose/BundleComposeSurfaceTest.kt`:
```kotlin
package org.reduxkotlin.bundle.compose

import org.reduxkotlin.bundle.createThreadSafeModelStore   // base bundle reachable
import org.reduxkotlin.compose.StableStore                 // compose binding reachable
import kotlin.test.Test
import kotlin.test.assertTrue

class BundleComposeSurfaceTest {
    @Test
    fun base_bundle_and_compose_bindings_are_reachable() {
        val symbols = listOf<Any>(::createThreadSafeModelStore, StableStore::class)
        assertTrue(symbols.isNotEmpty())
    }
}
```
NOTE: confirm `org.reduxkotlin.compose.StableStore` is the actual public symbol in `redux-kotlin-compose` (the design references `StableStore`/`fieldState`/`selectorState`); if the class name differs, import any one public Compose-binding symbol — the point is that a compose-multimodel symbol resolves through `redux-kotlin-bundle-compose`.

- [ ] **Step 3: Run → passes**: `./gradlew :redux-kotlin-bundle-compose:jvmTest --tests "org.reduxkotlin.bundle.compose.BundleComposeSurfaceTest"` → PASS. (If Compose test infra requires Android/desktop wiring, the compile alone proves re-export — keep the test minimal and JVM-only.)

- [ ] **Step 4: README** `redux-kotlin-bundle-compose/README.md`: one dep = base bundle + Compose `State<T>` bindings; use this instead of `redux-kotlin-bundle` for Compose apps; pulls the Compose runtime.

- [ ] **Step 5: API dump + gate**: `./gradlew :redux-kotlin-bundle-compose:apiDump` (expect a near-empty dump — no own public declarations), then `./gradlew :redux-kotlin-bundle-compose:build -x iosSimulatorArm64Test -x iosX64Test -x jsBrowserTest -x wasmJsBrowserTest` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**
```bash
git add settings.gradle.kts redux-kotlin-bundle-compose
git commit -m "feat(bundle): add redux-kotlin-bundle-compose (base + Compose bindings)"
```

---

### Task 8: redux-kotlin-bom + platform publishing convention

**Files:** `build-conventions/src/main/kotlin/convention.publishing-platform.gradle.kts`, `redux-kotlin-bom/build.gradle.kts`

- [ ] **Step 1: Create the publishing convention** `build-conventions/src/main/kotlin/convention.publishing-platform.gradle.kts` (mirrors `convention.publishing` but for a `java-platform` via vanniktech `JavaPlatform`):
```kotlin
import com.vanniktech.maven.publish.JavaPlatform
import util.Git

plugins {
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

val ghOwnerId: String = project.findProperty("gh.owner.id")!!.toString()
val ghOwnerName: String = project.findProperty("gh.owner.name")!!.toString()
val ghOwnerOrganization: String = project.findProperty("gh.owner.organization")!!.toString()
val ghOwnerOrganizationUrl: String = project.findProperty("gh.owner.organization.url")!!.toString()

mavenPublishing {
    configure(JavaPlatform())
    coordinates(project.group.toString(), project.name, project.version.toString())
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    pom {
        name.set(project.name)
        description.set(project.description ?: project.name)
        url.set("https://github.com/$ghOwnerId/${rootProject.name}")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set(ghOwnerId)
                name.set(ghOwnerName)
                organization.set(ghOwnerOrganization)
                organizationUrl.set(ghOwnerOrganizationUrl)
            }
        }
        scm {
            connection.set("scm:git:git@github.com:$ghOwnerId/${rootProject.name.lowercase()}.git")
            url.set("https://github.com/$ghOwnerId/${rootProject.name.lowercase()}")
            tag.set(Git.headCommitHash ?: "HEAD")
        }
    }
}

publishing {
    repositories {
        maven("https://maven.pkg.github.com/$ghOwnerId/${rootProject.name}") {
            name = "GitHub"
            credentials {
                username = System.getenv("GH_USERNAME")
                password = System.getenv("GH_PASSWORD")
            }
        }
    }
}
```
If `import com.vanniktech.maven.publish.JavaPlatform` does NOT resolve against vanniktech 0.36.0, fall back: remove the `mavenPublishing { configure(JavaPlatform()) ... }` block and instead use plain `maven-publish` — `plugins { \`maven-publish\` }` and `publishing { publications { create<MavenPublication>("bom") { from(components["javaPlatform"]); /* pom as above */ } } }`. Report which path you used.

- [ ] **Step 2: Create `redux-kotlin-bom/build.gradle.kts`:**
```kotlin
plugins {
    `java-platform`
    id("convention.publishing-platform")
}

description = "Bill of Materials aligning all redux-kotlin module versions"

dependencies {
    constraints {
        val v = project.version.toString()
        val g = project.group.toString()
        api("$g:redux-kotlin:$v")
        api("$g:redux-kotlin-threadsafe:$v")
        api("$g:redux-kotlin-granular:$v")
        api("$g:redux-kotlin-registry:$v")
        api("$g:redux-kotlin-multimodel:$v")
        api("$g:redux-kotlin-multimodel-granular:$v")
        api("$g:redux-kotlin-compose:$v")
        api("$g:redux-kotlin-compose-multimodel:$v")
        api("$g:redux-kotlin-routing:$v")
        api("$g:redux-kotlin-routing-codegen:$v")
        api("$g:redux-kotlin-bundle:$v")
        api("$g:redux-kotlin-bundle-compose:$v")
    }
}
```
NOTE: `redux-kotlin-routing-codegen` is published only once its JVM publishing convention exists (a separate follow-up); listing it in the BOM is correct (the constraint just declares a version, it doesn't require the artifact to exist at BOM-build time).

- [ ] **Step 3: Verify the BOM builds + publishes to Maven Local** (offline rehearsal, no signing): `./gradlew :redux-kotlin-bom:publishToMavenLocal` → BUILD SUCCESSFUL; confirm a `redux-kotlin-bom-<version>.pom` (packaging `pom`, with `<dependencyManagement>` listing all modules) appears under `~/.m2/repository/<group-path>/redux-kotlin-bom/`. If `build-conventions` fails to compile the new convention (e.g. `JavaPlatform` unresolved), apply the Step 1 fallback.

- [ ] **Step 4: Commit**
```bash
git add settings.gradle.kts build-conventions/src/main/kotlin/convention.publishing-platform.gradle.kts redux-kotlin-bom
git commit -m "feat(bundle): add redux-kotlin-bom platform + publishing convention"
```

---

### Task 9: Whole-repo verification

**Files:** none.

- [ ] **Step 1:** `./gradlew build apiCheck -x iosSimulatorArm64Test -x iosX64Test -x jsBrowserTest -x wasmJsBrowserTest` → BUILD SUCCESSFUL (env-gated iOS-sim/browser test execution excluded; trust CI).
- [ ] **Step 2:** Confirm only intended modules added dumps: `redux-kotlin-bundle` (3 symbols) + `redux-kotlin-bundle-compose` (near-empty). `git status` clean after commits.
- [ ] **Step 3:** `./gradlew :redux-kotlin-bundle:allTests` → BUILD SUCCESSFUL across host-runnable targets (jvm/js/wasmJs/native-for-host).

---

## Self-Review

**Spec coverage:**
- `redux-kotlin-bundle` aggregation (threadsafe+registry+routing+multimodel-granular) → Task 1.
- `createThreadSafeModelStore` (+enhancer/devChecks/onWrite) → Task 2.
- `getOrCreateThreadSafeModelStore` (StoreRegistry + TypedStoreRegistry) → Task 3.
- Surface reachable from one dep → Task 4.
- Thread-safety verified → Task 5 (concurrency stress); factory composition → Task 2.
- ABI dumps own-declarations-only → Tasks 6, 7 (verify steps).
- `redux-kotlin-bundle-compose` (base + compose-multimodel, opt-in Compose) → Task 7.
- `redux-kotlin-bom` (java-platform, all modules, vanniktech JavaPlatform + fallback) → Task 8.
- settings + module conventions + READMEs → Tasks 0/1/6/7/8.
- detekt explicitApi+KDoc on public helpers → KDoc in Tasks 2/3; gate in Task 6.

**Out of scope (correctly absent):** bundling `redux-kotlin-routing-codegen` (KSP processor — listed in the BOM constraints only); a JVM publishing convention for the codegen processor (separate follow-up).

**Placeholder/risk flags:** Task 8 vanniktech `JavaPlatform` is the one external-API uncertainty — front-loaded with an explicit maven-publish fallback and a `publishToMavenLocal` verification. Tasks 3/4/7 carry "confirm the exact symbol/constructor" notes for registry/compose public surface (the extension/factory under test is the real subject; the surrounding construction is adjustable).

**Type-name consistency:** `createThreadSafeModelStore(enhancer, devChecks, onWrite, block)`, `getOrCreateThreadSafeModelStore`, fixtures `CounterModel`/`Increment`/`Reset`/`counterInitial`/`onIncrement`/`onReset`, package `org.reduxkotlin.bundle` — identical across Tasks 2–5.

---

## Execution Handoff
Plan complete. Subagent-driven; Task 8 (BOM publishing) warrants a closer check.
