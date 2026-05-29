# redux-kotlin-routing-codegen

A KSP processor that generates a `ReduxModule` from `@Reduce` handler functions,
so you annotate functions instead of writing the `createModelStore { model { on<A> {} } }`
DSL by hand.

## Setup (consumer module build.gradle.kts)

```kotlin
plugins {
    id("convention.library-mpp-loved") // or your KMP setup
    id("com.google.devtools.ksp")
}
dependencies {
    add("kspCommonMainMetadata", project(":redux-kotlin-routing-codegen"))
}
ksp {
    arg("routing.moduleName", "MyFeature")             // REQUIRED — names the generated object
    arg("routing.generatedPackage", "com.example.gen") // optional, defaults to org.reduxkotlin.routing.generated
}
kotlin {
    sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
```

## Usage

Annotate top-level functions in `commonMain`:

```kotlin
@ReduxInitial fun userInitial(): UserModel = UserModel()
@Reduce fun onLoggedIn(s: UserModel, a: LoggedIn): UserModel = s.copy(user = a.user)
@Reduce fun onLoggedOut(s: UserModel, a: LoggedOut): UserModel = s.copy(user = null)
```

The processor generates `object MyFeature : ReduxModule`, installed with:

```kotlin
val store = createModelStore { install(MyFeature) }
```

## Rules

- `@Reduce` must be a **top-level** function `(M, A) -> M` (returns the model type).
  Model and action types must be **non-generic, non-nullable, public/internal** classes.
  Matching is by the action's **exact leaf class** (not subtypes).
- `@ReduxInitial` is a **top-level** `() -> M` provider. **Exactly one per model type,
  in the same module as that model's `@Reduce` handlers.** A model with handlers but no
  in-module `@ReduxInitial` is a compile error — for models shared across modules, register
  handlers with the hand-written DSL instead.
- Handlers must live in **`commonMain`** (only `kspCommonMainMetadata` is wired).

## Ordering with the hand-written DSL

`install(MyFeature)` registers its handlers at that point in the `createModelStore { }`
sequence. A hand-written handler for the same action placed before/after the `install(...)`
runs before/after the generated ones (registration order fixes dispatch order, and
last-write-wins applies within a dispatch). If mixing, install generated modules first
unless you intend otherwise.

## v1 limitations

- Single-model `@Reduce` handlers only. Multi-model (`onAction`) and broadcast
  (`onBroadcast`) handlers, and handlers in platform source sets, are NOT generated —
  use the hand DSL for those.
- The processor is **not yet published to Maven Central** — consume it via a local
  `project(...)` dependency for now (a JVM publishing setup is a pre-release follow-up).
