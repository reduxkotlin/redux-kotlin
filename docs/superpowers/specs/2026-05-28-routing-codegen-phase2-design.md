# Design: redux-kotlin-routing-codegen (Phase 2, KSP)

- **Status:** Draft (design + spike complete; pre-implementation)
- **Date:** 2026-05-28
- **Depends on:** Phase 1 `redux-kotlin-routing` (PR #303, not yet on `master`). This module + its PR are **stacked on** that branch.
- **Parent RFC:** `docs/superpowers/specs/2026-05-28-reducer-middleware-routing-design.md` §6.3 (Mechanism B).

## What this delivers

A KSP annotation processor that, run **per Gradle module**, generates a `ReduxModule` registrar from `@Reduce`-annotated handler functions in that module. The app composes features with `install(GeneratedReduxModule)` — the same runtime contract Phase 1 ships, so the generated code is exactly what a user would hand-write with the DSL. Codegen is the **convenience layer**; the DSL stays the escape hatch and the test oracle.

Goal served (per RFC, re-weighted): **ergonomics** (annotate a function, skip the builder boilerplate) and a path toward **lazy registration**. Codegen does *not* change routing semantics or perf.

## Spike findings that anchor this design (validated, branch `spike/ksp-kmp`)

- **KSP `2.3.9`** is the version for Kotlin 2.3.20. The old `<kotlin>-<ksp>` combo-version scheme is gone as of KSP 2.3.0 — it is plain semver now; pin `2.3.9` (its POM depends on `kotlin-stdlib:2.3.20`). KSP2 is the default engine; no opt-in flag.
- **Generating into `commonMain` works on every target** (jvm, macosArm64, wasmJs, js all compiled the generated common code). The required wiring (proven):
  ```kotlin
  plugins {
      id("convention.library-mpp-loved")
      id("com.google.devtools.ksp")
  }
  dependencies {
      add("kspCommonMainMetadata", project(":redux-kotlin-routing-codegen"))
  }
  kotlin {
      sourceSets.named("commonMain") {
          kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
      }
  }
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
      if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
  }
  ```
  Codegen task = **`kspCommonMainKotlinMetadata`**. Only `kspCommonMainMetadata` is wired (NOT per-platform `kspJvm`/`kspJs`) — code is generated once, shared by all targets.
- **`convention.library-mpp-loved` + abiValidation + KSP coexist.** No plainer module needed for the consumer.
- **Per-module independence holds by construction:** `getSymbolsWithAnnotation` only sees the module's own sources, so the RFC's "cross-module resolution on klibs" risk is **moot** — no classpath enumeration, no ServiceLoader. The app references each generated registrar **by name**.
- **Gotchas to handle:** (1) a `public` generated registrar appears in the consuming module's ABI dump — but only as ONE stable symbol (`object X : ReduxModule`), its body isn't ABI, so it does **not** churn per-handler; regenerate the dump once. (2) The `dependsOn("kspCommonMainKotlinMetadata")` is mandatory (else compile races codegen). (3) A plain `kotlin("jvm")` processor module needs its own `repositories {}` (convention.common only applies to library modules). (4) IDE may not index generated sources until first Gradle sync.

## Public API (annotations — added to `redux-kotlin-routing` commonMain)

```kotlin
/** Marks a top-level single-model reducer handler: (M, A) -> M. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Reduce

/** Marks a top-level provider of a model's initial instance: () -> M. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class ReduxInitial
```
`SOURCE` retention — they exist only for codegen, leaving no runtime footprint (and no `.class`/klib metadata, so they don't perturb runtime ABI beyond the two annotation declarations themselves).

### Usage

```kotlin
// user feature module, commonMain
@ReduxInitial fun userInitial(): UserModel = UserModel()

@Reduce fun onLoggedIn(s: UserModel, a: LoggedIn): UserModel = s.copy(user = a.user)
@Reduce fun onLoggedOut(s: UserModel, a: LoggedOut): UserModel = s.copy(user = null)
```
KSP generates (into `commonMain` metadata):
```kotlin
public object GeneratedReduxModule : ReduxModule {
    override fun RoutingBuilder.contribute() {
        model(userInitial()) {
            on<LoggedIn> { s, a -> onLoggedIn(s, a) }
            on<LoggedOut> { s, a -> onLoggedOut(s, a) }
        }
    }
}
```
App:
```kotlin
val store = createModelStore { install(GeneratedReduxModule) }
```

## Scope (v1)

**In:** single-model `@Reduce` handlers `(M, A) -> M` + `@ReduxInitial` providers `() -> M`, grouped by model type, emitted as one `ReduxModule` per module.

**Out (deferred; hand-DSL still works alongside generated modules):** multi-model `onAction` codegen, `onBroadcast` codegen, handlers in platform source sets (only `commonMain` is processed). These are explicitly listed as fast-follows. Rationale: single-model is the dominant case the RFC emphasizes; keeping v1 to it bounds processor + test complexity and the all-targets validation surface.

## Decisions (open to review)

1. **Initial via `@ReduxInitial` provider** (vs requiring a no-arg constructor, vs app-supplies-all-initials). The provider keeps a feature self-contained and imposes no constructor constraint. One `@ReduxInitial` per model type per module is required; a model with `@Reduce` handlers but no matching `@ReduxInitial` is a **compile error** (KSP `logger.error`).
2. **Generated registrar name/package via KSP args.** `ksp { arg("routing.moduleName", "UserFeature"); arg("routing.generatedPackage", "com.app.user.routing") }`. Defaults: name `GeneratedReduxModule`, package `org.reduxkotlin.routing.generated`. (A fixed default package means two codegen-using modules on the classpath would collide only if both keep the default *and* are installed together — documented; real multi-feature apps set the arg.)
3. **String-template codegen, not KotlinPoet** (v1). Avoids a dependency; generation is mechanical and FQN-qualified. Reviewers: challenge if escaping/import edge cases (generic actions, nested classes) make KotlinPoet worth the dep.
4. **Validation as KSP errors** (not silent skips): `@Reduce` must be a top-level function with exactly `(M, A)` params and return type `== M`; `@ReduxInitial` must be top-level, zero-param, returning the model type; duplicate `@ReduxInitial` for one type is an error. Each bad symbol gets a precise `logger.error(node)`.
5. **Handlers must live in `commonMain`** (v1) — only `kspCommonMainMetadata` is wired. A `@Reduce` in a platform source set is not processed; document, and consider a future per-platform pass.

## Module structure

- **`redux-kotlin-routing-codegen`** — the processor. A `kotlin("jvm")` module (KSP processors are JVM artifacts), NOT MPP, NOT published-as-MPP (it's a build-time tool; publish as a plain JVM artifact via the existing publishing convention or a JVM variant). Deps: `com.google.devtools.ksp:symbol-processing-api:2.3.9`. Own `repositories {}` block. Contains `RoutingSymbolProcessor` + `RoutingSymbolProcessorProvider` (registered via `resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`).
- **`redux-kotlin-routing`** (modify) — add `@Reduce` + `@ReduxInitial` to commonMain; regenerate its ABI dump.
- **Processor unit tests** — JVM, via a KSP-compile-testing library (e.g. `dev.zacsweers.kctfork` with KSP support, version compatible with KSP 2.3.9 / Kotlin 2.3.20 — pin in the plan). Compile annotated sources in-memory, assert generated source text + that error cases fail compilation with the expected message.
- **`redux-kotlin-routing-codegen-sample`** — a non-published KMP module applying `convention.library-mpp-loved` + KSP + the wiring above, with real `@Reduce`/`@ReduxInitial` sources and a `commonTest` that `install(GeneratedReduxModule)`s and asserts dispatch works. This is the **all-targets integration guard** (makes the spike permanent). Exclude from publishing. Add to `settings.gradle.kts`.

## Build-system DX

The three-part wiring is error-prone. v1 ships it as a **documented snippet** (README + the sample module as a copyable reference). A dedicated Gradle plugin that applies the wiring automatically is noted as a future improvement (out of scope; Buck/non-Gradle users wire KSP themselves regardless).

## Testing strategy

1. **Processor unit tests (JVM, fast):** golden-output tests for the happy path (one model, multiple actions, multiple models) and error tests (wrong arity, return-type mismatch, missing `@ReduxInitial`, duplicate initial, non-top-level `@Reduce`). Assert both generated text and `KotlinCompilation.Result.exitCode`/messages.
2. **Integration (all targets):** the sample module compiles generated common code on every host-runnable target and runs a `commonTest` proving the generated `ReduxModule` dispatches correctly through a real `createModelStore`.
3. **Gates:** detekt `explicitApi` + KDoc on the two new public annotations and any public processor API; `apiDump` for `redux-kotlin-routing` (annotations) and the codegen module; the sample module is not published and need not carry an ABI dump.

## Out of scope (carried forward)

KSP cross-module aggregation/discovery (unnecessary — per-module generation), stable Int/string action IDs + serialization (Phase-2 deferral per RFC), compiler plugin (Phase 3), coroutine effects (separate module).

## Revisions after multi-agent review (AUTHORITATIVE — supersede the sections above where they conflict)

A 4-lens review (KSP correctness, KMP build, API/ergonomics, testing) surfaced two build blockers and several processor-correctness majors. Resolutions:

### Codegen mechanics
- **Use KotlinPoet** (`com.squareup:kotlinpoet`) for generation, not string templates. It renders nested/qualified type names correctly and manages imports/aliasing for top-level handler references that share a simple name (Kotlin has no FQ call syntax for top-level functions — they MUST be imported). Build-time-only JVM dep; the "avoid a dependency" rationale is dropped.
- **Validate-and-reject unsupported action/model shapes** (they are illegal as `reified` type args or unreferenceable). For each `@Reduce`, `logger.error(node)` and skip if the action type `A`: has type arguments (generic), is nullable, is an `inner` class, or its `declaration.qualifiedName` is null / not `public`/`internal`-visible to the generated file. Same null-qualifiedName guard for `M`.
- **Validate via declaration identity, never `KSType.equals`:** assert `param0.type.resolve().declaration === returnType.resolve().declaration`, param count `== 2`, both non-nullable, no type args. `functionKind == FunctionKind.TOP_LEVEL` for both `@Reduce` and `@ReduxInitial` (reject MEMBER/LOCAL/etc.).
- **Group handlers by `param0` model `declaration.qualifiedName` (String key)**; pair `@ReduxInitial` by its return-type `declaration.qualifiedName`; assert provider-return decl === handler-param0 decl per group.
- **Emit explicit type args:** `model<com.app.UserModel>(userInitial()) { on<com.app.LoggedIn> { s, a -> onLoggedIn(s, a) } }` — pin `M` (don't rely on inference from the provider's declared return type) and render `A` via KotlinPoet.
- **Deterministic emission order (load-bearing):** sort model groups by model FQN; within a group sort handlers by (action FQN, then handler-function FQN). Registration order fixes dispatch order (RoutingDsl docstring), so nondeterministic order would change runtime behavior between builds — not merely flake goldens. Add a "run processor twice → byte-identical output" test.
- **Incremental correctness:** filter symbols by `validate()`; emit the single registrar with `Dependencies(aggregating = true, sources = <every containing KSFile>)`; single round (return empty deferred).
- **Generated file:** KotlinPoet `FileSpec` with a KDoc header naming the `routing.moduleName` arg + the `install(...)` snippet. (Generated code lives under `build/` → excluded from detekt, so its KDoc is for humans, not the gate.)

### API / ergonomics
- **`routing.moduleName` is REQUIRED** (no default). If a module has `@Reduce` symbols but no `moduleName` arg → `logger.error` naming the exact gradle snippet. Eliminates the default-FQN collision when two codegen-using modules land on one classpath. Default package stays `org.reduxkotlin.routing.generated`; the unique module name keeps the FQN unique.
- **v1 constraint — model + its `@ReduxInitial` must be in the SAME module.** A `@Reduce` whose model has no in-module `@ReduxInitial` is a `logger.error` whose message states *why* (initial not found in this module) and the two remedies (add `@ReduxInitial` here, or use the hand DSL for cross-module-shared models). **Top fast-follow:** generate `class <Name>(initialX: X, …) : ReduxModule` taking app-supplied initials for models whose `@ReduxInitial` is absent — resolves the shared-model case with one mechanism. Out of v1 scope.
- **Discoverability:** `logger.info` on a clean build printing the generated FQN + `install(...)` line, plus the generated KDoc header. Document the **generated + hand-DSL ordering rule** in the README (install position in the `createModelStore` block sets dispatch order vs hand-written handlers for the same action) and recommend "install generated modules first."

### Build & modules
- **Processor module** applies `convention.common` (→ repositories + detekt config; detekt scans it regardless of plugins) + `kotlin("jvm")` + `explicitApi()`; **NOT published in v1** (the only publishing convention is MPP-only; no JVM publishing convention exists). Wired into the sample/tests via `project(":redux-kotlin-routing-codegen")`. **Publishing (a new `convention.publishing-jvm`) is a required pre-release follow-up**, called out in the PR — without it external users can't consume the processor. Pin `jvmTarget = JVM_17`. ServiceLoader file: `src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`.
- **Sample module** applies `convention.library-mpp-loved` (all targets) + KSP. Because that convention force-enables `abiValidation`, the sample **carries a committed ABI dump** with **hard-coded `routing.moduleName`/`routing.generatedPackage`** — this turns the "one stable public symbol, body-not-ABI" claim into an enforced guard. Sample is excluded from publishing (do not apply `convention.publishing-mpp`). Sample `@Reduce`/`@ReduxInitial` handler sources are **`internal`** (processor sees same-module `internal` fine; avoids the explicitApi+KDoc burden and keeps them out of the sample's ABI). Dispatch assertions live in `commonTest` (KDoc-exempt).
- **Version catalog / settings:** add `ksp = "2.3.9"` + `[plugins] ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }`; `symbol-processing-api` + `kotlinpoet` + `kctfork-ksp` `[libraries]`. Pin the KSP plugin in `pluginManagement` or reference via `alias(libs.plugins.ksp)`. Add `:redux-kotlin-routing-codegen` and `:redux-kotlin-routing-codegen-sample` to `settings.gradle.kts`.

### Testing
- **Processor unit tests:** `dev.zacsweers.kctfork:ksp:0.12.1` (runs real **KSP2**; bundles Kotlin 2.3.0 / KSP 2.3.5 — minor engine skew vs prod 2.3.20/2.3.9; the `SymbolProcessorProvider`/`Resolver`/`CodeGenerator`/`KSPLogger` surface is stable across that gap; the **sample module certifies the production engine**). Assertions are **behavioral**: `result.exitCode == OK` + reflectively load the generated object and assert `it is ReduxModule`. Keep ≤2 whitespace-normalized golden snapshots for shape documentation only. Error cases: assert `exitCode != OK` + a **stable substring** of the diagnostic (symbol name + rule), and ensure the bad input has no other valid output so exit is reliably non-OK. Cover: happy (multi-action, multi-model), wrong arity, return≠M, nullable model/action, generic action, nested action (other package → import correctness), missing `@ReduxInitial`, duplicate `@ReduxInitial`, non-top-level `@Reduce`, two handlers with identical simple name in different packages, determinism (twice → identical).
- **Sample integration:** `commonTest` `install(<Name>)`s the generated module and asserts dispatch. **Execution** runs on `jvm` + `jsNode` (+ host-gated `macosArm64Test`/`linuxX64Test` so KClass-keyed dispatch is exercised on a native backend, not only JVM); native/wasm/ios-sim otherwise **compile-only** — state this precisely (do not claim "executes on all targets").
- **Config-cache gate:** CI/verification runs `./gradlew :redux-kotlin-routing-codegen-sample:compileKotlinMetadata --configuration-cache` twice (the spike did not test CC). If the manual `kotlin.srcDir` into `build/generated/ksp/...` trips CC, fall back to the KSP plugin's own source registration or `mkdirs` at configuration time.

### Confirmed non-issues (do not re-litigate)
- **SOURCE retention is safe** — KSP reads same-module source AST; retention only matters for cross-module classpath discovery, which we do not perform. The two annotation *type declarations* are public and land in `redux-kotlin-routing`'s dump (one-time `apiDump`).
- **Per-module independence** — no cross-module KSP resolution; no ServiceLoader/classpath enumeration.

## Findings → resolutions

| Lens / severity | Finding | Resolution |
|---|---|---|
| build / blocker | Sample abiValidation forced on → apiCheck demands a dump | Commit sample ABI dump + hard-code KSP args |
| build / blocker | Processor publishing story contradictory (only MPP convention) | Non-published v1; `convention.common`+jvm+explicitApi; publishing = pre-release follow-up |
| ksp / major | `on<A>` needs source-legal reified arg; string templates break | KotlinPoet + validate-and-reject generic/nullable/inner/private/local A |
| ksp / major | `KSType.equals` unreliable for arity/return checks | Compare `declaration` identity; assert arity/nullability/no-type-args |
| ksp / major | Grouping key must be qualifiedName, not KSType | Group + pair by `declaration.qualifiedName` |
| ksp / major | Top-level funs need imports; simple-name clash | KotlinPoet `MemberName` import + alias |
| ksp / minor | Top-level detection | `functionKind == TOP_LEVEL` |
| ksp / minor | Incremental Dependencies/validate unspecified | `aggregating=true` over all files; `validate()` |
| ksp / minor | M inferred from provider return | Emit explicit `model<M>(provider())` |
| api / major | Default moduleName/package collision (fails at install runtime) | Require `moduleName`; unique FQN |
| api / major | `@ReduxInitial` cross-module hole | v1: same-module required + clear error; ctor-param-initial = fast-follow |
| api / major | Generated symbol undiscoverable | Required name + `logger.info` + KDoc header |
| api / major | Generated+hand-DSL ordering trap | Document ordering rule; recommend install-first |
| test / major | kctfork exists (0.12.1, KSP2) but skews to 2.3.5/2.3.0 | Use it; note skew; sample = prod-engine guard |
| test / major | Goldens brittle + don't prove compilation | Behavioral compile+load; ≤2 normalized goldens |
| test / major | Sample is compile-guard not execute-guard on native/wasm | State precisely; add host-gated native execution |
| test / major | Sample abiValidation (dup blocker) | Commit dump + hard-code args |
| test / major | Nondeterministic emission order = behavior + flake | Total-order sort; determinism test |
| test / minor | Error-case assertions brittle | exitCode≠OK + substring |
| test / minor | Config-cache untested | CC gate in CI |

## Open risks for the review to pressure-test

- The `kspCommonMainMetadata` + `dependsOn` wiring vs Gradle 9.5 implicit-dependency/configuration-cache strictness — does it survive `--configuration-cache`? (Spike didn't test CC.)
- KSP-compile-testing library availability/compat for **KSP2 + Kotlin 2.3.20** — if no compatible version exists, processor tests fall back to driving the sample module's `kspCommonMainKotlinMetadata` task and asserting on generated files.
- Default-package collision ergonomics (decision 2) — is a required `moduleName` arg (no default) safer than a default that can collide?
- `@ReduxInitial` ergonomics vs alternatives — is a provider function the right call, or should the model's initial be declared at the `install` site?
- Generic / nested-class action types in string-template codegen (decision 3).
