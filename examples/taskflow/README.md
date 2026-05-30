# TaskFlow — the redux-kotlin bundle sample

TaskFlow is a Compose Multiplatform Kanban app that exists to **exercise the
`redux-kotlin` bundle end-to-end** on every target. It is a deliberately
realistic showcase: multiple accounts, an offline-first sync layer over a fake
network, an undo/redo stack, a simulated collaborator bot, and a board screen
built for tight render isolation. Every store, slice, subscription, and
middleware here maps to a specific bundle API — this README is the map.

> Sample app only — `examples/taskflow` is **not** a published module
> (`convention.control`, not `convention.publishing-mpp`). It depends on the
> bundle via `project(":redux-kotlin-bundle-compose")`.

## What it demonstrates

- **One root store + a store-per-account registry** — two store-composition
  philosophies side by side (single fixed-slot store vs. keyed multi-store).
- **`ModelState` multi-model** — each store holds many typesafe model slots
  rather than one monolithic state tree.
- **Routing-DSL reducers** — every slot's actions are wired with the
  `on<Action> { … }` routing builder; no hand-written `when(action)` dispatch
  at the store boundary.
- **Granular Compose subscriptions** — `fieldStateOf` / `selectorState` /
  `rememberStableStore`, so a card move recomposes only the two affected
  columns, never the whole board.
- **Concurrency** — the bundle's `createConcurrentModelStore` (lock-free reads,
  serialized writes), with subscriber callbacks marshalled to the main thread.
- **Offline-first persistence + a fake network**, joined by a sync engine with
  per-op inverse revert on server rejection.

## The bundle APIs and where they live

The bundle (`redux-kotlin-bundle-compose`) re-exports, as a single dependency:
`redux-kotlin-concurrent`, `redux-kotlin-registry`, `redux-kotlin-routing`,
`redux-kotlin-multimodel-granular`, and `redux-kotlin-compose-multimodel`
(which pulls in `redux-kotlin-compose`). **Note: the bundle's concurrency base
is `redux-kotlin-concurrent`, not `redux-kotlin-threadsafe`.**

| Bundle API | Where TaskFlow uses it |
| --- | --- |
| `createConcurrentModelStore { model(...) { on<A>{…} } }` | `store/AppStore.kt` (root store) and `store/AccountStore.kt` (per-account store). The lambda declares every model slot up front and routes actions to pure reducers. |
| Routing DSL `model(seed) { on<Action> { s, a -> reducer(s, a) } }` | `store/AppStore.kt`, `store/AccountStore.kt` (`declareAccountModels`). `on<A>` routes by **exact leaf action class**; a slot only registers the actions it handles. |
| `StoreRegistry<K, S>` | `store/AccountRegistry.kt` holds a `StoreRegistry<AccountId, ModelState>` — one isolated store per account, looked up lock-free. |
| `getOrCreate(key) { … }` (registry) | `AccountRegistry.getOrCreate` builds the per-account store + its handle, mirrors the bare `Store` into the registry, and serves it lock-free on later lookups. (The bundle also ships a one-call `StoreRegistry.getOrCreateConcurrentModelStore` extension; TaskFlow builds the store itself because each account needs a richer handle carrying its scope / sync repo / bot job.) |
| `ModelState` multi-model | `model/*.kt` defines the slots (`AccountsModel`, `AppSettingsModel`, `BoardModel`, `SyncModel`, `FilterModel`, `UndoModel`, …); `store/StoreExt.kt`'s `getModel<M>()` reads a typed slot for effects/handlers. |
| `rememberStableStore(store)` | every screen (`App.kt`, `ui/screens/BoardScreen.kt`, …) wraps the store once so Compose treats it as stable. |
| `fieldStateOf(M::class) { … }` | the workhorse subscription (46 call sites). Each binds one model slot and projects a **minimal** slice — e.g. `CardCell` binds only its own `Card` and its optimistic flag. |
| `selectorState { ms -> … }` | derived/cross-model state (visible card ids, WIP count, board name) computed in the selector, **never** in a composable body. |
| `applyMiddleware(...)` | `store/AccountStore.kt` stacks `activityLoggerMiddleware`, `undoMiddleware`, and the offline-first `effectsMiddleware` behind the per-account store. |

## Store topology

Two stores, on purpose:

- **Root `AppStore`** (`createAppStore`, `store/AppStore.kt`) — one app-global
  concurrent `ModelState` store, always present. Holds the account directory
  (`AccountsModel`), app settings (`AppSettingsModel`, including the
  fake-service knobs), and the login/add-account flow (`AuthFlowModel`).
- **Per-account stores** in `AccountRegistry`
  (`StoreRegistry<AccountId, ModelState>`) — one isolated store per logged-in
  account, created lazily on switch-in. Each carries that account's board-side
  slices behind its own effects/undo/activity middleware and its own
  `SyncRepository` over an isolated `FakeRemoteApi`. Switching accounts swaps
  the active store; each account keeps its own remembered route.

**Fixed-slot sentinel + reset-on-leave.** `ModelState` keys must always exist,
so every per-account slot is declared up front; the board slots start at an
empty/`NotLoaded` sentinel (`BoardModel().board == null`). Opening a board fills
`BoardModel` via the load effect; **leaving a board dispatches `BoardClosed`,
which resets the board / filter / undo / sync slices back to their sentinels**
(`App.kt` `BoardLifecycleEffect`), so the large `cards` map and undo snapshots
are released — memory stays bounded without ever removing a slot.

## Render isolation

The board screen (`ui/screens/BoardScreen.kt`) is the showcase. The discipline:

- **No composable selects the board, its cards, or its columns wholesale.** A
  lightweight column descriptor list is bound once; each `ColumnView` is wrapped
  in `key(colId)` and subscribes only to its own visible card ids **by
  `ColumnId`, never by index**.
- Each card is wrapped in `key(cardId)` and binds only its own `Card` plus its
  optimistic flag (`cardId in SyncModel.inFlight`).
- All derivation (visible/filtered ids, WIP count, name lookups) lives in
  `selectorState { … }` or in reducers — never `.filter`/`.count` in a
  composable body.

The net effect: moving one card recomposes only the two affected columns; every
other column stays frozen. This is proved by
`composeApp/src/jvmTest/.../ui/RenderIsolationTest.kt`, which counts
recompositions per column while dispatching a card move and asserts the
untouched column's count is flat.

## Persistence vs. network — two layers, one sync engine

These are deliberately **separate** layers, joined by the offline-first sync
engine. (See `data/` and design §13.)

- **`LocalStore` (SQLDelight)** — the durable offline cache.
  `SqlDelightLocalStore` (`data/local/`) runs the same generated SQL on every
  target. **No artificial latency** — reads are instant and always available,
  online or off. Seeded once per install via `LocalStore.ensureSeeded()`.
- **`RemoteApi` / `FakeRemoteApi` (`data/remote/`)** — the fake network. A
  `suspend` seam (`push(ops)` / `pull(since)`) with **configurable latency,
  failure rate, and an offline toggle** (driven from `AppSettingsModel`'s
  fake-service settings, adjustable live in the Settings screen). This is the
  single point a real HTTP client would replace.
- **`SyncEngine` (`data/sync/SyncEngine.kt`)** wires them offline-first. A
  mutation writes `LocalStore` + enqueues a durable outbound op **immediately**
  (instant, optimistic), then kicks the engine. The engine drains the queue to
  `RemoteApi.push` (this is where latency/failure apply):
  - `Accepted` → mark synced, drop from queue.
  - `Rejected` (a server validation conflict) → report a `CardOpFailed`
    carrying the op's **inverse**, so the store **reverts that one op** and
    drops it. This per-op inverse revert is the offline-sync correctness story
    (tested in `jvmTest/.../data/OfflineSyncE2ETest.kt`).
  - `OfflineException` → stop the drain with the queue intact; it retries and
    **drains on reconnect**. A `TransientNetworkException` bumps the op's
    attempt counter and retries later.

  After pushing, a `pull(since)` merges remote changes (including the bot's
  "server-side" edits) back into `LocalStore` (last-write-wins). Reads always
  come from `LocalStore`, so the app is fully usable offline.

## Material 3 "Expressive" path (and the fallback we shipped)

The design targets Material 3 **Expressive**. In this repo's Compose
Multiplatform **1.11.0** material3 build, the Expressive entry points
(`MaterialExpressiveTheme`, `MotionScheme`, `ButtonGroup`,
`FloatingActionButtonMenu`, and `ExperimentalMaterial3ExpressiveApi`) are
**`internal`** and not reachable from app code (verified by `compileKotlinJvm`:
"Cannot access … it is internal"). So TaskFlow ships the stable fallback:

- `ui/theme/Theme.kt` uses the stable **`MaterialTheme`** with the hi-fi color
  scheme, typography, and shapes.
- `ui/theme/Motion.kt` defines **`TaskFlowMotion`** — six spring tokens that
  mirror `MotionScheme.expressive()` (spatial springs with a slight overshoot,
  effects springs critically damped) and drive component-level animations
  directly.

When a material3 build exposes the Expressive theme publicly, the swap is a
one-liner in `Theme.kt` (documented in its KDoc).

## Time

TaskFlow uses the **stdlib `kotlin.time.Instant`** (Kotlin 2.x) as its time
type throughout (`model/*.kt`, `data/**`), with `kotlinx-datetime` **0.7.1** on
the classpath. Dispatch sites mint the clock via a `LocalClock` composition
local (`Clock.System.now()`), keeping reducers pure.

## Image loading and offline fallbacks

`Avatar` and `AttachmentChip` (`ui/components/`) load remote images with a plain
Coil `AsyncImage` (Rule F: never `SubcomposeAsyncImage`), behind a tiered
fallback chain so the app looks complete **offline**:

1. the remote URL;
2. on remote error, a **bundled offline placeholder** PNG chosen
   deterministically by id-hash (`ui/image/BundledFallback.kt` reads
   `Res.readBytes("files/avatars/avatar-N.png")` /
   `files/cards/card-N.png` and feeds the bytes to `AsyncImage`);
3. the ultimate fallback — the deterministic colored **monogram** (avatars) or a
   **broken-link glyph** (attachments) — if even the bundled load fails.

### Bundled placeholder assets

- `composeApp/src/commonMain/composeResources/files/avatars/avatar-0.png …
  avatar-5.png` — 96×96 radial-gradient swatches.
- `composeApp/src/commonMain/composeResources/files/cards/card-0.png …
  card-2.png` — 200×120 diagonal-gradient swatches.

**Provenance:** these are **generated placeholders** (simple solid/gradient
PNGs produced by a small throwaway script), not third-party artwork. They carry
no rights encumbrance — treat them as **CC0 / public-domain**. They exist only
as offline fallbacks; exact imagery is not significant. Regenerate the Compose
`Res` accessors after changing them with:

```bash
./gradlew :examples:taskflow:composeApp:generateComposeResClass
```

## Running it

```bash
# Desktop (JVM)
./gradlew :examples:taskflow:composeApp:run

# Web (wasmJs) — production bundle, then a dev server
./gradlew :examples:taskflow:composeApp:wasmJsBrowserDistribution
./gradlew :examples:taskflow:composeApp:wasmJsBrowserRun

# Android (requires an Android SDK; the androidApp module is only included when one is present)
./gradlew :examples:taskflow:androidApp:installDebug

# iOS — link the shared framework (Mac + Xcode iOS SDK); see iosApp/README.md for the host
./gradlew :examples:taskflow:composeApp:linkDebugFrameworkIosSimulatorArm64
```

### Web durability caveat

On the web target the SQLDelight `LocalStore` runs **sql.js in a Web Worker**,
persisted to the browser's **IndexedDB**. That is durable but browser-scoped:
clearing site data wipes it, and it is less robust than native SQLite on
android/ios/jvm. Nothing else degrades on web — it is the only target where
durability differs. (A move to OPFS-backed web persistence is noted in the
design as a future revisit, once the relevant libraries leave alpha.)

## Documented manual follow-ups

- **Full iOS `.xcodeproj`.** `iosApp/` ships only the SwiftUI host sources +
  `iosApp/README.md`; the `.xcodeproj` (`project.pbxproj`) is a **manual
  follow-up** — it is not auto-generated. Wire the framework via a
  `embedAndSignAppleFrameworkForXcode` Run Script phase (which auto-syncs the
  compose-resources bundle — **no** hand-rolled copy script). See
  `iosApp/README.md` for the exact steps and `Info.plist` keys.
- **Strings** are inline literals (no resource-localized string table) in v1.

## Source map

```
composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/
  App.kt                       root composable: stores, bootstrap, nav, lifecycle, sync, bot
  store/AppStore.kt            root store (createConcurrentModelStore + routing DSL)
  store/AccountStore.kt        per-account store + middleware stack + model declarations
  store/AccountRegistry.kt     StoreRegistry<AccountId, ModelState> + per-account handles
  store/StoreExt.kt            getModel<M>() typed slot reads
  model/                       ModelState slots + domain types
  reducer/                     pure reducers (routed from the store builders)
  middleware/                  effects (offline-first), undo/redo, activity log, bot
  data/local/                  SQLDelight LocalStore (durable, no latency)
  data/remote/                 RemoteApi + FakeRemoteApi (latency / failure / offline)
  data/sync/                   SyncEngine + SyncRepository (offline-first, inverse revert)
  ui/screens/                  per-screen composables (BoardScreen = render-isolation showcase)
  ui/components/               presentational components (Avatar, AttachmentChip, KanbanCard, …)
  ui/image/                    Coil singleton + bundled offline fallbacks
  ui/theme/                    MaterialTheme + TaskFlowMotion (Expressive fallback)
composeApp/src/jvmTest/        render-isolation, account-switch, offline-sync E2E tests
iosApp/                        SwiftUI host sources + README (xcodeproj = manual follow-up)
androidApp/                    Android host (MainActivity)
```
