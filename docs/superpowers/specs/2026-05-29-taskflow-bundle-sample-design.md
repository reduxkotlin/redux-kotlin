# TaskFlow — redux-kotlin bundle showcase sample (design)

**Date:** 2026-05-29
**Status:** Design approved; hi-fi visual specs to be produced next (separate Claude Code session), then implementation plan.

## 1. Purpose & success criteria

A flagship, semi-realistic Compose Multiplatform sample app that demonstrates how
`redux-kotlin-bundle` + `redux-kotlin-bundle-compose` are used in the real world — the
stack most developers will actually adopt. It exists to teach, by example, the patterns
that the smaller `examples/counter` and `examples/todos` do not: cross-component shared
state, multi-model state, store isolation, async side effects, undo/redo, and Compose
`State<T>` bindings with render isolation.

**Success criteria**

- Runs on **Android, iOS, and Web (wasmJs)** from one shared `commonMain` codebase, plus a JVM/desktop target for a fast dev loop.
- Exercises every bundle API in a way that earns its place (no contrived demos):
  `createConcurrentModelStore`, `StoreRegistry.getOrCreateConcurrentModelStore`, routed
  reducers (`on<Action>`), `ModelState` multi-model, granular subscriptions
  (`fieldStateOf` / `selectorState` / `subscribeToModel` / `subscribeFields`), core
  middleware, and the Compose bindings (`rememberStableStore`, `fieldStateOf`,
  `selectorState`).
- Demonstrates the headline value prop: **components react to each other's state without
  direct access** — a column never reads another column, the WIP badge never reads the
  detail panel; they subscribe to slices of one store and re-render independently.
- Looks and feels polished — **UX is P0** — using **Material 3 Expressive**.
- Lives under `examples/` (`convention.control`, not published; no apiDump/apiCheck;
  no mandatory `explicitApi()` KDoc). Passes `detektAll`.

## 2. Concept — TaskFlow (Kanban)

A Trello/Linear-style task board. Columns (To Do / Doing / Done), cards that can be
moved between columns, a global filter bar, per-column WIP badges, a card-detail panel,
an activity feed, and a simulated "bot collaborator." Multiple boards per account, and
multiple simultaneously logged-in accounts.

**Card movement:** animated **tap-to-move** (tap a card to select → `◂ ▸` buttons or a
"move to…" control), with `animateItem` placement + spring settle. Chosen over full
drag-and-drop because DnD on Compose/wasm is immature and would turn the sample into a
DnD-engineering project rather than a state-management showcase. UX stays smooth via
motion-physics animations.

## 3. Store topology — A primary + B for accounts (ADR)

The single-store-vs-multi-store question is genuinely unsettled for mobile Redux. This
sample teaches a defensible synthesis where each philosophy is used for its real purpose.

### Decision

- **Single store with fixed-slot models + reset-on-leave (philosophy A)** organizes each
  account's screens. `ModelState`'s key set is **immutable at construction** (`with`/`withAll`
  throw on an undeclared class; the widening constructor is `@PublishedApi internal`), so the
  earlier "inject models via `replaceReducer`" idea is **not possible** and is dropped. Instead
  every per-account model slot — including the board working slices — is **declared up front**
  with an empty/`NotLoaded` sentinel. Opening a board fills `BoardModel` (via the load effect);
  **leaving a board dispatches `BoardClosed`, which resets `BoardModel`/`FilterModel`/`UndoModel`/
  `SyncModel` back to their empty sentinels** — the large `cards` map and undo snapshots are
  released to GC, bounding memory, while the key set stays fixed. This is the showcase's bound-
  memory lesson, expressed within the routing store's contract.
- **Multi-store via `StoreRegistry` (philosophy B)** isolates **accounts**. One store per
  logged-in account is the textbook isolation case (Slack workspaces / browser profiles).
  Logging out removes only that account's store (`registry.remove(id)`) and cancels its
  coroutine scope.

### Two-layer structure

**Root AppStore** — one `createConcurrentModelStore`, app-global, always present:

- `AccountsModel` — logged-in accounts (summaries) + `activeAccountId`.
- `AppSettingsModel` — theme, language, and the live fake-service knobs.
- `AuthFlowModel` — login/add-account flow (loading, error).

**`AccountRegistry: StoreRegistry<AccountId, ModelState>`** — one isolated store per
account, created lazily on login via `getOrCreateConcurrentModelStore(accountId)`. Each
account store holds:

- `SessionModel` — that account's profile.
- `NavModel` — that account's current screen (remembered across switches) + `openCardId` + `composing` (create-card column).
- `BoardListModel` — that account's boards.
- `CollaboratorsModel` — `PersistentMap<AccountId, AccountSummary>` of people referenceable on this account's cards (owner + bot + assignees), so assignee/creator/bot avatars resolve **without** reaching into the root store.
- `BoardModel`, `FilterModel`, `UndoModel`, `SyncModel` — **declared up front**; empty/`NotLoaded` until a board opens; reset by `BoardClosed` on leave.
- `ActivityModel`.

Switching accounts sets `activeAccountId`; Compose rebinds `registry.store(activeAccountId)`
via `rememberStableStore`. Each account keeps its own Nav + undo history → visible, honest
state isolation.

### Rejected alternatives (recorded)

- **One global store for everything** — registry becomes pointless; weaker showcase.
- **Multi-store + broadcast every action to all stores** (the "wire them together"
  hybrid) — *rejected*. It inherits the cons of both: it kills the only benefit of
  splitting (isolation — every store reprocesses irrelevant actions) and reintroduces
  cross-store ordering coupling. Microsoft's production `redux-micro-frontend` deliberately
  chose *selective* global-action routing + read-only projection over broadcast. The
  legitimate version of "every reducer sees every action" is just philosophy A (single
  store), with memory bounded by reset-on-leave.
- **Runtime model inject/eject via `replaceReducer`** — *rejected as infeasible*. `ModelState`
  is immutable-keyed (verified in `redux-kotlin-multimodel/.../ModelState.kt`); you cannot add
  a model slot to a live store. Fixed slots + sentinel reset is the supported equivalent.

References: [Redux Store Setup FAQ](https://redux.js.org/faq/store-setup),
[Redux code-splitting](https://redux.js.org/usage/code-splitting),
[microsoft/redux-micro-frontend](https://github.com/microsoft/redux-micro-frontend).

## 4. Data flow

**Middleware pipeline** (applyMiddleware order, outer → inner) on each ConcurrentModelStore:

1. **activityLogger** — humanizes actions into `ActivityModel` (capped list).
2. **undo** — snapshots the board before **user** undoable actions; handles `Undo`/`Redo`.
   Bot-originated mutations are distinct, **non-`Undoable`** action types, so they never enter
   the user's undo history.
3. **effects (coroutine)** — intercepts `*Requested` actions, launches a coroutine on the
   account store's `CoroutineScope`, calls the `Repository`, dispatches `*Succeeded`/`*Failed`.
4. **reducer** — authored with the **routing DSL** (`model(Init()) { on<Action> { s, a -> … } }`)
   inside `createConcurrentModelStore { … }`. `on<A>` routes by **exact leaf class**. Pure
   `when`-style reducer helpers exist only as internal, unit-testable functions the `on` blocks
   delegate to; the store is **not** assembled via `combineModelReducers`.

Plus `granularSubscriptionsEnhancer` so columns/badges/detail/feed each re-render off
their own slice. Form keystrokes stay in local Compose `remember` state (not the store),
committed via dispatch on submit.

**Offline-first persistence + fake network sync (two distinct layers — see §13).** Local
persistence and the network are **separate concerns**:

- **LocalStore (SQLDelight)** is the durable offline cache — instant, **no** artificial latency.
- **RemoteApi (fake network)** simulates a backend (`pull`/`push`) with the configurable
  latency + failure + an **online/offline** toggle from `AppSettingsModel.fakeService`. It is the
  thing later replaced by a real HTTP client behind the same interface.
- **SyncEngine** wires them offline-first.

Flow for a card mutation (move/add/edit/delete) — all optimistic + undoable: the reducer updates
the store immediately; the effect (a) writes LocalStore (durable, instant), (b) **enqueues an
outbound op** (persisted in a `pending_op` table) carrying a unique `OpId`, and (c) kicks the
SyncEngine. The engine pushes queued ops to `RemoteApi.push` (latency/failure apply here):
**`Accepted`** → mark synced, drop from queue; **`Rejected(reason)`** (server validation/conflict)
→ apply the **per-op inverse** (move-back / delete-the-added / restore-the-edited / re-add-the-
deleted — never a whole-board snapshot, so a concurrent bot edit isn't clobbered) + a `SyncToast`;
**`Deferred`** (offline/transient) → keep queued, increment `pendingCount`, retry with backoff /
on reconnect. Reads always come from LocalStore (works offline). A pull (`RemoteApi.pull(since)`)
merges remote changes (incl. the bot's "server-side" edits) into LocalStore (last-write-wins).
`SyncModel` surfaces `online`, `pendingCount`, `inFlight`, `lastSyncedAt`, `lastError`; the board
header shows a sync indicator. Toggling **offline** in Settings, making edits, then back **online**
visibly drains the pending queue — the offline-support showcase.

**Undo / redo.** `UndoModel` holds `past`/`future` **full-board** snapshots (`PersistentList<BoardModel>`,
cap **15**). Snapshots are O(board size) but immutable `Card`/`Column` instances are structurally
shared across snapshots, so unchanged cards aren't copied. Undoable (user): card move / add /
edit / delete. Not undoable: nav, filter, loading flags, session, **bot mutations**. `Undo`/`Redo`
apply via a dedicated `BoardRestored(board)` action (not by overloading `LoadBoardSucceeded`) and
re-persist via the effect path. `BoardClosed` clears the undo stacks.

**Bot collaborator.** A toggleable coroutine (started per account, cancelled on `BoardClosed`/
logout) periodically dispatches **distinct, non-`Undoable`** bot actions (`BotMovedCard`/`BotAddedCard`)
treated as "server-truth" — no optimistic revert, not in the user's undo history. Drives presence +
the activity feed. Proves the UI reacts to externally-originated state with zero component coupling.
`moveCard` reducers are integrity-preserving (remove the id from **all** columns, then insert once)
so a stale `from` from interleaved bot/user moves can't orphan a card.

**Navigation = Redux state.** `NavModel.route` (`BoardList | Board(id) | Profile | Settings`)
+ `openCardId` + `composing: ColumnId?` (drives card-detail **create** mode). Compose renders from
`selectorState { nav.route }`. No external nav library → identical behavior on iOS / Android / Web.
Entering `Board(id)` fires the load effect (fills the pre-declared `BoardModel` slot); leaving
dispatches `BoardClosed`, resetting the board slices to sentinels (memory released).

## 5. State schema

**Immutability is mandatory.** Every Redux model is an immutable `data class`, and **all
collections use `kotlinx.collections.immutable`** (`PersistentList`/`PersistentMap`/`PersistentSet`)
— never the stdlib `List`/`Map`/`Set`. This guarantees the store state is deeply immutable (no
accidental in-place mutation), gives O(1) structural sharing for undo snapshots, and keeps
granular subscriptions' reference-equality checks honest.

Typed id value classes throughout; cards are **normalized** (a `PersistentMap<CardId, Card>`;
columns hold ordered `PersistentList<CardId>`), so a move edits two id-lists and only those two
columns recompose.

```kotlin
import kotlinx.collections.immutable.*
import kotlinx.datetime.Instant

@JvmInline value class AccountId(val v: String)
@JvmInline value class BoardId(val v: String)
@JvmInline value class ColumnId(val v: String)
@JvmInline value class CardId(val v: String)
@JvmInline value class LabelId(val v: String)
@JvmInline value class OpId(val v: String)          // unique per async op (minted at dispatch)

// --- Root AppStore models ---
data class AccountsModel(
    val accounts: PersistentMap<AccountId, AccountSummary> = persistentMapOf(),
    val activeAccountId: AccountId? = null,
)
data class AccountSummary(
    val id: AccountId, val displayName: String, val email: String, val avatarUrl: String,
)
data class AppSettingsModel(
    val theme: Theme = Theme.System,
    val language: String = "en",
    val fakeService: FakeServiceConfig = FakeServiceConfig(),
)
enum class Theme { System, Light, Dark }
data class FakeServiceConfig(
    val latencyMinMs: Int = 300, val latencyMaxMs: Int = 800,
    val failureRate: Float = 0.10f,                 // 0f..1f
    val botEnabled: Boolean = true, val botIntervalMs: Int = 4_000,
    val online: Boolean = true,                     // connectivity toggle: when false, RemoteApi is unreachable
)
data class AuthFlowModel(
    val mode: AuthMode = AuthMode.Login, val inFlight: Boolean = false, val error: String? = null,
)
enum class AuthMode { Login, AddAccount }

// --- Per-account models (ALL declared up front; board slices start empty/sentinel) ---
// Identity (name/email/avatar) is NOT duplicated here — it lives once in CollaboratorsModel
// (which includes self). SessionModel holds only the id + session-only fields. EditProfile
// updates root AccountsModel + CollaboratorsModel so the switcher/cards never go stale.
data class SessionModel(val accountId: AccountId, val bio: String? = null)
data class NavModel(
    val route: Route = Route.BoardList,
    val openCardId: CardId? = null,
    val composing: ColumnId? = null,                // non-null => card-detail in CREATE mode
)
sealed interface Route {
    data object BoardList : Route
    data class Board(val boardId: BoardId) : Route
    data object Profile : Route
    data object Settings : Route
}
data class BoardListModel(
    val boards: PersistentMap<BoardId, BoardSummary> = persistentMapOf(),
    val order: PersistentList<BoardId> = persistentListOf(),
)
data class BoardSummary(
    val id: BoardId, val name: String, val color: Long,
    val cardCount: Int, val doneCount: Int, val updatedAt: Instant,
)
// Resolves assignee/creator/bot avatars within the account store (no root reach-in).
data class CollaboratorsModel(val byId: PersistentMap<AccountId, AccountSummary> = persistentMapOf())

// Always-present slot. board == null is the NotLoaded sentinel (reset by BoardClosed).
data class BoardModel(
    val board: Board? = null,
)
data class Board(
    val boardId: BoardId,
    val columns: PersistentList<Column>,
    val cards: PersistentMap<CardId, Card>,
)
data class Column(
    val id: ColumnId, val title: String,
    val cardIds: PersistentList<CardId>, val wipLimit: Int? = null,
)
data class Card(
    val id: CardId,
    val title: String,
    val description: String,                         // Markdown
    val attachments: PersistentList<Attachment> = persistentListOf(),
    val labels: PersistentList<Label> = persistentListOf(),
    val assigneeId: AccountId? = null,
    val createdBy: AccountId,
    val createdAt: Instant, val updatedAt: Instant,
)
sealed interface Attachment {
    data class Image(val url: String, val alt: String, val width: Int? = null, val height: Int? = null) : Attachment
    data class Link(val url: String, val title: String? = null, val description: String? = null, val imageUrl: String? = null) : Attachment
}
data class Label(val id: LabelId, val name: String, val color: Long)

data class FilterModel(
    val query: String = "",
    val assignee: AccountId? = null,
    val labelIds: PersistentSet<LabelId> = persistentSetOf(),
)
data class UndoModel(
    val past: PersistentList<Board> = persistentListOf(),
    val future: PersistentList<Board> = persistentListOf(),
    val cap: Int = 15,
)
data class SyncModel(
    val inFlight: PersistentSet<CardId> = persistentSetOf(),  // cards with an in-flight op (drives per-card optimistic alpha)
    val pendingCount: Int = 0,                                // queued outbound ops not yet synced
    val online: Boolean = true,                               // one-way projection of AppSettings.fakeService.online (via SyncStatusChanged only)
    val lastSyncedAt: Instant? = null,
    val lastError: String? = null,
)
data class ActivityModel(val entries: PersistentList<ActivityEntry> = persistentListOf())
data class ActivityEntry(
    val id: String, val actorId: AccountId, val summary: String, val timestamp: Instant,
)
```

`Instant` is `kotlinx.datetime.Instant`. All ids are typed value classes. `BoardModel.board == null`
is the NotLoaded sentinel so the `BoardModel` key is always present (required — `ModelState`'s key
set is fixed). `BoardSummary` counts/`updatedAt` for the **open** board are **derived in a selector**
from `BoardModel` (never recomputed in a reducer — reducers stay pure, no `Clock.now()`); the
**board-list** tiles are a DB-aggregate cache refreshed on `LoadBoardListSucceeded`.

## 6. Seeded data (incl. stock profile images)

Auth is **fake**: a small set of seeded accounts, no password — "Continue" simulates latency
and logs in. Images load remotely via Coil 3 (exercising async image loading on every platform
incl. wasmJs). **All asset URLs are keyless, deterministic, and CORS-`*`** — verified, because
`pravatar.cc` sends **no** `Access-Control-Allow-Origin` header and therefore **fails on wasmJs**
(Compose web fetches images in CORS mode). **PNG only** (no SVG) so no `coil-svg` decoder is needed.

| Asset | Scheme | Notes |
|-------|--------|-------|
| User avatars | `https://api.dicebear.com/9.x/avataaars/png?seed={accountId}` | DiceBear, CORS-`*` ✅, distinct illustrated people, CC0, no real-face privacy concern. |
| Bot avatar | `https://api.dicebear.com/9.x/bottts/png?seed=taskflow` | Robot look — visually distinct from humans. |
| Card image attachments | `https://picsum.photos/seed/{cardId}/600/400` | Unsplash-backed, CORS-`*` ✅, deterministic, one 3:2 aspect everywhere. |
| Link-preview thumb | seeded `picsum` URL or `null` → generic glyph | `AttachmentChip` link variant. |

Seeded accounts: `ann` Ann Patterson, `raj` Raj Mehta, `mia` Mia Chen (`@taskflow.dev`); plus a
non-login `bot` "TaskBot" collaborator. Each seeded account starts with **one board, three columns
(To Do / Doing / Done), ~6–8 cards** — including ≥1 with a markdown body, ≥1 image attachment, ≥1
link attachment, ≥1 with labels, and assignees across the owner + bot. Seed timestamps use a
**fixed `Instant` constant** (deterministic for tests); only runtime-created cards use `Clock.System.now()`.

**Offline fallback (always wired):** the Coil `ImageLoader` falls back to **bundled PNG/JPEG** in
`composeResources/files/{avatars,cards}/` (picked by id-hash) on network/error — so the demo is
honest offline and CI screenshot tests never depend on the network. Bundled files are CC0 (DiceBear
export) / Unsplash-license, sourced in the README.

## 7. Screens (7; all Compose Multiplatform, nav = Redux state)

1. **Login / Add account** — pick a seeded account → fake latency → `AccountLoggedIn` →
   create account store in registry. (root: `AuthFlowModel`, `AccountsModel`)
2. **Account switcher** — list logged-in accounts with where each one is ("ann on Sprint
   42", "raj on Settings"); switch sets `activeAccountId`; "Add account"; per-account
   logout disposes that store. (root: `AccountsModel`)
3. **Board list** — account home; grid of board summaries (color, counts, progress,
   updated-at) + New board. Tap → `Nav→Board(id)` → inject models + load effect (skeleton
   shimmer). (account: `BoardListModel`)
4. **Board (kanban)** — the hero screen. Columns + cards + WIP badges + filter ButtonGroup
   + undo/redo + FloatingActionButtonMenu. Adaptive: phone = NavigationBar + paged columns;
   wide = NavigationRail + side-by-side columns + persistent Activity rail.
   (account: `BoardModel`/`FilterModel`/`UndoModel`/`ActivityModel`)
5. **Card detail** — one screen, three **modes**: view (rendered markdown + attachment
   previews + labels + assignee + move-to ButtonGroup), edit, and create. Side sheet on
   wide, full screen on phone. Editor text is local Compose state, committed on Save via
   `AddCard`/`EditCard`. (account: `Nav.openCardId` + `subscribeToModel(BoardModel)`)
6. **Profile / User** — account-scoped header + editable fields + stats + Log out.
   (account: `SessionModel`)
7. **Settings** — theme (Light/Dark/System) + live fake-backend knobs (latency, failure
   rate, bot toggle) writing `AppSettingsModel`; the fake backend reads them live, so you
   can crank latency/failure to *see* loading/error/undo on demand. (root: `AppSettingsModel`)

## 8. Visual design — Material 3 Expressive

- **`MaterialExpressiveTheme`** (experimental, `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`)
  → defaults to `MotionScheme.expressive()` (physics-based springs across all components).
- **Components**: `ButtonGroup` (board toolbar, card move-to), `FloatingActionButtonMenu` +
  items (add card/column/board), shape-morphing buttons.
- **Shape**: large corner radii (cards ~20dp, columns ~18dp); shape morph on press.
- **Typography**: emphasized type set for titles/board names; baseline for body.
- **Color**: dynamic color on Android 12+; a violet/indigo seed scheme elsewhere; full
  light + dark, driven by `AppSettingsModel.theme`.
- **Adaptive layout** via `WindowSizeClass`: NavigationBar (compact) ↔ NavigationRail
  (expanded).

**Animation spec (UX = P0):**

- Card move — `animateItem` slide between columns + spring settle; optimistic-save fade.
- WIP badge — animated count change; turns error-tinted when the limit is exceeded.
- Screen transitions — expressive shared-axis / crossfade per nav route change.
- Loading — skeleton shimmer on board/board-list while the fake backend resolves.
- Account switch — content crossfade as the active store rebinds.

> The exact tokens (colors, type scale, shapes, spring specs, spacing) are produced in the
> **hi-fi spec** step (§11). Reference mockups for all 7 screens exist under
> `.superpowers/brainstorm/<session>/content/` from the brainstorm.

## 9. Modules, platforms, dependencies

```
examples/taskflow/
├── composeApp/   ← KMP + Compose MP; ALL code + UI
│     plugins: convention.control, kotlin("multiplatform"),
│              compose.multiplatform, compose.compiler,
│              com.android.kotlin.multiplatform.library (SDK-gated),
│              app.cash.sqldelight
│     deps: redux-kotlin-bundle-compose, compose.material3 (Expressive),
│           kotlinx-collections-immutable, kotlinx-datetime, kotlinx-coroutines,
│           coil-compose + coil-network-ktor3 + ktor engines, markdown-renderer-m3/-coil3,
│           sqldelight runtime + per-target drivers
│     android via kotlin.androidLibrary { } (AGP 9), NOT bare androidTarget()
│     targets: android · jvm(desktop) ·
│              iosX64/iosArm64/iosSimulatorArm64 (framework) ·
│              wasmJs { browser; binaries.executable() }   (no separate js{} target)
├── androidApp/   ← com.android.application host (MainActivity lives HERE) → App()
└── iosApp/       ← real Xcode project: Info.plist + UIViewControllerRepresentable(MainViewController())
                    + a run-script copying the compose-resources bundle (static framework)
```

- Web = `composeApp` wasmJs browser executable + `index.html` (empty body; `ComposeViewport(document.body)`). Desktop = `compose.desktop { application { ... } }` for the fast dev loop. `commonMain` holds `App()` + all Redux + all screens; platform entry points are logic-free hosts.
- Register modules in `settings.gradle.kts` (gate the `androidApp` include behind SDK presence). The sample is not published → no `apiDump`/`apiCheck`, no mandatory `explicitApi()` KDoc; detekt formatting still applies tree-wide.

**Repo-wide change:** bump `org.jetbrains.compose` **1.10.0 → 1.11.0 (stable)** — its bundled
`material3` carries the Expressive APIs (no separate alpha pin). This forces `./gradlew apiDump`
re-generation on the existing `redux-kotlin-compose*` modules (commit the updated `*.api`).

**Pinned sample dependencies (all verified for wasmJs unless noted):**

| Dep | Coordinate / version |
|-----|----------------------|
| Compose MP plugin | `org.jetbrains.compose` **1.11.0**; Expressive via the `compose.material3` accessor |
| Immutable collections | `org.jetbrains.kotlinx:kotlinx-collections-immutable` **0.5.0-beta01** |
| Date/time | `org.jetbrains.kotlinx:kotlinx-datetime` **0.6.2** |
| Images | `io.coil-kt.coil3:coil-compose` **3.2.0** + `io.coil-kt.coil3:coil-network-ktor3` **3.2.0** |
| Ktor | `io.ktor:*` **3.1.0**; engines: `ktor-client-darwin` (iOS), `ktor-client-java` (jvm), `ktor-client-android` (android); **wasmJs needs no engine** |
| Markdown | `com.mikepenz:multiplatform-markdown-renderer-m3` + `-coil3` **0.4x** (Compose-1.11 build; not 0.39.0) |
| Persistence | `app.cash.sqldelight` **2.3.2** (see §13) |

**Platform-parity rule:** where a library/API is missing on web or iOS, **do not drop the feature
on Android/iOS/jvm** — shim via `expect/actual` (e.g. the DB `DriverFactory`, the Ktor engine,
Android dynamic-color). Android/iOS/jvm always get the full implementation; only the web target
degrades, and only where unavoidable (documented).

## 10. Testing strategy

Matches repo convention (`commonTest` default; `jvmTest` for JVM-only).

- **commonTest (bulk, pure, every target):** routed reducer transitions (dispatched through the
  assembled store, since `on<A>` adaptation is where bugs hide); undo/redo (snapshot, undo, redo,
  cap, bot mutations excluded); effects middleware against an **in-memory `Repository`** + a
  test-scheduler scope (`backgroundScope`/`StandardTestDispatcher`) — success, failure→**per-op
  inverse** revert, retry; **account isolation** (actions on A never touch B; logout removes only
  that store); **board reset-on-leave** (BoardClosed empties the slices; memory released);
  integrity invariant (`cards.keys == ∪ column.cardIds`, no duplicates) after every mutation;
  edge cases — board-close-while-load-in-flight, logout-while-in-flight, bot-during-undo,
  empty-stack undo; `Repository` round-trips against the in-memory SQLite driver.
- **jvmTest (Compose UI, desktop, `compose.uiTest` with a fake no-network `ImageLoader`):** the
  **render-isolation proof** — baseline captured after first `waitForIdle()`, each column under
  `key(colId)` with stable params, assert recomposition deltas (moved columns increment, others
  flat) + a control dispatch proving an unrelated column stays flat; and account-switch restores
  each account's remembered screen.
- Native/iOS-sim + wasmJs DB tests trusted to CI/build per repo convention. Entry points have no logic.

## 11. Process / next steps (intentional deviation from default flow)

The default brainstorm → writing-plans handoff is deliberately interrupted: **before any
implementation plan**, hi-fi visual specs are produced in a dedicated Claude Code session
(design tokens, component specs, per-screen layouts, animation/motion specs, dark mode).
Only after the hi-fi spec is finalized do we write the implementation plan, then implement
(TDD, on a feature branch off master).

## 12. Out of scope (v1) / future

- A **real** network backend + real auth (in scope now: local SQLite persistence **and** a fake
  network sync layer with offline support — see §13; the `RemoteApi` interface is the seam a real
  HTTP client replaces behind the same actions/sync flow).
- Full drag-and-drop (animated tap-to-move chosen instead; the spec-data "dragging" card state is intentionally unused).
- KSP routing-codegen (`@Reduce`) in the sample — possible later enhancement, not v1.
- Room 3 / OPFS web persistence — revisit when `room3` + `androidx.sqlite:sqlite-web` exit alpha (better web durability than sql.js/IndexedDB).

## 13. Persistence (local) & Sync (fake network) — two distinct layers

These are **separate concerns**. The local DB is durable offline storage; the fake network is a
replaceable backend simulation. The effects middleware talks to a `SyncRepository` that composes both.

### 13a. LocalStore — SQLDelight (durable offline cache, no latency)

- **Engine:** SQLDelight **2.3.2** (plugin `app.cash.sqldelight`, `generateAsync = true`). Same
  generated SQL on every target: native SQLite on android/ios/jvm; **sql.js in a Web Worker**
  (IndexedDB-persisted) on wasmJs via `web-worker-driver`.
- **`expect class DriverFactory { suspend fun createDriver(): SqlDriver }`**, one `actual` per source
  set: `AndroidSqliteDriver`, `NativeSqliteDriver` (iosMain, shared across the 3 iOS targets),
  `JdbcSqliteDriver` (jvm/desktop file), `WebWorkerDriver` over the sql.js worker (wasmJs; async
  `Schema.create(driver).await()`). Canonical platform shim — android/ios/jvm get full file-backed
  SQLite; only web differs (IndexedDB durability); nothing degraded.
- **Schema** (normalized, mirrors §5): `account`, `account_nav`, `app_settings`, `board`,
  `board_column`, `card` (`columnId`+`sortIndex`), `attachment` (sealed image/link via `kind`),
  `label`, `card_label`, `activity`, `collaborator`, **plus a `pending_op` outbound sync queue**
  (`opId`, `kind`, payload, `createdAt`, `attempts`) and a `sync_meta` (`lastSyncedAt` cursor). Typed
  ids → TEXT; `Instant` → INTEGER via adapter; `color`/bools → INTEGER. **Ephemeral, never persisted:**
  `UndoModel`, in-flight push set, `AuthFlowModel`, bot presence.
- **`LocalStore`** (suspend, immutable returns) implemented by `SqlDelightLocalStore` — instant
  reads/writes (**no chaos/latency**), multi-row writes in `db.transaction {}`, plus queue ops
  (`enqueue`, `pendingOps`, `markSynced`) and `applyRemote(changes)` (merge).
- **Seed-on-first-run:** `LocalStore.ensureSeeded()` (idempotent, guarded by `SELECT count(*) FROM account`).
- **wasmJs build:** `@cashapp/sqldelight-sqljs-worker` + `sql.js` npm deps + a `webpack.config.d`
  `CopyWebpackPlugin` step copying `sql-wasm.wasm`/worker into the dist; commit the `kotlin-js-store`
  lockfile. Never wire a synchronous driver on wasm (OOM — use the worker driver).

### 13b. RemoteApi — fake network backend (latency, failure, offline; later real)

- **`interface RemoteApi`** (suspend): `push(ops: List<SyncOp>): PushResult`, `pull(since: Instant?): RemotePage`,
  plus auth/board fetch as needed. **This is the seam a real HTTP client replaces** — keep it pure of
  any DB/Compose types.
- **`FakeRemoteApi`** holds an in-memory "server" snapshot (seeded identically) and applies, from
  `AppSettingsModel.fakeService`: configurable **latency** (300–800ms), **failure rate** (transient),
  and the **`online` toggle** (when false, every call throws `OfflineException`). `push` returns
  `PushResult.Accepted` / `Rejected(reason)` (deterministic validation, e.g. move into a full WIP
  column, to demo conflict) / and surfaces transient failures as exceptions → treated as `Deferred`.
  The bot collaborator's "server-side" edits originate here and arrive via `pull`.

### 13c. SyncEngine + SyncRepository (offline-first orchestration)

- **`SyncRepository`** is what the effects middleware calls. Local-first: reads from `LocalStore`;
  a mutation writes `LocalStore` + `enqueue(SyncOp)` immediately (durable, instant), then signals the
  `SyncEngine`.
- **`SyncEngine`** drains `pending_op` to `RemoteApi.push`: `Accepted` → `markSynced` + drop;
  `Rejected` → emit a `CardOpFailed(opId, reason, inverse)` so the reducer applies the **per-op inverse**
  + a `SyncToast`; `Deferred`/`OfflineException` → leave queued, bump `attempts`, retry with backoff /
  on reconnect. After a successful push it `pull`s and `applyRemote`s, updating `lastSyncedAt`. Triggers:
  after each mutation, on the `online` toggle flipping true, on manual refresh, and on a periodic tick.
  Status flows to `SyncModel` (`online`, `pendingCount`, `inFlight`, `lastSyncedAt`, `lastError`).
- **Offline showcase:** toggle offline in Settings → mutations queue (board updates locally, sync badge
  shows N pending) → toggle online → queue drains, badge clears. Proves offline support end-to-end.

### 13d. Tests

In-memory driver (`JdbcSqliteDriver.IN_MEMORY`) backs `LocalStore` round-trip tests. `FakeRemoteApi` +
`SyncEngine` tested under `runTest` virtual time: push Accepted/Rejected/Deferred paths, offline →
queue grows, reconnect → queue drains + pull merges, retry/backoff, and that a `Rejected` push triggers
the inverse revert. Heavy DB tests stay off the wasmJs CI gate (build-only there).

### 13e. Concrete carriers & lifecycle (v3)

- **`SyncOp` codec:** `@Serializable sealed interface SyncOp` (one variant per card mutation: Move/Add/Edit/Delete),
  carrying the forward params, the `OpId`, **and the `InverseOp`** (so a `Rejected` push reconstructs the
  revert from the queued op — the engine does NOT keep a side map). Serialized to `pending_op.payload` (TEXT)
  via `kotlinx-serialization-json` (catalog + `kotlin.plugin.serialization`). Value-class ids need
  `@Serializable` value-class support or a small surrogate.
- **Threading:** every `ConcurrentModelStore` is built with a **main-thread `NotificationContext`**; effects/sync/bot
  run on a background scope and `withContext(Main)` before `store.dispatch` (subscribers write Compose state).
- **Sync perf:** `pull` early-returns on an empty page; `applyRemote` merges **by changed key only** (reuse
  unchanged `Card`/`Column` instances); `SyncStatusChanged` fires only on a real delta; periodic `Refresh`
  ≥ 10 s (`FakeServiceConfig.syncIntervalMs`), keyed to cancel on `BoardClosed`.
- **Process-death rehydration:** persist `activeAccountId` in `app_settings`; on launch the bootstrap effect
  loads it + each account's `account_nav` (route/openCard) into the store so Android process death / web reload
  restore the screen.
- **iOS resources:** the static framework's compose resources are synced by
  `embedAndSignAppleFrameworkForXcode` (no hand-rolled copy script).
- **wasm assets:** `devNpm("copy-webpack-plugin")` + `composeApp/webpack.config.d/sqljs.config.js` copy
  `sql-wasm.wasm` into the dist; commit the root `kotlin-js-store` lockfile.
```
