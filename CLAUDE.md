# Working in redux-kotlin

A Kotlin Multiplatform port of the Redux contract. A deliberately minimal core
(`redux-kotlin`) plus opt-in companion modules that layer on the same
`Store<S>` contract. Read this before making changes — it captures the build
gate and conventions that aren't obvious from the source.

## Modules

Twenty-one published modules (library modules apply `convention.library-mpp-*`
+ `convention.publishing-mpp`; the BOM applies `java-platform` +
`convention.publishing-platform`). The recommended consumer entry points are
the bundles; everything else is à-la-carte.

**Core trio + thunk:**

- `redux-kotlin` — core: `Store`/`TypedStore`, `Reducer`, `Middleware`, `createStore`, `applyMiddleware`, `combineReducers`, `compose`.
- `redux-kotlin-concurrent` — `createConcurrentStore` (lock-free reads + reentrant-lock-serialized writes; the CallerSerialized strategy; `NotificationContext`/`coalescingNotificationContext`).
- `redux-kotlin-threadsafe` — `createThreadSafeStore` (atomicfu-locked store wrapper). **Deprecated** in favor of `redux-kotlin-concurrent`.
- `redux-kotlin-thunk` — `createThunkMiddleware` async-actions middleware.

**State shape:**

- `redux-kotlin-granular` — `subscribeTo` / `subscribeFields` field-level subscriptions.
- `redux-kotlin-registry` — `StoreRegistry<K,S>` / `TypedStoreRegistry` keyed multi-store container.
- `redux-kotlin-multimodel` — `ModelState` typesafe heterogeneous model bag.
- `redux-kotlin-multimodel-granular` — granular subscriptions for `ModelState`.

**Compose trio:**

- `redux-kotlin-compose` — Compose `State<T>` bindings (`fieldState`, `selectorState`, `StableStore`).
- `redux-kotlin-compose-multimodel` — Compose bindings for `ModelState`.
- `redux-kotlin-compose-saveable` — `rememberSaveableState` store-anchored snapshot persistence (survives rotation + process death) via Compose `SaveableStateRegistry` + kotlinx.serialization.

**Routing:**

- `redux-kotlin-routing` — routed `(model, action)` dispatch over `ModelState`: `createModelStore { model(initial) { on<A> { … } } }`, `onAction`/`onBroadcast`/`install`, `preloadedState` rehydration.

**Bundles:**

- `redux-kotlin-bundle` — one-dependency stack: `createConcurrentModelStore` + registry helpers (concurrent + granular + multimodel(+granular) + registry + routing).
- `redux-kotlin-bundle-compose` — the bundle + the Compose trio; for Compose apps.
- `redux-kotlin-bom` — `java-platform` BOM constraining every published module (incl. devtools, marked experimental).

**DevTools (×6, experimental — BOM-aligned but exempt from semver):**

- `redux-kotlin-devtools-core`, `-bridge`, `-remote`, `-inapp`, `-inapp-noop` (release no-op facade), `-ui`.

**Unpublished repo tools** (`convention.control` or plain JVM, no publishing
plugin): `redux-kotlin-routing-codegen` (KSP `@Reduce`/`@ReduxInitial`
processor — JVM-only, consumed via `project(...)`, listed in the BOM but
publishing is a pre-release follow-up), `redux-kotlin-routing-codegen-sample`,
`redux-kotlin-devtools-standalone` (Compose desktop monitor),
`redux-kotlin-devtools-cli` (library — powers `rk devtools`; exposes `devToolsCommand()`),
`redux-kotlin-snapshot` (library — powers `rk snapshot`; renders `f(state) → PNG`,
diffs against goldens, emits an HTML dashboard; exposes `snapshotCommand(app)` / `SnapshotApp.runCli`),
`redux-kotlin-cli` (the unified `rk` binary; `./gradlew :redux-kotlin-cli:installDist`
→ `redux-kotlin-cli/build/install/rk/bin/rk`),
`redux-kotlin-cli-dist` (Compose bundled-JRE packaging → `brew install reduxkotlin/tap/rk`
/ `scoop install rk`).

`examples/` holds sample apps (`convention.control`, not published) — 
`examples/taskflow` is the canonical bundle showcase. `website/`
is the Docusaurus docs site.

A new companion module: add it to `settings.gradle.kts`, create
`build.gradle.kts` applying `convention.library-mpp-loved` +
`convention.publishing-mpp`, set `commonMain` deps
`api(project(":redux-kotlin"))` + whatever it needs, use package
`org.reduxkotlin.<feature>`, and add the constraint to
`redux-kotlin-bom/build.gradle.kts`. Mirror `redux-kotlin-registry` as the
template.

## Build & test

```bash
./gradlew build                              # full build (compile + test + detekt)
./gradlew :redux-kotlin-registry:jvmTest     # one module's JVM tests
./gradlew :redux-kotlin-registry:allTests    # all targets the host can run
./gradlew detektAll                          # lint the whole tree (see gate below)
./gradlew apiDump                            # regenerate public-API dumps (run after API changes)
./gradlew apiCheck                           # verify public API matches the committed dump
```

### Test source-set layout (follow it)

- `commonTest` — platform-uniform correctness; runs on every target. Default home for tests.
- `jvmTest` — JVM-only (e.g. concurrency stress using `java.util.concurrent`). Native/JS have different memory models; keep contention tests here.
- `jvmCommonTest` — shared parent source set feeding both `jvmTest` and `androidUnitTest`; put shared JVM-side test deps here.

`kotlin-test` is wired by the convention plugins — don't add test framework deps
manually. `redux-kotlin-granular`'s and `redux-kotlin-registry`'s test suites
are good patterns to copy.

## The lint gate (detekt) — the #1 source of friction, read this

- Engine: **detekt 2.0.0-alpha.3** under the `dev.detekt` plugin (note: the
  `dev.detekt` namespace, not the old `io.gitlab.arturbosch.detekt`), with the
  ktlint-wrapper formatting rules. Config: `gradle/detekt.yml`,
  `buildUponDefaultConfig = true`. Task: `detektAll` (root-level, scans the whole tree).
- Git hooks run it automatically: **pre-commit = `detektAll --auto-correct`**,
  **pre-push = `detektAll`**. `--auto-correct` fixes formatting in place, so a
  commit may rewrite your files — re-stage and commit again if so.
- **Formatting violations auto-correct** (trailing commas, multi-line `if/else`
  brace style, wrapping). Don't hand-fix these; let the hook do it.
- **KDoc violations do NOT auto-correct** — you must write them. `explicitApi()`
  is on, so every `public` declaration needs an explicit modifier AND a KDoc
  comment, including nested `data class`es and their properties
  (`UndocumentedPublicClass` / `UndocumentedPublicProperty`). Document public
  symbols as you write them to avoid a hook bounce.
- Excluded from scanning: `**/build`, `scripts/`, `.claude/`, `**/jvmBenchmark/**`.
- Never bypass with `--no-verify`. If the hook fails, fix the cause.

## KMP targets & where they run

`convention.library-mpp-loved` targets: `jvm`, `android`, `js` (browser+node),
`wasmJs` (browser+node), `iosArm64`, `iosSimulatorArm64`, `macosArm64`,
`linuxX64`, `mingwX64`. The core uses `convention.library-mpp-all`,
which additionally has `linuxArm64`.

- `jvm`/`js`/`android` compile on **every** host (host-independent; the Android
  samples depend on them).
- **Native** targets are host-gated on CI — only built on a matching host OS.
- **iOS simulator tests need a Mac with the Xcode iOS SDK** and may not run on
  every dev box. If `iosSimulatorArm64Test` fails locally with an Xcode SDK
  error, that's environmental — trust CI for cross-platform verification.

## Public API stability

This repo tracks its public API surface with committed dumps under each
module's `api/` directory. After any change to a `public` declaration:

```bash
./gradlew apiDump     # regenerate the dumps
```

Commit the updated `*.api` files alongside the code. `apiCheck` runs as part of
`build` and fails if the surface drifts from the committed dump — that's the
intended guardrail, not a nuisance. An intentional API change = update the dump;
an accidental one = the check catches it.

## Git & commits

- Conventional Commits: `type(scope): subject` (`feat`, `fix`, `docs`, `test`,
  `build`, `chore`, `refactor`, `perf`). Match recent `git log` style.
- Hooks are worktree-safe and auto-install at configuration time. Skip install
  with `GIT_HOOKS_SKIP_INSTALL=1` (used in CI / sandboxes).
- Create new branches from the latest remote `master`.

## Website

Docusaurus in `website/`. `yarn build` is the CI guard and uses
`onBrokenLinks: 'throw'` — a broken internal link fails the build, so build
locally after editing docs. reduxkotlin.org is hosted on **Vercel**;
`website/vercel.json` holds the URL redirects (the `static/_redirects` file is a
Netlify convention that Vercel ignores).

## Publishing

Maven Central via the vanniktech publish plugin → Central Portal
(`convention.publishing-mpp` / `convention.publishing-nexus`). Don't change
publishing config without intent.
