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

- **Single store + dynamic model injection/ejection (philosophy A)** organizes each
  account's screens. Boards' working models are injected when a board opens and ejected
  when it closes, bounding memory — redux-kotlin's analogue of Redux's `injectReducer`
  code-splitting pattern, via `ModelState.with` + `combineModelReducers` +
  `Store.replaceReducer`.
- **Multi-store via `StoreRegistry` (philosophy B)** isolates **accounts**. One store per
  logged-in account is the textbook isolation case (Slack workspaces / browser profiles).
  Logging out disposes only that account's store.

### Two-layer structure

**Root AppStore** — one `createConcurrentModelStore`, app-global, always present:

- `AccountsModel` — logged-in accounts (summaries) + `activeAccountId`.
- `AppSettingsModel` — theme, language, and the live fake-service knobs.
- `AuthFlowModel` — login/add-account flow (loading, error).

**`AccountRegistry: StoreRegistry<AccountId, ModelState>`** — one isolated store per
account, created lazily on login via `getOrCreateConcurrentModelStore(accountId)`. Each
account store holds:

- `SessionModel` — that account's profile.
- `NavModel` — that account's current screen (remembered across switches).
- `BoardListModel` — that account's boards.
- *(injected on board open)* `BoardModel`, `FilterModel`, `UndoModel`, `SyncModel`.
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
  store), with memory bounded by ejection.

References: [Redux Store Setup FAQ](https://redux.js.org/faq/store-setup),
[Redux code-splitting](https://redux.js.org/usage/code-splitting),
[microsoft/redux-micro-frontend](https://github.com/microsoft/redux-micro-frontend).

## 4. Data flow

**Middleware pipeline** (applyMiddleware order, outer → inner) on each ConcurrentModelStore:

1. **activityLogger** — humanizes actions into `ActivityModel` (capped list).
2. **undo** — snapshots the board slice before undoable actions; handles `Undo`/`Redo`.
3. **effects (coroutine)** — intercepts `*Requested` actions, launches a coroutine on a
   store-scoped `CoroutineScope`, calls the fake backend, dispatches `*Succeeded`/`*Failed`.
4. **reducer** — `combineModelReducers` over routed `on<Action>` reducers.

Plus `granularSubscriptionsEnhancer` so columns/badges/detail/feed each re-render off
their own slice. Form keystrokes stay in local Compose `remember` state (not the store),
committed via dispatch on submit.

**Async fake backend (simulated latency).** `FakeBackend` is an in-memory repository of
`suspend` functions with configurable latency (default 300–800ms) and a configurable
failure rate (default 10%). Card moves are **optimistic**: the reducer updates the board
immediately, the effects middleware persists, and on `*Failed` the change reverts with a
toast + Retry. Demonstrates Request/Success/Failure action triples and real loading/error UI.

**Undo / redo.** `UndoModel` holds `past[]` / `future[]` snapshots of the board slice
(capped, e.g. 50). Undoable: card move / create / edit / delete. Not undoable: nav, filter,
loading flags, session, bot presence. `Undo`/`Redo` re-persist via the same effect path.

**Bot collaborator.** A toggleable coroutine (started by middleware) periodically dispatches
*normal* actions (move/add a card) as a simulated second user, driving presence + the
activity feed. Proves the UI reacts to externally-originated state with zero component
coupling.

**Navigation = Redux state.** `NavModel.route` (`BoardList | Board(id) | Profile | Settings`)
+ `openCardId`. Compose renders from `selectorState { nav.route }`. No external nav library
→ identical behavior on iOS / Android / Web. Entering a board injects its models + fires the
load effect; leaving ejects them.

## 5. State schema

Typed id value classes throughout; cards are **normalized** (a `Map<CardId, Card>`; columns
hold ordered `List<CardId>`), so a move edits two id-lists and only those two columns recompose.

```kotlin
@JvmInline value class AccountId(val v: String)
@JvmInline value class BoardId(val v: String)
@JvmInline value class ColumnId(val v: String)
@JvmInline value class CardId(val v: String)
@JvmInline value class LabelId(val v: String)

// --- Root AppStore models ---
data class AccountsModel(
    val accounts: Map<AccountId, AccountSummary> = emptyMap(),
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
    val latencyMinMs: Int = 300,
    val latencyMaxMs: Int = 800,
    val failureRate: Float = 0.10f,   // 0f..1f
    val botEnabled: Boolean = true,
    val botIntervalMs: Int = 4_000,
)
data class AuthFlowModel(
    val mode: AuthMode = AuthMode.Login,
    val inFlight: Boolean = false,
    val error: String? = null,
)
enum class AuthMode { Login, AddAccount }

// --- Per-account models ---
data class SessionModel(val profile: AccountDetail)
data class AccountDetail(
    val id: AccountId, val displayName: String, val email: String,
    val avatarUrl: String, val bio: String? = null,
)
data class NavModel(val route: Route = Route.BoardList, val openCardId: CardId? = null)
sealed interface Route {
    data object BoardList : Route
    data class Board(val boardId: BoardId) : Route
    data object Profile : Route
    data object Settings : Route
}
data class BoardListModel(
    val boards: Map<BoardId, BoardSummary> = emptyMap(),
    val order: List<BoardId> = emptyList(),
)
data class BoardSummary(
    val id: BoardId, val name: String, val color: Long,
    val cardCount: Int, val doneCount: Int, val updatedAt: Instant,
)

// injected on board open
data class BoardModel(
    val boardId: BoardId,
    val columns: List<Column>,
    val cards: Map<CardId, Card>,
)
data class Column(
    val id: ColumnId, val title: String,
    val cardIds: List<CardId>, val wipLimit: Int? = null,
)
data class Card(
    val id: CardId,
    val title: String,
    val description: String,                 // Markdown
    val attachments: List<Attachment> = emptyList(),
    val labels: List<Label> = emptyList(),
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
    val labelIds: Set<LabelId> = emptySet(),
)
data class UndoModel(
    val past: List<BoardModel> = emptyList(),
    val future: List<BoardModel> = emptyList(),
    val cap: Int = 50,
)
data class SyncModel(
    val inFlight: Set<String> = emptySet(),  // operation ids
    val lastError: String? = null,
)
data class ActivityModel(val entries: List<ActivityEntry> = emptyList())
data class ActivityEntry(
    val id: String, val actorId: AccountId, val summary: String, val timestamp: Instant,
)
```

`Instant` is `kotlinx.datetime.Instant` (adds a multiplatform `kotlinx-datetime` dependency
to the sample). All ids are typed value classes.

## 6. Seeded data (incl. stock profile images)

Auth is **fake**: a small set of seeded accounts, no password — "Continue" simulates
latency and logs in. Stock profile photos are loaded remotely via Coil 3 (so the sample
also exercises async image loading on every platform, including wasmJs). They can later be
swapped for bundled Compose Resources for fully offline operation.

Seeded accounts (stock avatars via [pravatar.cc](https://pravatar.cc), stable URLs):

| AccountId | Display name  | Email               | avatarUrl                          |
|-----------|---------------|---------------------|------------------------------------|
| `ann`     | Ann Patterson | ann@taskflow.dev    | `https://i.pravatar.cc/240?img=47` |
| `raj`     | Raj Mehta     | raj@taskflow.dev    | `https://i.pravatar.cc/240?img=12` |
| `mia`     | Mia Chen      | mia@taskflow.dev    | `https://i.pravatar.cc/240?img=5`  |

Simulated bot collaborator: `bot` "TaskBot", avatar
`https://api.dicebear.com/9.x/bottts/png?seed=taskflow` (illustrated, distinct from humans).

Seeded card image/link attachments use stable stock sources (e.g.
`https://picsum.photos/seed/<id>/600/360` for images). Each seeded account starts with 1–2
boards of realistic cards (some with markdown bodies, labels, attachments, assignees).

> If pravatar/picsum availability is a concern for CI or offline demos, mirror a handful of
> images into `composeApp` Compose Resources and point the seed data at those instead. The
> data shape does not change.

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
│              compose.multiplatform, compose.compiler
│     deps: redux-kotlin-bundle-compose, kotlinx-datetime,
│           coil3 (+ coil3 network), multiplatform-markdown-renderer
│     targets: androidTarget · jvm(desktop) ·
│              iosX64/iosArm64/iosSimulatorArm64 (framework) ·
│              wasmJs { browser; binaries.executable() }
├── androidApp/   ← com.android.application host → App()
└── iosApp/       ← Xcode project embedding the framework (SwiftUI ComposeView)
```

- Web = `composeApp` wasmJs browser executable + `index.html`. Desktop = `application { Window { App() } }` for the fast dev loop. `commonMain` holds `App()` + all Redux + all screens; platform entry points are logic-free hosts.
- Register modules in `settings.gradle.kts`. The sample is not published → no `apiDump`/`apiCheck`, no mandatory `explicitApi()` KDoc; detekt formatting still applies tree-wide.

**New sample-only dependencies (all must support wasmJs):**

- `org.jetbrains.kotlinx:kotlinx-datetime`
- `io.coil-kt.coil3:coil-compose` + network fetcher (async stock images)
- `com.mikepenz:multiplatform-markdown-renderer` (markdown bodies)
- Material 3 **Expressive** APIs — require a `material3` artifact compatible with the
  repo's Compose 1.10.0.

## 10. Testing strategy

Matches repo convention (`commonTest` default; `jvmTest` for JVM-only).

- **commonTest (bulk, pure, every target):** routed reducer transitions; undo/redo
  middleware (snapshot, undo, redo, cap, non-undoable ignored); effects middleware with a
  fake backend + test dispatcher (success, failure→optimistic revert, retry); **account
  isolation** (actions on account A never touch B; logout disposes only that store); model
  inject/eject (enter/leave board adds/removes models; memory bounded).
- **jvmTest (Compose UI, desktop, `compose.uiTest`):** the **render-isolation proof** (move
  a card → only the two affected columns recompose) and account-switch restores each
  account's screen. Pattern already used by `redux-kotlin-compose`'s jvmTest.
- Native/iOS-sim tests trusted to CI per repo convention. Entry points have no logic.

## 11. Process / next steps (intentional deviation from default flow)

The default brainstorm → writing-plans handoff is deliberately interrupted: **before any
implementation plan**, hi-fi visual specs are produced in a dedicated Claude Code session
(design tokens, component specs, per-screen layouts, animation/motion specs, dark mode).
Only after the hi-fi spec is finalized do we write the implementation plan, then implement
(TDD, on a feature branch off master).

## 12. Out of scope (v1) / future

- Real backend / network sync, real auth, persistence across launches (fake backend +
  in-memory now; the action surface is designed so real implementations slot in behind the
  same actions later).
- Full drag-and-drop (tap-to-move chosen instead).
- Offline-bundled images (remote stock images now; Compose Resources fallback documented).
- KSP routing-codegen (`@Reduce`) in the sample — possible later enhancement, not v1.
```
