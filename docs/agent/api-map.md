---
tier: T2
concern: api-map
derives_from:
  - "*/api/*.klib.api → committed public API surface"
api_files: []
rules: []
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 35351cc, date: 2026-06-10 }
---

# API map

Read the committed `.api` dump for a module's public surface; regenerate with `./gradlew apiDump`.

| Module | `.api` path | What's in it |
|---|---|---|
| `:redux-kotlin` | `redux-kotlin/api/redux-kotlin.klib.api` | Core contract: `Store`/`TypedStore`, `Reducer`, `Middleware`, `createStore`, `applyMiddleware`, `combineReducers`, `compose`. |
| `:redux-kotlin-threadsafe` | `redux-kotlin-threadsafe/api/redux-kotlin-threadsafe.klib.api` | `createThreadSafeStore` (atomicfu-locked store wrapper). |
| `:redux-kotlin-concurrent` | `redux-kotlin-concurrent/api/redux-kotlin-concurrent.klib.api` | `createConcurrentStore` (lock-free reads + reentrant-lock-serialized writes; CallerSerialized strategy) + `NotificationContext` notify control (`coalescingNotificationContext` runs the callback inline when already on the target thread, else posts — one callback per subscriber per dispatch, no burst collapsing). |
| `:redux-kotlin-thunk` | `redux-kotlin-thunk/api/redux-kotlin-thunk.klib.api` | `Thunk<State>` typealias + `createThunkMiddleware(extraArgument)` async action middleware (ported from the standalone repo, same coordinates). |
| `:redux-kotlin-granular` | `redux-kotlin-granular/api/redux-kotlin-granular.klib.api` | `subscribeTo` / `subscribeFields` field-level subscriptions. |
| `:redux-kotlin-registry` | `redux-kotlin-registry/api/redux-kotlin-registry.klib.api` | `StoreRegistry<K,S>` / `TypedStoreRegistry` keyed multi-store container. |
| `:redux-kotlin-multimodel` | `redux-kotlin-multimodel/api/redux-kotlin-multimodel.klib.api` | `ModelState` typesafe heterogeneous model bag (incl. `withAll` bulk merge). |
| `:redux-kotlin-multimodel-granular` | `redux-kotlin-multimodel-granular/api/redux-kotlin-multimodel-granular.klib.api` | Granular subscriptions for `ModelState`. |
| `:redux-kotlin-compose` | `redux-kotlin-compose/api/redux-kotlin-compose.klib.api` | Compose `State<T>` bindings (`fieldState`, `selectorState`, `StableStore`). |
| `:redux-kotlin-compose-multimodel` | `redux-kotlin-compose-multimodel/api/redux-kotlin-compose-multimodel.klib.api` | Compose bindings for `ModelState`. |
| `:redux-kotlin-compose-saveable` | `redux-kotlin-compose-saveable/api/redux-kotlin-compose-saveable.klib.api` | `StateSaver<S,Snapshot>` + `Store<S>.rememberSaveableState` snapshot persistence for Compose via `SaveableStateRegistry` (survives rotation + process death). |
| `:redux-kotlin-routing` | `redux-kotlin-routing/api/redux-kotlin-routing.klib.api` | Routing DSL + `@Reduce`/`@ReduxInitial` annotations and `ReduxModule` contribution surface. |
| `:redux-kotlin-routing-codegen-sample` | `redux-kotlin-routing-codegen-sample/api/redux-kotlin-routing-codegen-sample.klib.api` | Sample showing KSP-generated routing wiring (`ReduxModule` output). |
| `:redux-kotlin-bundle` | `redux-kotlin-bundle/api/redux-kotlin-bundle.klib.api` | One-call assembly of a concurrent `ModelState` store + registry + routing (`createConcurrentModelStore`, `getOrCreateConcurrentModelStore`; optional `preloadedState`). |
| `:redux-kotlin-bundle-compose` | `redux-kotlin-bundle-compose/api/redux-kotlin-bundle-compose.klib.api` | Compose-side bundle conveniences (no published declarations). |
| `:redux-kotlin-devtools-core` | `redux-kotlin-devtools-core/api/redux-kotlin-devtools-core.klib.api` | DevTools entry points: `devTools` enhancer + `DevToolsConfig`, `DevToolsHub`/`DevToolsSession` (incl. `maxAge`, `outputsFlow`), `devToolsMiddleware`, `devToolsCombineReducers`, `DevToolsOutput` (with `isRunning`), state/action diffing (`DiffOp`, `DiffEntry`). |
| `:redux-kotlin-devtools-bridge` | `redux-kotlin-devtools-bridge/api/redux-kotlin-devtools-bridge.klib.api` | Wire protocol between app and DevTools UI (`BridgeMessage`, `BridgeConfig`, `BridgeOutput`) plus the `.jsonl` recording codec (`RecordingHeader`, `encodeRecording`/`decodeRecording`/`decodeRecordingLenient`). |
| `:redux-kotlin-devtools-remote` | `redux-kotlin-devtools-remote/api/redux-kotlin-devtools-remote.klib.api` | Remote DevTools transport config (`RemoteConfig`). |
| `:redux-kotlin-devtools-inapp` | `redux-kotlin-devtools-inapp/api/redux-kotlin-devtools-inapp.klib.api` | In-app DevTools: overlay host (`ReduxDevToolsHost`) + embeddable inspector (`ReduxDevToolsPanel` — tabs only, no bubble/drawer, for mounting in your own UI); triggers/config (`DevToolsTrigger`, `InAppConfig`, `DevToolsTab`). |
| `:redux-kotlin-devtools-inapp-noop` | `redux-kotlin-devtools-inapp-noop/api/redux-kotlin-devtools-inapp-noop.klib.api` | No-op in-app DevTools (same host + panel + config types, stripped for release builds). |
| `:redux-kotlin-devtools-ui` | `redux-kotlin-devtools-ui/api/redux-kotlin-devtools-ui.klib.api` | Compose DevTools UI panels — package `org.reduxkotlin.devtools.ui` (`DevToolsTab`, `DevToolsThemeMode`, `ui.model` view-state, `RkTokens`). |

`redux-kotlin-bom` (BOM platform) and `redux-kotlin-routing-codegen` (KSP processor) are
published but carry no `.api` dump, so they're not listed.

Published, experimental — exempt from semver (ABI tracked in
`redux-kotlin-snapshot/api/redux-kotlin-snapshot.api`):
`redux-kotlin-snapshot` is the library behind `rk snapshot` — on Maven Central as
`org.reduxkotlin:redux-kotlin-snapshot` and in the BOM. It renders a redux-kotlin Compose screen
headlessly from a known state (`f(state) → PNG`), diffs against a committed golden, emits an HTML
dashboard, and extracts a deterministic bounds-free semantics dump (text/JSON node tree) that a
`--verify-semantics` golden can gate on — a cheaper, less flaky regression signal than the pixel diff
for an AI-agent loop. JVM/desktop-only (depend on it from a JVM/desktop source set + `compose.desktop.currentOs`).
Run it via `rk snapshot`, a consuming app's `main` (which calls `runCli`), or the TaskFlow `snapshotUi`
task; see `docs/agent/references/snapshot.md`.

Unpublished developer tools (no `.api` dump, not on Maven):
`redux-kotlin-devtools-standalone` is the `convention.control` Compose desktop monitor app, and
`redux-kotlin-devtools-cli` is the library behind `rk devtools` (clikt commands for the standalone server +
capture queries: `serve`/`stores`/`actions`/`diff`/`state`/`tail`); the installable tool is `rk` from
`./gradlew :redux-kotlin-cli:installDist` (binary: `redux-kotlin-cli/build/install/rk/bin/rk`).
`redux-kotlin-cli-dist` is the Compose app-image + JReleaser packaging module: `createDistributable` builds
a per-OS bundled-JRE app-image and `packageRkArchive` archives it; JReleaser publishes the archives to the
Homebrew tap (`reduxkotlin/homebrew-tap`) and Scoop bucket (`reduxkotlin/scoop-bucket`) on a tagged release.

`examples/*` are `convention.control` (not published, no `.api`).
