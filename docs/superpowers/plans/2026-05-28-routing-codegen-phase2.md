# redux-kotlin-routing-codegen (Phase 2, KSP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** A KSP annotation processor that, per Gradle module, generates a `ReduxModule` registrar from `@Reduce`-annotated single-model handler functions, so users annotate functions instead of writing the `createModelStore { model { on<A>{} } }` DSL by hand.

**Architecture:** Annotations (`@Reduce`, `@ReduxInitial`) live in `redux-kotlin-routing` commonMain (SOURCE retention). A JVM KSP processor module `redux-kotlin-routing-codegen` reads them from the *current* module's sources (no cross-module resolution), validates signatures, and emits one `public object <moduleName> : ReduxModule` into `commonMain` metadata via KotlinPoet. The app composes features with `install(<moduleName>)`. A non-published KMP sample module is the all-targets integration guard.

**Tech Stack:** Kotlin 2.3.20, KSP **2.3.9** (KSP2; plain semver — no `<kotlin>-<ksp>` combo), KotlinPoet + kotlinpoet-ksp for generation, `dev.zacsweers.kctfork:ksp` for in-memory processor tests. Builds on `redux-kotlin-routing` (Phase 1, PR #303 — **this branch is stacked on it**).

**Design:** `docs/superpowers/specs/2026-05-28-routing-codegen-phase2-design.md` — read the **"Revisions after multi-agent review (AUTHORITATIVE)"** section; it supersedes earlier prose.

---

## Spike-validated facts (do not re-derive)

- KSP `2.3.9` resolves from Maven Central; KSP2 is default.
- Generating into commonMain works on jvm/macosArm64/wasmJs/js with this wiring (proven):
  ```kotlin
  dependencies { add("kspCommonMainMetadata", project(":redux-kotlin-routing-codegen")) }
  kotlin { sourceSets.named("commonMain") { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") } }
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
      if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
  }
  ```
  Codegen task = `kspCommonMainKotlinMetadata`. Only `kspCommonMainMetadata` is wired.
- `convention.library-mpp-loved` + abiValidation + KSP coexist.

---

## File structure

**New module `redux-kotlin-routing-codegen/`** (JVM processor; not published in v1):
- `build.gradle.kts`
- `src/main/kotlin/org/reduxkotlin/routing/codegen/RoutingSymbolProcessor.kt`
- `src/main/kotlin/org/reduxkotlin/routing/codegen/RoutingSymbolProcessorProvider.kt`
- `src/main/kotlin/org/reduxkotlin/routing/codegen/HandlerModel.kt` (internal data holders + validation)
- `src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
- `src/test/kotlin/org/reduxkotlin/routing/codegen/*Test.kt` + a `KspTestSupport.kt` harness.

**New module `redux-kotlin-routing-codegen-sample/`** (non-published KMP integration guard):
- `build.gradle.kts`, `src/commonMain/kotlin/.../sample/*.kt` (internal `@Reduce`/`@ReduxInitial`), `src/commonTest/kotlin/.../SampleDispatchTest.kt`, `api/` (committed ABI dump).

**Modified:**
- `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/Annotations.kt` (new file) + regenerated `api/`.
- `gradle/libs.versions.toml` (ksp plugin, symbol-processing-api, kotlinpoet, kotlinpoet-ksp, kctfork).
- `settings.gradle.kts` (ksp plugin pin in pluginManagement + 2 new modules).

---

## Conventions (every task)
- **TDD** where logic exists. detekt runs on commit (`detektAll --auto-correct`); NEVER `--no-verify`; re-stage if auto-corrected.
- `explicitApi()` is on for `redux-kotlin-routing` and the processor module → public decls need explicit `public` + KDoc.
- Generated code lives under `build/` → excluded from detekt.
- Branch is `feat/redux-kotlin-routing-codegen` (stacked on the routing branch). Do not switch branches.
- Processor module fast test loop: `./gradlew :redux-kotlin-routing-codegen:test`.

---

### Task 0: Version catalog + KSP plugin pin

**Files:** `gradle/libs.versions.toml`, `settings.gradle.kts`

- [ ] **Step 1: Add versions/libraries/plugins to `gradle/libs.versions.toml`**

Under `[versions]` add:
```toml
ksp = "2.3.9"
kotlinpoet = "2.0.0"
kctfork = "0.12.1"
```
Under `[libraries]` add:
```toml
ksp-symbol-processing-api = { module = "com.google.devtools.ksp:symbol-processing-api", version.ref = "ksp" }
kotlinpoet = { module = "com.squareup:kotlinpoet", version.ref = "kotlinpoet" }
kotlinpoet-ksp = { module = "com.squareup:kotlinpoet-ksp", version.ref = "kotlinpoet" }
kctfork-ksp = { module = "dev.zacsweers.kctfork:ksp", version.ref = "kctfork" }
```
Under `[plugins]` add:
```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Pin the KSP plugin in `settings.gradle.kts` pluginManagement**

In `settings.gradle.kts`, the `pluginManagement { repositories { ... } }` block exists. Add a `plugins` block inside `pluginManagement` so module `plugins { id("com.google.devtools.ksp") }` resolves without a version:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("com.google.devtools.ksp") version "2.3.9"
    }
}
```

- [ ] **Step 3: Verify versions resolve**

Run: `./gradlew help --quiet` (forces settings + catalog evaluation). Expected: no error about unresolved catalog entries. (Artifact downloads happen when first used.)

- [ ] **Step 4: Commit**
```bash
git add gradle/libs.versions.toml settings.gradle.kts
git commit -m "build(codegen): add KSP 2.3.9, KotlinPoet, kctfork to version catalog"
```

---

### Task 1: Annotations in redux-kotlin-routing

**Files:**
- Create: `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/Annotations.kt`
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/AnnotationsTest.kt`

- [ ] **Step 1: Write the failing test**
```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertTrue

class AnnotationsTest {
    @Reduce
    fun sampleReduce(s: UserModel, a: LoggedIn): UserModel = s.copy(user = a.user)

    @ReduxInitial
    fun sampleInitial(): UserModel = UserModel()

    @Test
    fun annotations_are_applicable_to_functions() {
        // Compile-time proof the annotations exist and target functions.
        assertTrue(sampleInitial().user == null)
        assertTrue(sampleReduce(UserModel(), LoggedIn("x")).user == "x")
    }
}
```
(Reuses `UserModel`/`LoggedIn` from the existing `Fixtures.kt` in commonTest.)

- [ ] **Step 2: Run → fails** (unresolved `Reduce`/`ReduxInitial`)
Run: `./gradlew :redux-kotlin-routing:compileTestKotlinJvm`
Expected: FAIL.

- [ ] **Step 3: Implement `Annotations.kt`**
```kotlin
package org.reduxkotlin.routing

/**
 * Marks a top-level single-model reducer handler of shape `(M, A) -> M`,
 * to be collected by the redux-kotlin-routing-codegen KSP processor and
 * emitted as an `on<A>` registration for model `M`. Matching is by the
 * action's exact leaf class.
 *
 * The annotated function must be top-level, take exactly two parameters
 * (the model, then the action), return the model type, and use
 * non-generic, non-nullable, public/internal model and action types.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Reduce

/**
 * Marks a top-level zero-argument provider of a model's initial
 * instance (`() -> M`). Exactly one `@ReduxInitial` per model type must
 * exist in the same module as that model's [Reduce] handlers; the
 * generated registrar calls it to seed the model via `model(provider())`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class ReduxInitial
```

- [ ] **Step 4: Run → passes**
Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.AnnotationsTest"`
Expected: PASS.

- [ ] **Step 5: Regenerate API dump** (annotations are public)
Run: `./gradlew :redux-kotlin-routing:apiDump`
Expected: `redux-kotlin-routing/api/*.api` now list `Reduce`/`ReduxInitial`.

- [ ] **Step 6: Commit**
```bash
git add redux-kotlin-routing/src redux-kotlin-routing/api
git commit -m "feat(routing): add @Reduce and @ReduxInitial codegen annotations"
```

---

### Task 2: Scaffold the processor module + prove kctfork KSP2 harness

This task de-risks the test harness BEFORE writing processor logic: wire the module, a trivial passthrough processor, and one behavioral kctfork test that runs real KSP2. **Resolve the exact kctfork 0.12.1 KSP2 API here** (it is version-specific) so later tasks reuse a proven harness.

**Files:** `redux-kotlin-routing-codegen/build.gradle.kts`, `RoutingSymbolProcessorProvider.kt`, `RoutingSymbolProcessor.kt` (trivial), the `META-INF/services` file, `KspTestSupport.kt`, `SmokeTest.kt`; modify `settings.gradle.kts`.

- [ ] **Step 1: Register module in settings**
In `settings.gradle.kts` `include(...)`, add `":redux-kotlin-routing-codegen",` after `":redux-kotlin-routing",`.

- [ ] **Step 2: Create `redux-kotlin-routing-codegen/build.gradle.kts`**
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("convention.common")
    kotlin("jvm")
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    testImplementation(kotlin("test"))
    testImplementation(libs.kctfork.ksp)
}
```
(`convention.common` supplies repositories + detekt config; it does NOT apply a kotlin plugin, hence explicit `kotlin("jvm")`.)

- [ ] **Step 3: ServiceLoader registration**
Create `redux-kotlin-routing-codegen/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` containing exactly one line:
```
org.reduxkotlin.routing.codegen.RoutingSymbolProcessorProvider
```

- [ ] **Step 4: Trivial provider + processor**

`RoutingSymbolProcessorProvider.kt`:
```kotlin
package org.reduxkotlin.routing.codegen

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/** KSP entry point that creates the redux-kotlin routing processor. */
public class RoutingSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RoutingSymbolProcessor(environment.codeGenerator, environment.logger, environment.options)
}
```
`RoutingSymbolProcessor.kt` (trivial for now — generates nothing, returns empty):
```kotlin
package org.reduxkotlin.routing.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

/** Collects @Reduce/@ReduxInitial functions and generates a ReduxModule registrar. */
public class RoutingSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> = emptyList()
}
```

- [ ] **Step 5: kctfork harness + smoke test**

Create `src/test/kotlin/org/reduxkotlin/routing/codegen/KspTestSupport.kt`. **The kctfork 0.12.1 KSP2 configuration API is version-specific — verify against the resolved jar and adjust.** The intended shape (kctfork 0.12.x exposes `configureKsp(useKsp2 = true) { ... }`):
```kotlin
package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir

/** Compiles [sources] with the routing processor under KSP2 and returns the result. */
fun compileWithProcessor(
    moduleName: String?,
    vararg sources: SourceFile,
): KotlinCompilation.Result {
    val compilation = KotlinCompilation().apply {
        this.sources = sources.toList()
        inheritClassPath = true
        configureKsp(useKsp2 = true) {
            symbolProcessorProviders.add(RoutingSymbolProcessorProvider())
            if (moduleName != null) processorOptions["routing.moduleName"] = moduleName
        }
    }
    return compilation.compile()
}

/** Reads the single generated registrar file's text, or null. */
fun KotlinCompilation.Result.generatedRegistrar(name: String): String? =
    outputDirectory.parentFile.resolve("ksp/sources/kotlin")
        .walkTopDown().firstOrNull { it.name == "$name.kt" }?.readText()
```
Note: `kspProcessorOptions`/`kspSourcesDir`/`configureKsp` names vary across kctfork versions; if `configureKsp` is absent, use the older `compilation.symbolProcessorProviders` + `compilation.kspProcessorOptions` properties and set `compilation.languageVersion`/KSP2 flag as that version documents. The acceptance criterion: the smoke test below runs the processor under KSP2 and the result `exitCode == OK`.

Create `SmokeTest.kt`:
```kotlin
package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun processor_runs_under_ksp2_and_compiles_empty() {
        val src = SourceFile.kotlin("Empty.kt", "package t\nfun noop() {}")
        val result = compileWithProcessor(moduleName = "T", src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
```

- [ ] **Step 6: Run the smoke test, resolving the kctfork API**
Run: `./gradlew :redux-kotlin-routing-codegen:test --tests "org.reduxkotlin.routing.codegen.SmokeTest"`
Expected: PASS. If the kctfork API names differ, fix `KspTestSupport.kt` until this passes. **Do not proceed to Task 3 until the harness compiles a project under real KSP2 and returns `OK`.** If kctfork cannot run KSP2 at all on this setup, report BLOCKED — the fallback is to drive `:redux-kotlin-routing-codegen-sample:kspCommonMainKotlinMetadata` and assert on generated files (Task 8 covers the sample); restructure tests accordingly.

- [ ] **Step 7: Commit**
```bash
git add settings.gradle.kts redux-kotlin-routing-codegen
git commit -m "build(codegen): scaffold processor module + kctfork KSP2 harness"
```

---

### Task 3: Parse + validate @Reduce / @ReduxInitial (errors first)

**Files:** `HandlerModel.kt` (new), `RoutingSymbolProcessor.kt` (extend), `ValidationTest.kt` (new).

- [ ] **Step 1: Write failing validation tests**

`ValidationTest.kt`:
```kotlin
package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun errorResult(body: String, moduleName: String? = "T") =
    compileWithProcessor(moduleName, SourceFile.kotlin("Src.kt", "package t\nimport org.reduxkotlin.routing.*\n$body"))

class ValidationTest {
    @Test fun missing_module_name_errors() {
        val r = errorResult("data class M(val n: Int=0)\ndata class A(val x: Int)\n@ReduxInitial fun mi():M=M()\n@Reduce fun h(s:M,a:A):M=s", moduleName = null)
        assertTrue(r.messages.contains("routing.moduleName"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }
    @Test fun wrong_arity_errors() {
        val r = errorResult("data class M(val n:Int=0)\n@ReduxInitial fun mi():M=M()\n@Reduce fun h(s:M):M=s")
        assertTrue(r.messages.contains("exactly"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }
    @Test fun return_not_model_errors() {
        val r = errorResult("data class M(val n:Int=0)\ndata class A(val x:Int)\n@ReduxInitial fun mi():M=M()\n@Reduce fun h(s:M,a:A):A=a")
        assertTrue(r.messages.contains("return"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }
    @Test fun generic_action_errors() {
        val r = errorResult("data class M(val n:Int=0)\nclass A<T>\n@ReduxInitial fun mi():M=M()\n@Reduce fun h(s:M,a:A<String>):M=s")
        assertTrue(r.messages.contains("generic"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }
    @Test fun missing_initial_errors() {
        val r = errorResult("data class M(val n:Int=0)\ndata class A(val x:Int)\n@Reduce fun h(s:M,a:A):M=s")
        assertTrue(r.messages.contains("ReduxInitial"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }
    @Test fun non_top_level_reduce_errors() {
        val r = errorResult("data class M(val n:Int=0)\ndata class A(val x:Int)\n@ReduxInitial fun mi():M=M()\nobject O { @Reduce fun h(s:M,a:A):M=s }")
        assertTrue(r.messages.contains("top-level"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }
}
```

- [ ] **Step 2: Run → fails** (processor does nothing yet, so no errors emitted → assertions fail).
Run: `./gradlew :redux-kotlin-routing-codegen:test --tests "org.reduxkotlin.routing.codegen.ValidationTest"`
Expected: FAIL.

- [ ] **Step 3: Implement validation + collection in `HandlerModel.kt`**
```kotlin
package org.reduxkotlin.routing.codegen

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

internal data class HandlerInfo(
    val modelFqn: String,
    val modelDecl: KSClassDeclaration,
    val actionDecl: KSClassDeclaration,
    val actionFqn: String,
    val handlerPackage: String,
    val handlerSimpleName: String,
    val handlerFqn: String,
)

internal data class InitialInfo(
    val modelFqn: String,
    val providerPackage: String,
    val providerSimpleName: String,
)

/** Returns true if a type is a usable, source-referenceable, non-generic, non-null class. */
private fun KSType.isUsableConcreteClass(): Boolean {
    if (isMarkedNullable) return false
    if (arguments.isNotEmpty()) return false
    val decl = declaration as? KSClassDeclaration ?: return false
    if (decl.qualifiedName == null) return false
    if (Modifier.INNER in decl.modifiers) return false
    if (decl.classKind == ClassKind.ENUM_ENTRY) return false
    val visible = decl.isPublic() || decl.isInternal()
    return visible
}

/** Validates a @Reduce function; logs an error and returns null if invalid. */
internal fun validateReduce(fn: KSFunctionDeclaration, logger: KSPLogger): HandlerInfo? {
    if (fn.functionKind != FunctionKind.TOP_LEVEL) {
        logger.error("@Reduce must be a top-level function.", fn); return null
    }
    if (fn.parameters.size != 2) {
        logger.error("@Reduce must take exactly (model, action) parameters.", fn); return null
    }
    val modelType = fn.parameters[0].type.resolve()
    val actionType = fn.parameters[1].type.resolve()
    val returnType = fn.returnType?.resolve()
    if (returnType == null || returnType.declaration !== modelType.declaration) {
        logger.error("@Reduce return type must equal the model (first parameter) type.", fn); return null
    }
    if (!modelType.isUsableConcreteClass()) {
        logger.error("@Reduce model type must be a non-generic, non-null, public/internal class.", fn); return null
    }
    if (modelType.arguments.isNotEmpty()) { logger.error("@Reduce model type must not be generic.", fn); return null }
    if (!actionType.isUsableConcreteClass()) {
        logger.error("@Reduce action type must be a non-generic, non-null, public/internal class.", fn); return null
    }
    if (actionType.arguments.isNotEmpty()) { logger.error("@Reduce action type must not be generic.", fn); return null }
    val modelDecl = modelType.declaration as KSClassDeclaration
    val actionDecl = actionType.declaration as KSClassDeclaration
    val pkg = fn.packageName.asString()
    val simple = fn.simpleName.asString()
    return HandlerInfo(
        modelFqn = modelDecl.qualifiedName!!.asString(),
        modelDecl = modelDecl,
        actionDecl = actionDecl,
        actionFqn = actionDecl.qualifiedName!!.asString(),
        handlerPackage = pkg,
        handlerSimpleName = simple,
        handlerFqn = if (pkg.isEmpty()) simple else "$pkg.$simple",
    )
}

/** Validates a @ReduxInitial provider; logs an error and returns null if invalid. */
internal fun validateInitial(fn: KSFunctionDeclaration, logger: KSPLogger): InitialInfo? {
    if (fn.functionKind != FunctionKind.TOP_LEVEL) {
        logger.error("@ReduxInitial must be a top-level function.", fn); return null
    }
    if (fn.parameters.isNotEmpty()) {
        logger.error("@ReduxInitial must take no parameters.", fn); return null
    }
    val returnType = fn.returnType?.resolve()
    if (returnType == null || !returnType.isUsableConcreteClass()) {
        logger.error("@ReduxInitial must return a non-generic, non-null, public/internal model class.", fn); return null
    }
    val modelDecl = returnType.declaration as KSClassDeclaration
    return InitialInfo(
        modelFqn = modelDecl.qualifiedName!!.asString(),
        providerPackage = fn.packageName.asString(),
        providerSimpleName = fn.simpleName.asString(),
    )
}
```

- [ ] **Step 4: Wire validation into `process()`** (still generating nothing; just validate + the moduleName/missing-initial/duplicate-initial checks)
Replace `RoutingSymbolProcessor.process` body:
```kotlin
    private val reduceAnno = "org.reduxkotlin.routing.Reduce"
    private val initialAnno = "org.reduxkotlin.routing.ReduxInitial"

    override fun process(resolver: Resolver): List<com.google.devtools.ksp.symbol.KSAnnotated> {
        val reduceFns = resolver.getSymbolsWithAnnotation(reduceAnno)
            .filterIsInstance<com.google.devtools.ksp.symbol.KSFunctionDeclaration>().toList()
        val initialFns = resolver.getSymbolsWithAnnotation(initialAnno)
            .filterIsInstance<com.google.devtools.ksp.symbol.KSFunctionDeclaration>().toList()
        if (reduceFns.isEmpty() && initialFns.isEmpty()) return emptyList()

        val moduleName = options["routing.moduleName"]
        if (moduleName == null) {
            logger.error(
                "redux-kotlin-routing-codegen: missing KSP arg 'routing.moduleName'. " +
                    "Add ksp { arg(\"routing.moduleName\", \"YourFeature\") } to this module's build.gradle.kts.",
            )
            return emptyList()
        }

        val handlers = reduceFns.mapNotNull { validateReduce(it, logger) }
        val initials = mutableMapOf<String, InitialInfo>()
        for (fn in initialFns) {
            val info = validateInitial(fn, logger) ?: continue
            if (initials.put(info.modelFqn, info) != null) {
                logger.error("Duplicate @ReduxInitial for model ${info.modelFqn}.", fn)
            }
        }
        val byModel = handlers.groupBy { it.modelFqn }
        for ((modelFqn, _) in byModel) {
            if (modelFqn !in initials) {
                logger.error(
                    "No @ReduxInitial found in this module for model $modelFqn. " +
                        "Add a top-level @ReduxInitial provider for it in this module, " +
                        "or register its handlers with the hand-written DSL (cross-module model sharing is not supported in v1).",
                )
            }
        }
        // Generation added in Task 4.
        return emptyList()
    }
```
Add the needed imports (`getSymbolsWithAnnotation`, `isPublic`/`isInternal` come from `com.google.devtools.ksp.*`).

- [ ] **Step 5: Run → passes**
Run: `./gradlew :redux-kotlin-routing-codegen:test --tests "org.reduxkotlin.routing.codegen.ValidationTest"`
Expected: PASS (6 tests). Adjust error-message substrings if your wording differs from the asserted substrings (keep them stable).

- [ ] **Step 6: Commit**
```bash
git add redux-kotlin-routing-codegen/src
git commit -m "feat(codegen): validate @Reduce/@ReduxInitial signatures"
```

---

### Task 4: Generate the registrar (happy path, single model) with KotlinPoet

**Files:** `RegistrarWriter.kt` (new), `RoutingSymbolProcessor.kt` (extend), `GenerationTest.kt` (new).

- [ ] **Step 1: Write the failing behavioral test**
```kotlin
package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerationTest {
    private val feature = SourceFile.kotlin(
        "Feature.kt",
        """
        package feat
        import org.reduxkotlin.routing.Reduce
        import org.reduxkotlin.routing.ReduxInitial
        data class UserModel(val user: String? = null)
        data class LoggedIn(val user: String)
        object LoggedOut
        @ReduxInitial fun userInitial(): UserModel = UserModel()
        @Reduce fun onLoggedIn(s: UserModel, a: LoggedIn): UserModel = s.copy(user = a.user)
        @Reduce fun onLoggedOut(s: UserModel, a: LoggedOut): UserModel = s.copy(user = null)
        """.trimIndent(),
    )

    @Test
    fun generates_a_compiling_redux_module() {
        val result = compileWithProcessor(moduleName = "UserFeature", feature)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val text = result.generatedRegistrar("UserFeature")
        assertTrue(text != null, "registrar not generated")
        assertTrue(text!!.contains("object UserFeature"))
        assertTrue(text.contains("ReduxModule"))
        assertTrue(text.contains("on<feat.LoggedIn>") || text.contains("on<LoggedIn>"))
    }
}
```
(Behavioral: the whole compilation — generated file included — must reach `OK`, proving the generated code is valid Kotlin against the routing runtime on the classpath via `inheritClassPath`.)

NOTE: for `compileWithProcessor` to see `org.reduxkotlin.routing.*` (ReduxModule, RoutingBuilder, model/on, Reduce/ReduxInitial), the processor test module must have the routing runtime on its classpath. Add to `redux-kotlin-routing-codegen/build.gradle.kts` dependencies: `testImplementation(project(":redux-kotlin-routing"))` and `testImplementation(project(":redux-kotlin-multimodel"))`. `inheritClassPath = true` then exposes them to kctfork.

- [ ] **Step 2: Run → fails** (no generation yet).

- [ ] **Step 3: Implement `RegistrarWriter.kt`** using KotlinPoet + kotlinpoet-ksp
```kotlin
package org.reduxkotlin.routing.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

private val REDUX_MODULE = ClassName("org.reduxkotlin.routing", "ReduxModule")
private val ROUTING_BUILDER = ClassName("org.reduxkotlin.routing", "RoutingBuilder")

/** Renders the registrar object and writes it to the generated common sources. */
internal fun writeRegistrar(
    codeGenerator: CodeGenerator,
    moduleName: String,
    generatedPackage: String,
    handlersByModel: Map<String, List<HandlerInfo>>,
    initials: Map<String, InitialInfo>,
    originatingFiles: List<KSFile>,
) {
    val contribute = FunSpec.builder("contribute")
        .addModifiers(KModifier.OVERRIDE)
        .receiver(ROUTING_BUILDER)

    // Deterministic order: models by FQN, handlers by (action FQN, handler FQN).
    for (modelFqn in handlersByModel.keys.sorted()) {
        val handlers = handlersByModel.getValue(modelFqn)
            .sortedWith(compareBy({ it.actionFqn }, { it.handlerFqn }))
        val initial = initials.getValue(modelFqn)
        val modelClass = handlers.first().modelDecl.toClassName()
        val provider = MemberName(initial.providerPackage, initial.providerSimpleName)
        contribute.beginControlFlow("model<%T>(%M())", modelClass, provider)
        for (h in handlers) {
            val action = h.actionDecl.toClassName()
            val handler = MemberName(h.handlerPackage, h.handlerSimpleName)
            contribute.addStatement("on<%T> { s, a -> %M(s, a) }", action, handler)
        }
        contribute.endControlFlow()
    }

    val registrar = TypeSpec.objectBuilder(moduleName)
        .addKdoc(
            "Generated by redux-kotlin-routing-codegen from @Reduce handlers.\n" +
                "Install with `install(%L)`. Configure the name via the `routing.moduleName` KSP arg.",
            moduleName,
        )
        .addSuperinterface(REDUX_MODULE)
        .addFunction(contribute.build())
        .build()

    val file = FileSpec.builder(generatedPackage, moduleName)
        .addType(registrar)
        .build()

    file.writeTo(codeGenerator, Dependencies(aggregating = true, *originatingFiles.toTypedArray()))
}
```

- [ ] **Step 4: Call it from `process()`** — replace the `// Generation added in Task 4.` comment with:
```kotlin
        // Only generate if validation produced no fatal gaps.
        val anyMissingInitial = byModel.keys.any { it !in initials }
        if (handlers.isEmpty() || anyMissingInitial) return emptyList()

        val generatedPackage = options["routing.generatedPackage"] ?: "org.reduxkotlin.routing.generated"
        val originating = (reduceFns + initialFns).mapNotNull { it.containingFile }.distinct()
        writeRegistrar(codeGenerator, moduleName, generatedPackage, byModel, initials, originating)
        logger.info(
            "redux-kotlin-routing-codegen: generated $generatedPackage.$moduleName " +
                "(install with install($moduleName))",
        )
```

- [ ] **Step 5: Run → passes**
Run: `./gradlew :redux-kotlin-routing-codegen:test --tests "org.reduxkotlin.routing.codegen.GenerationTest"`
Expected: PASS. If the generated code fails to compile, read the kctfork `result.messages` — most likely an import/type-rendering issue; fix `RegistrarWriter`.

- [ ] **Step 6: Commit**
```bash
git add redux-kotlin-routing-codegen/src redux-kotlin-routing-codegen/build.gradle.kts
git commit -m "feat(codegen): generate ReduxModule registrar via KotlinPoet"
```

---

### Task 5: Multi-model, name-clash imports, determinism

**Files:** `GenerationTest.kt` (extend), `DeterminismTest.kt` (new).

- [ ] **Step 1: Add tests**

Append to `GenerationTest.kt`:
```kotlin
    @Test
    fun two_handlers_same_simple_name_different_packages_compile() {
        val a = SourceFile.kotlin("A.kt", """
            package a
            import org.reduxkotlin.routing.*
            data class MA(val n: Int = 0)
            data class Act(val x: Int)
            @ReduxInitial fun mi(): MA = MA()
            @Reduce fun onAct(s: MA, a: Act): MA = s.copy(n = a.x)
        """.trimIndent())
        val b = SourceFile.kotlin("B.kt", """
            package b
            import org.reduxkotlin.routing.*
            data class MB(val n: Int = 0)
            data class Act(val x: Int)
            @ReduxInitial fun mi(): MB = MB()
            @Reduce fun onAct(s: MB, a: Act): MB = s.copy(n = a.x)
        """.trimIndent())
        val result = compileWithProcessor(moduleName = "Multi", a, b)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
```
(Two `onAct` in packages `a`/`b` and two `Act` classes force KotlinPoet import-aliasing; `OK` proves it works.)

Create `DeterminismTest.kt`:
```kotlin
package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DeterminismTest {
    private fun src() = SourceFile.kotlin("D.kt", """
        package d
        import org.reduxkotlin.routing.*
        data class M1(val n: Int = 0)
        data class M2(val n: Int = 0)
        data class AX(val x: Int); data class AY(val x: Int)
        @ReduxInitial fun m1i(): M1 = M1()
        @ReduxInitial fun m2i(): M2 = M2()
        @Reduce fun onY(s: M2, a: AY): M2 = s
        @Reduce fun onX(s: M1, a: AX): M1 = s
    """.trimIndent())

    @Test
    fun generated_output_is_byte_identical_across_runs() {
        val first = compileWithProcessor("Det", src()).generatedRegistrar("Det")
        val second = compileWithProcessor("Det", src()).generatedRegistrar("Det")
        assertNotNull(first); assertEquals(first, second)
    }
}
```

- [ ] **Step 2: Run → expect PASS** (KotlinPoet handles import aliasing; sorting is already implemented in Task 4). If the clash test fails with a duplicate-import/compile error, ensure `RegistrarWriter` uses `MemberName`/`ClassName` (KotlinPoet auto-aliases) and not raw strings. If determinism fails, confirm the sort comparators in `writeRegistrar` are total (model FQN, then action FQN, then handler FQN).
Run: `./gradlew :redux-kotlin-routing-codegen:test`
Expected: all green.

- [ ] **Step 3: Commit**
```bash
git add redux-kotlin-routing-codegen/src
git commit -m "test(codegen): cover name-clash imports and deterministic output"
```

---

### Task 6: Scaffold the sample integration module (all-targets guard)

**Files:** `redux-kotlin-routing-codegen-sample/build.gradle.kts`, sample sources, `SampleDispatchTest.kt`; modify `settings.gradle.kts`.

- [ ] **Step 1: Register module** — add `":redux-kotlin-routing-codegen-sample",` to `settings.gradle.kts` `include(...)` after `":redux-kotlin-routing-codegen",`.

- [ ] **Step 2: `redux-kotlin-routing-codegen-sample/build.gradle.kts`**
```kotlin
plugins {
    id("convention.library-mpp-loved")
    id("com.google.devtools.ksp")
}

dependencies {
    add("kspCommonMainMetadata", project(":redux-kotlin-routing-codegen"))
}

ksp {
    arg("routing.moduleName", "SampleModule")
    arg("routing.generatedPackage", "org.reduxkotlin.routing.sample.generated")
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                api(project(":redux-kotlin-routing"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
```
(Do NOT apply `convention.publishing-mpp` — sample is unpublished. The hard-coded KSP args make the generated FQN stable for the ABI dump.)

- [ ] **Step 3: Sample sources** (internal — avoids explicitApi+KDoc burden)
`src/commonMain/kotlin/org/reduxkotlin/routing/sample/Counter.kt`:
```kotlin
package org.reduxkotlin.routing.sample

import org.reduxkotlin.routing.Reduce
import org.reduxkotlin.routing.ReduxInitial

internal data class CounterModel(val count: Int = 0)

internal data class Increment(val by: Int)
internal object Reset

@ReduxInitial internal fun counterInitial(): CounterModel = CounterModel()

@Reduce internal fun onIncrement(s: CounterModel, a: Increment): CounterModel = s.copy(count = s.count + a.by)

@Reduce internal fun onReset(s: CounterModel, a: Reset): CounterModel = CounterModel()
```

- [ ] **Step 4: Integration test** `src/commonTest/kotlin/org/reduxkotlin/routing/sample/SampleDispatchTest.kt`
```kotlin
package org.reduxkotlin.routing.sample

import org.reduxkotlin.routing.createModelStore
import org.reduxkotlin.routing.get
import org.reduxkotlin.routing.install
import org.reduxkotlin.routing.sample.generated.SampleModule
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleDispatchTest {
    @Test
    fun generated_module_dispatches() {
        val store = createModelStore { install(SampleModule) }
        store.dispatch(Increment(3))
        store.dispatch(Increment(4))
        assertEquals(7, store.state.get<CounterModel>().count)
        store.dispatch(Reset)
        assertEquals(0, store.state.get<CounterModel>().count)
    }
}
```

- [ ] **Step 5: Generate + run on JVM**
Run: `./gradlew :redux-kotlin-routing-codegen-sample:kspCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL; file `build/generated/ksp/metadata/commonMain/kotlin/org/reduxkotlin/routing/sample/generated/SampleModule.kt` exists.
Run: `./gradlew :redux-kotlin-routing-codegen-sample:jvmTest`
Expected: PASS (1 test).

- [ ] **Step 6: Generate the sample ABI dump** (abiValidation is on via the convention)
Run: `./gradlew :redux-kotlin-routing-codegen-sample:apiDump`
Expected: creates `redux-kotlin-routing-codegen-sample/api/*.api` listing `org.reduxkotlin.routing.sample.generated.SampleModule`.

- [ ] **Step 7: Commit**
```bash
git add settings.gradle.kts redux-kotlin-routing-codegen-sample
git commit -m "test(codegen): add KMP sample integration module + ABI dump"
```

---

### Task 7: Cross-target compile + config-cache verification

**Files:** none (verification only).

- [ ] **Step 1: Compile generated common code on every host-runnable target**
Run: `./gradlew :redux-kotlin-routing-codegen-sample:compileKotlinJvm :redux-kotlin-routing-codegen-sample:compileKotlinMacosArm64 :redux-kotlin-routing-codegen-sample:compileKotlinWasmJs :redux-kotlin-routing-codegen-sample:compileKotlinJs`
Expected: BUILD SUCCESSFUL (proves the generated registrar is valid Kotlin on JVM, native, wasm, JS backends).

- [ ] **Step 2: Execute dispatch on a native backend** (host-gated; macOS host)
Run: `./gradlew :redux-kotlin-routing-codegen-sample:macosArm64Test`
Expected: PASS — exercises KClass-keyed dispatch of generated code on a native backend, not just JVM. (If the host can't run it, note it for CI.)

- [ ] **Step 3: Configuration-cache gate**
Run: `./gradlew :redux-kotlin-routing-codegen-sample:compileKotlinMetadata --configuration-cache` then run the SAME command again.
Expected: first run "Configuration cache entry stored"; second run "Configuration cache entry reused"; both BUILD SUCCESSFUL. If CC reports problems with the `kotlin.srcDir(build/generated/ksp/...)`, fix by registering the dir defensively (create it at configuration time) and re-run; document the fix.

- [ ] **Step 4: Commit** (only if Step 3 required a build-file fix)
```bash
git add redux-kotlin-routing-codegen-sample/build.gradle.kts
git commit -m "build(codegen): make sample KSP wiring configuration-cache safe"
```

---

### Task 8: README + full gate

**Files:** `redux-kotlin-routing-codegen/README.md`.

- [ ] **Step 1: Write the README** documenting: what it does; the required `build.gradle.kts` wiring (the spike-validated snippet); the required `routing.moduleName` arg; the `@Reduce`/`@ReduxInitial` rules (top-level, `(M,A)->M`, non-generic/non-null types, model+initial in same module); `install(<moduleName>)`; the **generated + hand-DSL ordering rule** ("`install(...)` registers handlers at that point in the `createModelStore` sequence; a hand-DSL handler for the same action before/after the install runs before/after the generated ones — install generated modules first if unsure"); v1 limitations (single-model only; no multi-model/broadcast/platform-source-set codegen; cross-module shared models use the hand DSL); and that the processor is **not yet published** (consume via local project dependency for now).

- [ ] **Step 2: Full-module gates**
Run: `./gradlew :redux-kotlin-routing-codegen:build` → expect BUILD SUCCESSFUL (compile + tests + detekt explicitApi/KDoc).
Run: `./gradlew :redux-kotlin-routing-codegen-sample:build -x iosSimulatorArm64Test -x iosX64Test -x jsBrowserTest -x wasmJsBrowserTest` → expect BUILD SUCCESSFUL (includes the sample's `checkKotlinAbi` against the committed dump).
Run: `./gradlew :redux-kotlin-routing:build` → expect BUILD SUCCESSFUL (annotations + dump).
Run: `./gradlew detektAll` → expect BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add redux-kotlin-routing-codegen/README.md
git commit -m "docs(codegen): add README"
```

---

### Task 9: Whole-repo verification

**Files:** none.

- [ ] **Step 1:** `./gradlew build apiCheck -x iosSimulatorArm64Test -x iosX64Test -x jsBrowserTest -x wasmJsBrowserTest` → BUILD SUCCESSFUL (only env-gated iOS-sim/browser test execution excluded; trust CI for those, per CLAUDE.md).
- [ ] **Step 2:** Confirm only the intended modules changed dumps: `redux-kotlin-routing` (annotations) + `redux-kotlin-routing-codegen-sample` (new). `git status` clean after commits.

---

## Self-Review

**Spec coverage (design "Revisions" obligations → tasks):**
- KotlinPoet codegen → Task 4 (`RegistrarWriter`).
- Validate-and-reject unsupported shapes → Task 3 (`isUsableConcreteClass`, validateReduce) + tests (arity/return/generic/missing-initial/non-top-level).
- Declaration-identity validation (not KSType.equals) → Task 3 (`declaration !== modelType.declaration`).
- Group by qualifiedName → Task 3 (`groupBy { it.modelFqn }`).
- functionKind == TOP_LEVEL → Task 3.
- Dependencies(aggregating=true) → Task 4 (`writeRegistrar`).
- Explicit `model<M>(provider())` → Task 4 (`beginControlFlow("model<%T>(%M())", ...)`).
- Deterministic order → Task 4 (sorts) + Task 5 (determinism test).
- Required moduleName → Task 3 (missing-name error + test).
- Same-module model+initial; clear error → Task 3 (missing-initial error + test).
- Discoverability (logger.info + KDoc header) → Task 4.
- Generated+hand-DSL ordering doc → Task 8 README.
- Processor: convention.common + jvm + explicitApi, non-published → Task 2.
- Sample: mpp-loved + KSP + committed dump + hard-coded args + internal sources → Task 6.
- Version catalog (ksp/kotlinpoet/kctfork) → Task 0.
- kctfork 0.12.1 behavioral tests → Tasks 2–5; sample = prod-engine guard → Tasks 6–7.
- Native execution (macosArm64Test) + config-cache gate → Task 7.
- Annotations + apiDump → Task 1.

**Out of scope (correctly absent):** multi-model `onAction`/`onBroadcast` codegen, platform-source-set handlers, cross-module shared-model (constructor-param-initial), processor publishing convention — all noted as follow-ups in the design.

**Placeholder/risk flags:** Task 2 Step 5/6 explicitly calls out that the **kctfork 0.12.1 KSP2 configuration API must be verified against the resolved jar** (version-specific) and gives a fallback (sample-driven file assertions) if kctfork can't run KSP2 — this is the single highest-uncertainty external API in the plan and is front-loaded to de-risk before processor logic is built.

**Type-name consistency:** `RoutingSymbolProcessor(codeGenerator, logger, options)`, `RoutingSymbolProcessorProvider`, `HandlerInfo`/`InitialInfo`, `validateReduce`/`validateInitial`/`isUsableConcreteClass`, `writeRegistrar`, `compileWithProcessor`/`generatedRegistrar`, KSP args `routing.moduleName`/`routing.generatedPackage` — used identically across tasks.

---

## Execution Handoff
Plan complete. Subagent-driven execution with per-task review; Task 2 (harness) and Task 4 (generation) warrant a closer review pass.
