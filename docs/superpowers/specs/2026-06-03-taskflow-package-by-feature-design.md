# TaskFlow: Package-by-Layer → Package-by-Feature

**Date:** 2026-06-03
**Phase:** 0a of the redux-kotlin AI integration strategy (prerequisite for the Phase 0 reference set).
**Branch:** `taskflow-refactor` (off latest `origin/master`), worktree-isolated.
**Type:** Behavior-preserving restructure. **No logic changes.** Pure file/package moves + import updates + splitting two aggregate files (`Actions.kt`, `EffectsMiddleware.kt`) along feature lines.

## Why

`examples/taskflow` is the canonical reference implementation for the whole AI-integration strategy. Spec §5.1 prescribes **package-by-feature** as the recommended layout (better modularity + agent context-locality — fewer files, fewer tokens per feature). taskflow currently uses package-by-layer (`model/ action/ reducer/ middleware/ ui/ …`). This refactor makes the canon match the recommendation so the Phase 0 reference guides (esp. the `feature-slice.md` exemplar) can cite it.

## Constraints (from CLAUDE.md)

- **detekt gate** (`./gradlew detektAll`): `explicitApi()` is on — every `public` declaration needs an explicit modifier **and** a KDoc. Moved code keeps its existing KDoc; any newly-extracted public symbol (e.g. per-feature effect handlers, if made public) must carry KDoc. Formatting auto-corrects via pre-commit hook; never `--no-verify`.
- **Conventional Commits**: `refactor(taskflow): …` per migration step.
- **apiCheck N/A**: taskflow is `convention.control` (sample app, not published) — no `api/` dump exists, so `apiCheck` does not gate this module. Confirm during execution.
- **iOS sim tests host-gated** — trust CI; do not block on `iosSimulatorArm64Test` SDK errors locally.

## Current shape (baseline)

Single Gradle module `:examples:taskflow:composeApp` (KMP: jvm, android, js, wasmJs, ios*, plus `:androidApp` shell). **Two stores:**

- **Root** (`AppStore`, 3 slots): `AccountsModel`, `AppSettingsModel`, `AuthFlowModel`. No root middleware.
- **Per-account** (`AccountStore`, 9 slots, owned by `AccountRegistry`): `SessionModel`, `NavModel`, `BoardListModel`, `CollaboratorsModel`, `BoardModel`, `FilterModel`, `UndoModel`, `SyncModel`, `ActivityModel`.

Reducers combined via the hand-written `createConcurrentModelStore { model<M> { on<T> { … } } }` routing DSL — **kept as-is** (no switch to KSP `@Reduce` this pass).

Per-account middleware order (inside the devTools pipeline, **must be preserved exactly**): `activityLogger → undo → effects`.

**Cross-cutting actions** (one action leaf, handled by reducers now living in different feature packages — fine because the `Action`/`Undoable` markers are plain interfaces, see note below):
- `EditProfile` → `accountsReducer` (account) + `sessionReducer` (account) + `collaboratorsReducer` (collaborators)
- `BoardClosed` → `boardReducer` + `filterReducer` + `syncReducer` (board) + `undoModelReducer` (undo)

## Target package map — `org.reduxkotlin.sample.taskflow.*`

7 features under `feature.<name>` + 4 shared homes (`core`, `infra`, `app`, `ui`). Decisions: mid-grain granularity; `core+infra+app+ui` shared scheme; sync concern inside board; `feature.<name>` package segment.

### Features

| Package | Models | Reducers | Middleware/effects | UI | Other |
|---|---|---|---|---|---|
| `feature.board` | BoardModel, FilterModel, SyncModel, Board, Column, Card, Attachment, Label; `newBoardColumns` | boardReducer, filterReducer, syncReducer | **effectsMiddleware** (whole; per-feature effect handlers composed in, order preserved) | BoardScreen, CardDetailScreen, BoardSelectors; KanbanCard, ColumnHeader, FilterBar, MoveToGroup, AttachmentChip, LabelChip, SyncIndicator, SyncToast, FabMenu | InverseOp |
| `feature.boardList` | BoardListModel, BoardSummary | boardListReducer | — | BoardListScreen, BoardSummaryCard | — |
| `feature.undo` | UndoModel | undoModelReducer, undoReducer, `pushUndo`, UndoResult | undoMiddleware | — | — |
| `feature.activity` | ActivityModel, ActivityEntry | activityReducer (`ACTIVITY_CAP`) | activityLoggerMiddleware (`BOT_ACCOUNT_ID`) | — | — |
| `feature.collaborators` | CollaboratorsModel | collaboratorsReducer | BotCollaborator (`startBot`) | — | `seedCollaborators` |
| `feature.account` | AccountsModel, AuthFlowModel, AuthMode, AccountSummary, SessionModel, AccountDetail | accountsReducer, authFlowReducer, sessionReducer | — | LoginScreen, SwitcherScreen, ProfileScreen, AccountRow | — |
| `feature.settings` | AppSettingsModel, Theme, FakeServiceConfig | appSettingsReducer | — | SettingsScreen, SettingsSlider | — |

### Shared homes

| Package | Contents |
|---|---|
| `core` | `Ids.kt` value classes (AccountId, BoardId, ColumnId, CardId, LabelId, OpId); root `Action` / `Undoable` marker interfaces |
| `infra` | `platform/` (6 expect/actual: DriverFactory, DynamicColor, HttpEngine, Ids, Notification, AndroidContextHolder); `db/` (Adapters, SQLDelight schema); `data/local` (LocalStore, SqlDelightLocalStore); `data/remote` (RemoteApi, FakeRemoteApi, PushResult, RemoteChange, SyncCodec, SyncMappers, SyncOp, SyncOpBuilders); `data/sync` (SyncEngine, SyncRepository); `SeedData`; `util/IdGenerator` |
| `app` | `App.kt` shell + routing composables (AppShell, ActiveAccount, BoxWithConstraintsRouting, RouteScreen, lifecycle effects); **`app.nav`** sub-pkg (NavModel, navReducer, Route, AdaptiveNav); store composition (AppStore, AccountStore, AccountRegistry, StoreExt). This is the composition root — it imports every feature's reducers (expected and acceptable). |
| `ui` | `theme/`, `adaptive/WindowSize`, `image/` (ImageLoader, BundledFallback); cross-feature widgets (Avatar, MarkdownView, MarkdownEditor) |

### Revision 1 (post completeness-review): shared domain kernel in `core`

The multi-agent completeness review surfaced that the `data/` layer (`LocalStore`, `SqlDelightLocalStore`, `FakeRemoteApi`, `RemoteChange`, `SyncMappers`, `SyncOpBuilders`, `SyncEngine`, `SyncRepository`) — destined for `infra` — imports domain **entities**, persisted **state value-types**, and **card-mutation actions** from across the app. Homing those in feature packages would make `infra` depend on `feature.board/boardlist/account/settings/activity/app.nav` — a backward layer dependency in the canonical exemplar.

**Resolution (chosen):** a shared **domain kernel** in `core`. `core` holds exactly the types `infra` structurally needs, so `infra → core` only. Features keep their `ModelState` slot models, reducers, effects/middleware, selectors, UI, and feature-specific actions. This is a behavior-preserving placement change (no logic change); the `Action` leaves live across packages (§5.1 holds — now `core` + 6 feature/app packages).

> **Correction (Kotlin sealed rule):** §5.1 and earlier drafts claimed sealed-interface leaves may live across packages in the same module (Kotlin 1.5+). That is **incorrect** — Kotlin 1.5 relaxed sealed subtypes to the same **module AND same package**, not arbitrary packages. To split `Action` leaves across feature packages, the root `Action`/`Undoable` markers are therefore **plain `public interface`s, not `sealed`**. This is the idiomatic package-by-feature choice and is behavior-preserving here: no `when(action)` relies on exhaustiveness (all use an `else`/no-op branch), and `is Undoable` marker checks are unaffected. `InverseOp` **stays `sealed`** because all four of its leaves live together in `core`.

**`core` membership (supersedes the `core` row above):**
- ids: AccountId, BoardId, ColumnId, CardId, LabelId, OpId
- markers: `Action`, `Undoable`
- entities: Board, Column, Card, Attachment, Label
- summaries/detail: BoardSummary, AccountSummary, AccountDetail
- persisted state value-types: NavModel, Route, AppSettingsModel, FakeServiceConfig, Theme, ActivityEntry
- card-mutation / sync-contract actions (the sync engine builds + replays them): CardMoveRequested, AddCard, EditCard, DeleteCard, CardOpSucceeded, CardOpFailed, InverseOp (+ 4 nested leaves)

**Feature/app models after the kernel split (supersede the table rows above):**
- `feature.board`: BoardModel, FilterModel, SyncModel + `newBoardColumns`/`columnById` helpers + non-kernel board actions (BotMovedCard, BotAddedCard, BoardClosed, BoardRestored, StartCreateCard, CancelCreateCard, AddColumn, LoadBoardRequested/Succeeded/Failed, SetFilterQuery, SetFilterAssignee, ToggleFilterLabel, SyncStatusChanged, Refresh) + boardReducer, filterReducer, syncReducer + effectsMiddleware + selectors + UI
- `feature.boardlist`: BoardListModel + boardListReducer (`DEFAULT_BOARD_COLOR`) + LoadBoardList* / CreateBoard actions + UI
- `feature.undo`: UndoModel + reducers + undoMiddleware + Undo/Redo/PushUndo/SetUndoModel
- `feature.activity`: ActivityModel + activityReducer (`ACTIVITY_CAP`) + activityLoggerMiddleware (`BOT_ACCOUNT_ID`) + RecordActivity
- `feature.collaborators`: CollaboratorsModel + collaboratorsReducer + BotCollaborator + `seedCollaborators`
- `feature.account`: AccountsModel, AuthFlowModel, AuthMode, SessionModel + accountsReducer/authFlowReducer/sessionReducer + auth/account actions + UI
- `feature.settings`: AppSettingsModel-reducer only (model lives in core) + Set* settings actions + UI
- `app.nav`: navReducer + Navigate/Back/EnterEditMode/OpenCard/CloseCard + AdaptiveNav (NavModel/Route live in core)

`infra` now depends only on `core`. `app` remains the composition root (depends on everything). The plan (`docs/superpowers/plans/2026-06-03-taskflow-package-by-feature.md`) carries the executable per-task mapping reflecting this revision.

### File splits

- **`action/Actions.kt`** (single file) → per-feature action files. Root `Action` + `Undoable` markers move to `core` as **plain interfaces** (see the sealed-rule correction above). Each feature owns its leaves (card mutations + InverseOp → core; other board leaves → board; nav actions → app.nav; etc.).
- **`middleware/EffectsMiddleware.kt`** → `effectsMiddleware` stays as the single composed middleware (Rule E = discipline, not one file). Its per-action handlers (`onMove/onAdd/onEdit/onDelete/onLoadBoard/onLoadBoardList/onCreateBoard/onAddColumn`) stay co-located with board. The `handle(...)` routing `when` and collector-start logic are preserved verbatim. Lives in `feature.board`.
- **`model/RootModels.kt`** → account (AccountsModel, AuthFlowModel, AuthMode, AccountSummary) + settings (AppSettingsModel, Theme, FakeServiceConfig).
- **`model/AccountModels.kt`** → account (SessionModel, AccountDetail) + app.nav (NavModel, Route) + boardList (BoardListModel, BoardSummary) + collaborators (CollaboratorsModel).
- **`model/BoardSlice.kt`** → board (FilterModel, SyncModel) + undo (UndoModel) + activity (ActivityModel, ActivityEntry).
- **`model/BoardModels.kt`** → board (intact).

UI components: feature-specific widgets migrate into their feature; widgets used by ≥2 features stay in `ui/`. The classification above is the authoritative split; resolve any ambiguity at migration time by "used by exactly one feature → that feature, else ui".

## Rules A–H preservation (must stay intact)

- **C (render isolation):** selectors (`BoardSelectors`) + narrow-slice bindings stay co-located with board; no widening of reads.
- **D (identity split):** `EditProfile` continues to fan to accounts + session + collaborators across their new packages.
- **E (off-main effects):** `effectsMiddleware` kept whole; handler composition + the `activityLogger → undo → effects` registration order unchanged.
- **F (delta-only status):** `SyncEngine` (infra) untouched.
- **G (mint at the edge):** `IdGenerator`/`LocalClock` stay in infra; ids/timestamps minted at dispatch sites, never in reducers.
- **H (single inset point):** shell root inset application unchanged in `app`.

## Build gates (run after each migration step, not just at the end)

1. `./gradlew :examples:taskflow:composeApp:compileKotlinJvm` — fast compile check after each feature move.
2. `./gradlew detektAll` — lint/KDoc/explicitApi gate.
3. `./gradlew build` — full: all host-buildable targets + tests + detektAll.
4. `jvmTest` must pass (reducer/integration/Compose-UI suites). iOS sim is host-gated — trust CI.

Migrate one feature at a time (independently compilable moves) so a break localizes to the step that introduced it.

## Deliverables

1. taskflow reorganized package-by-feature; green `build`; behavior identical.
2. `examples/taskflow/ARCHITECTURE.md` updated to describe the feature-based layout — fix §4 module/dep graph framing, §5 layered view, and **every `path:line` file reference that moved** (inventory: `store/AppStore.kt:40`, `store/AccountStore.kt:123` & `:163`, `store/AccountRegistry.kt:28`, `action/Actions.kt`, `reducer/AccountReducers.kt:59`, `middleware/BotCollaborator.kt:37`, `BoardSelectors.kt`, `App.kt` refs, all §18 test refs, `db/Adapters.kt`, `TaskFlowDb.sq`, `composeApp/build.gradle.kts`).
3. Conventional-commit history; work in worktree; branched from latest master.

## Out of scope

- KSP `@Reduce` migration (keep hand-written routing DSL).
- Any behavior, API-surface, or dependency-graph change.
- Splitting `composeApp` into multiple Gradle modules — this is package-by-feature within one module.

## Next

After approval → `writing-plans` for the step-by-step migration order, then execute with the build-green gate between steps. On completion, report back to resume Phase 0 (author `feature-slice.md` citing the new layout).
