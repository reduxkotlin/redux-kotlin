# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

> **Heading toward 1.0.** The development baseline is now `1.0.0-SNAPSHOT`. The
> first 1.0 release is planned as a pre-release, `1.0.0-alpha01`, published from
> a `v1.0.0-alpha01` tag via the release workflow. DevTools modules ship
> alongside but remain experimental and exempt from semver.

### Added

- `redux-kotlin-devtools-inapp`: new public composable `ReduxDevToolsPanel(instanceId, startTab,
  theme)` — an **embeddable** DevTools inspector (the Actions/State/Diff/Pipeline/Outputs tabs with
  no bubble or overlay drawer) for mounting inside your own UI, e.g. a host app's debug drawer. The
  inspector body was extracted into a shared `InspectorBody`, so `ReduxDevToolsHost` is unchanged.
  Mirrored as an inert no-op in `redux-kotlin-devtools-inapp-noop` (release-stripped).
- New module `redux-kotlin-thunk`: ported from the standalone
  [reduxkotlin/redux-kotlin-thunk](https://github.com/reduxkotlin/redux-kotlin-thunk) repo into
  the monorepo — `Thunk` typealias + `createThunkMiddleware`. Published under the same maven
  coordinates `org.reduxkotlin:redux-kotlin-thunk` and added to the BOM; the standalone repo
  will be archived.
- New module `redux-kotlin-compose-saveable`: `StateSaver` + `rememberSaveableState` —
  store-anchored snapshot persistence via Compose `SaveableStateRegistry` +
  kotlinx.serialization. Survives rotation and process death on Android; restore is applied
  synchronously during composition (no stale first frame).
- `redux-kotlin-concurrent`: `coalescingNotificationContext(isOnTargetThread, post)` — runs
  subscriber callbacks inline when the dispatch is already on the target (main) thread, posts
  otherwise; removes the one-frame notification lag for main-thread dispatches.
- `redux-kotlin-routing` / `redux-kotlin-bundle`: optional `preloadedState: ModelState?` on
  `createModelStore` / `createConcurrentModelStore` — overlays restored models onto the declared
  defaults at construction, so the first read/render already reflects rehydrated state.
- `redux-kotlin-multimodel`: `ModelState.withAll(other: ModelState)` overlay overload backing
  `preloadedState`.
- `redux-kotlin-snapshot` is now **published** to Maven Central
  (`org.reduxkotlin:redux-kotlin-snapshot`) and constrained by `redux-kotlin-bom` — previously an
  in-repo-only tool. **Experimental**, exempt from semver like the DevTools family. Published via a
  new `convention.publishing-jvm` (the repo's first JVM-only publishing convention) and ABI-tracked
  under `redux-kotlin-snapshot/api/`. JVM/desktop-only: depend on it from a JVM/desktop source set
  and add `compose.desktop.currentOs` for the host Skiko runtime.
- **New DevTools family** — action/state/diff/pipeline inspection for redux-kotlin apps.
  Six published modules:
  - `redux-kotlin-devtools-core` — the `devTools(config)` store enhancer, `DevToolsConfig`,
    the process-global `DevToolsHub`/`DevToolsSession`, `devToolsMiddleware` /
    `devToolsCombineReducers` pipeline instrumentation, and JSON state diffing.
  - `redux-kotlin-devtools-bridge` — `BridgeOutput`/`BridgeConfig`: streams a session to the
    standalone monitor / CLI over WebSocket; also the `.jsonl` recording codec
    (`encodeRecording` / `decodeRecording` / `decodeRecordingLenient`).
  - `redux-kotlin-devtools-remote` — `RemoteOutput`/`RemoteConfig`: streams to an external
    Redux DevTools monitor (browser extension / `@redux-devtools/cli`).
  - `redux-kotlin-devtools-inapp` — `ReduxDevToolsHost` + `InAppConfig`: the in-app Compose
    Multiplatform drawer (bubble / edge-swipe triggers, Actions/State/Diff/Pipeline/Outputs tabs).
  - `redux-kotlin-devtools-inapp-noop` — zero-overhead release sibling mirroring the inapp +
    core facade for `releaseImplementation` substitution.
  - `redux-kotlin-devtools-ui` — shared Compose UI panels (`DevToolsTab`, `DevToolsThemeMode`)
    used by both the in-app drawer and the standalone monitor.

  Plus two unpublished developer tools in the repo: `redux-kotlin-devtools-standalone`
  (Compose desktop monitor app, `./gradlew :redux-kotlin-devtools-standalone:run`) and
  `redux-kotlin-devtools-cli` (the library behind `rk devtools` in the unified `rk` CLI —
  `brew install reduxkotlin/tap/rk` / `scoop install rk`, or build from source with
  `./gradlew :redux-kotlin-cli:installDist`, then run `redux-kotlin-cli/build/install/rk/bin/rk devtools`).
  See [docs/devtools.md](docs/devtools.md) for the integration guide.

  The six published DevTools modules are version-aligned by `redux-kotlin-bom` but
  marked **experimental**: they are exempt from the semver guarantee until the
  devtools surface stabilizes and may change in minor releases.
### Fixed

- `redux-kotlin-concurrent`: the state mirror is now published **before**
  subscriber notifications are signaled (previously after, in the dispatch
  epilogue). A posted callback racing ahead of the dispatching thread could
  read the pre-dispatch mirror; for diff-based consumers (granular
  subscriptions, Compose bindings) that meant a lost wakeup — the binding
  stayed stale until the next dispatch. A callback now always observes state
  at least as new as the dispatch that triggered it. The old
  "mirror published after listeners / no mid-listener tear" wording is
  retired: off-context readers may observe the new state while listeners run.
- `redux-kotlin-concurrent`: after `unsubscribe()` returns, no new callback
  invocation begins — a callback already queued on a posting context is
  skipped at execution time (deliberate divergence from core Redux snapshot
  delivery; with an inline context a peer unsubscribed earlier in the same
  fan-out is skipped).
- `redux-kotlin-concurrent`: a throwing `onError` handler no longer aborts
  delivery to remaining subscribers or escapes `dispatch` — it is printed and
  swallowed.
- `redux-kotlin-compose`: `selectorState` / `fieldState` now install their store
  subscription **before** running the subscribe-time re-sample (previously
  after). A state change landing between the re-sample and the subscription
  install — e.g. a fast off-main dispatch during effect commit — could
  otherwise go unobserved forever (no notification, no re-sample). With the
  new order every change before the install is caught by the re-sample and
  every change after it by the subscription; the worst-case overlap is one
  redundant same-value recomposition.
- `redux-kotlin-granular`: `subscribeTo` / `subscribeFields` registration is
  now race-safe — after the underlying `store.subscribe` is installed, every
  selector is re-evaluated and a change that landed during registration fires
  the real `(old, new)` diff at activation (previously it was silently
  missed; only `triggerOnSubscribe` entries got a `(current, current)`
  callback). A moved value subsumes the `triggerOnSubscribe` callback — no
  double-fire; worst case on a posting store is one redundant same-value
  callback.
- `redux-kotlin-devtools-core`: silent capture drops are now counted and surfaced
  (the drop counter was previously dead code — captures could be lost without any
  signal); capture writes are atomic so a reader never observes a half-written
  entry. `DevToolsHub.registerOutput` dedupes by output **instance**, so
  re-registering the same output no longer double-streams every event.
- `redux-kotlin-devtools-bridge`: reseeding a session (monitor reconnect / late
  attach) now emits an `Init` event first, so the monitor rebuilds its baseline
  instead of diffing against a stale one. Monitor reconnects resume from the
  recorded stream — previously a reconnect could drop the actions captured while
  disconnected. Non-loopback connections are peer-checked against the shared
  `token`.
- `redux-kotlin-devtools-remote`: `RemoteOutput` gained an injectable `logger`
  (connection errors were previously swallowed), and the legacy
  socketcluster-style ping/pong handshake is answered correctly so the
  `@redux-devtools/cli` monitor no longer drops the connection.
- `redux-kotlin-devtools-inapp-noop`: the no-op facade now mirrors the real API
  one-for-one (including `KotlinxValueSerializer` and the core combinators), so a
  `releaseImplementation` substitution compiles against the same surface; an
  automated parity gate keeps the facades aligned.
- `redux-kotlin-devtools-inapp`: `InAppConfig.startTab` is honored,
  `DevToolsThemeMode.SYSTEM` follows the platform theme, `DevToolsSession.maxAge`
  is surfaced, the session list is reactive (stores registered after the drawer
  mounts appear without reopening), and the Outputs tab reflects the truthful
  hub-global output state — toggles act on the hub's outputs, not a per-drawer
  copy.

- `redux-kotlin-bom`: no longer constrains `redux-kotlin-routing-codegen` — the KSP
  processor is not published yet (in-repo only); the constraint returns when it ships.

### Deprecated

- `redux-kotlin-compose`: `StableStore` and `rememberStableStore` are deprecated because
  `.value` exposes the raw store to Compose. Migrate binding components to
  `rememberSelectorStore`, `selectorState` / `fieldState`, and method-form `dispatch`.
- `redux-kotlin-threadsafe` is deprecated in favor of `redux-kotlin-concurrent`. All public
  declarations (`createThreadSafeStore`, `createTypedThreadSafeStore`, `ThreadSafeStore`,
  `Store.asThreadSafe`, `createThreadSafeStoreEnhancer`) now carry `@Deprecated` warnings.
  Migration: `createConcurrentStore(reducer, preloadedState, enhancer = enhancer)` /
  `createTypedConcurrentStore` / `Store.asConcurrent()` keep the same contract with lock-free
  reads and serialized writes. The module will be retired in a future release.

### Changed

- **BREAKING (pre-1.0):** `redux-kotlin-compose`'s `SelectorStore` is now a narrow,
  Compose-stable selection-and-dispatch capability instead of implementing `Store`.
  Direct state reads, manual subscriptions, reducer replacement, and raw-store escape are
  no longer available below the composition host. Runtime/effect code keeps the raw `Store`;
  binding components use `selectorState` / `fieldState` and `dispatch(action)`.
- **Behavior change:** `dispatch` from inside a reducer now throws
  `IllegalStateException` ("You may not dispatch while state is being reduced"),
  restoring the core Redux contract. Previously the nested dispatch was silently
  accepted and its state change overwritten. `getState`/`subscribe`/`unsubscribe`
  already enforced the same guard. Dispatch follow-up actions from middleware or a
  subscriber instead.
- `redux-kotlin-compose`: `selectorState` / `fieldState` now read store state synchronously on
  every read and use the subscription only to schedule recomposition — bindings stay fresh even
  with asynchronous notification contexts, and re-sample conditionally at subscribe so a change
  landing before the subscription is not lost.
- CI/toolchain bumped to JDK 21; library bytecode stays at JVM 17 to preserve downstream
  compatibility with JDK 17 consumers. Test matrix runs both JDKs.
- Sample apps modernised: `compileSdk`/`targetSdk` 33 → 35, `JavaVersion` 1.8 → 21,
  `packagingOptions` → `packaging`. The standalone `examples/` Gradle build was folded into
  the root composite so sample apps are now exercised in CI on every push.
- Build conventions: adopted Kotlin's default hierarchy template via
  `applyDefaultHierarchyTemplate`; removed the bespoke `targetGroup` wiring util.
- Tooling: `com.gradle.enterprise` plugin migrated to `com.gradle.develocity`; Renovate
  upgraded to `config:recommended` with grouped `packageRules` for kotlin-ecosystem,
  android-build, gradle-build, and github-actions.
- **BREAKING (pre-1.0, unreleased):** `redux-kotlin-devtools-ui` package renamed
  `org.reduxkotlin.devtools.inapp` → `org.reduxkotlin.devtools.ui` — `DevToolsTab` and
  `DevToolsThemeMode` now import from `org.reduxkotlin.devtools.ui` (`InAppConfig` and the
  triggers stay in `org.reduxkotlin.devtools.inapp`).
- `redux-kotlin-devtools-bridge` moved to the standard companion-module target tier
  (drops `linuxArm64`; all other targets unchanged). `redux-kotlin-devtools-standalone`
  dropped its `wasmJs` web viewer — the monitor is desktop-only.
- `redux-kotlin-devtools-bridge`: `BridgeConfig` gained `storeName` (explicit display
  name for the monitor's store rail).

### Removed

- `kotlinx-atomicfu-gradle-plugin` is no longer applied. The codebase only uses
  `kotlinx.atomicfu.locks` (not the AtomicXxx types the plugin optimises), so the plugin
  was dead weight and was incompatible with the new `com.android.kotlin.multiplatform.library`
  plugin. The `kotlinx-atomicfu` runtime library is retained.
- Stale Android sample bits: `iosArm32()` target (removed in Kotlin 1.9.20),
  `kotlin("android")` plugin (removed in AGP 9.0), unused `kotlin("kapt")` plugin.

---

## [0.6.0]

### Added

- All missing ios, watchos, tvos and macos simulator targets added
- Added `androidNativeX64` and `androidNativeX86` targets
- Added proper android release and debug variants instead of piggybacking on jvm artefact
- New and improved `typedReducer` and `createTypedStore` builders for those needing a simple action-typed store. 
  Recommended to use with sealed interface hierarchies.

### Changed

- Major gradle infra rework
- Enabled `explicitPublicApi()`
- **BREAKING**: `redux-kotlin-threadsafe` APIs moved to a new package: `org.reduxkotlin.threadsafe`

### Removed

- Remove deprecated `wasm32` target

---

## [0.5.5] - 2020-08-16

- update to Kotlin 1.4.0
- added platforms (androidNativeArm32, androidNativeArm64, iosArm32, linuxArm64, linuxX64,
  mingwX86, tvosArm64, tvosX64, watchosArm32, watchosArm64, watchosX86)
- remove spek & atrium deps and use plain kotlin tests & assertions. Tests run for all platforms now.

---

## [0.5.2] - 2020-07-03

- publish all available platforms to maven
- add CI/CD through github actions

---

## [0.5.1] - 2020-06-11

- update lib dependency to api import, so core lib is included in redux-kotlin-threadsafe

---

## [0.5.0] - 2020-06-11

- kotlin 1.3.72
- createThreadSafeStore fun added for thread synchronized access
- createEnsureSameThreadStore to provide existing same-thread-enforcement

---

## [0.4.0] - 2020-03-23

- kotlin 1.3.70

---

## [0.3.2] - 2020-02-22

- issue #34 - incorrect same thread enforcement behavior fixed

---

## [0.3.1] - 2019-12-16

### Changed

- update same thread enforcement message to not be getState only

---

## [0.3.0] - 2019-12-16

### Added

- thread enforcement

---

## [0.2.9] - 2019-11-23

### Changed

- update Kotlin to 1.3.60

---

[Unreleased]: https://github.com/reduxkotlin/redux-kotlin/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/reduxkotlin/redux-kotlin/compare/v0.5.5...0.6.0
[0.5.5]: https://github.com/reduxkotlin/redux-kotlin/releases/tag/v0.5.5
