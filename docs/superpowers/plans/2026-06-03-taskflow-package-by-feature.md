# TaskFlow Package-by-Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize `examples/taskflow` from package-by-layer to package-by-feature, behavior-identical, with a green multiplatform build.

**Architecture:** Single Gradle module (`:examples:taskflow:composeApp`). Move files into 7 `feature.<name>` packages + 4 shared homes (`core`, `infra`, `app`, `ui`). The sealed `Action`/`Undoable` markers move to `core`; concrete action leaves split into each feature (legal across packages, Kotlin 1.5+). `effectsMiddleware` stays whole inside `feature.board`. The hand-written `createConcurrentModelStore { model<M>{ on<T>{} } }` routing DSL and the `activityLogger → undo → effects` middleware order are preserved verbatim.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, SQLDelight, redux-kotlin (concurrent + multimodel + compose-multimodel bundle), detekt 2.0.0-alpha.3 (`explicitApi()` on), kotlin-test.

---

## Refactor discipline (read before any task)

This is a **pure restructure — no logic changes, no new tests.** The regression net is the **existing test suite**; tests migrate alongside the code they cover. Every task is the same shape:

1. `git mv` the listed files to their target directory.
2. Rewrite the `package` declaration in each moved file.
3. Tree-wide, rewrite every `import` of the moved symbols' **old FQN → new FQN** (all source sets: commonMain, androidMain, iosMain, jvmMain, wasmJsMain, commonTest, jvmTest, androidApp). Wildcard imports are permitted by detekt config if a mass-rewrite is easier, but prefer explicit to match existing style.
4. **Gate:** `./gradlew :examples:taskflow:composeApp:compileKotlinJvm` must succeed (catches missed imports).
5. **Gate:** `./gradlew detektAll` must pass (KDoc travels with moved code; only fix formatting the hook can't auto-correct — re-stage if pre-commit rewrites).
6. **Gate:** `./gradlew :examples:taskflow:composeApp:jvmTest` must pass (behavior unchanged).
7. Commit with `refactor(taskflow): …`.

**Path shorthand** — `CM` = `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow`, with `AM`/`IM`/`JM`/`WM` for android/ios/jvm/wasmJs Main and `CT`/`JT` for commonTest/jvmTest. New packages are all-lowercase: `feature.boardlist` (not `boardList`), `feature.collaborators`, etc.

**Mechanical rewrite helper** (illustrative; the implementer may use IDE or sed — the compile gate is the proof):
```bash
# from worktree root; rewrites a single FQN across every kt file under taskflow
grep -rl 'org.reduxkotlin.sample.taskflow.model.AccountId' examples/taskflow --include=*.kt \
  | xargs sed -i '' 's#sample\.taskflow\.model\.AccountId#sample.taskflow.core.AccountId#g'
```

## Authoritative symbol → target-package map

| Old location (file) | Symbols | New package | New dir |
|---|---|---|---|
| `action/Actions.kt` | `Action`, `Undoable` markers | `…core` | `core/Action.kt` |
| `action/Actions.kt` | board mutation + lifecycle + filter + sync + bot leaves + `InverseOp` | `…feature.board` | `feature/board/BoardActions.kt` |
| `action/Actions.kt` | `LoadBoardListRequested/Succeeded/Failed`, `CreateBoard` | `…feature.boardlist` | `feature/boardlist/BoardListActions.kt` |
| `action/Actions.kt` | `Navigate`, `Back`, `EnterEditMode`, `OpenCard`, `CloseCard` | `…app.nav` | `app/nav/NavActions.kt` |
| `action/Actions.kt` | `Undo`, `Redo`, `PushUndo`, `SetUndoModel` | `…feature.undo` | `feature/undo/UndoActions.kt` |
| `action/Actions.kt` | `RecordActivity` | `…feature.activity` | `feature/activity/ActivityActions.kt` |
| `action/Actions.kt` | `EditProfile`, `StartLogin`, `LoginRequested`, `AccountLoggedIn`, `LoginFailed`, `LoadAccounts*`, `SwitchAccount`, `LogoutAccount` | `…feature.account` | `feature/account/AccountActions.kt` |
| `action/Actions.kt` | `SetTheme`, `SetLatency`, `SetFailureRate`, `SetBotEnabled`, `SetOnline` | `…feature.settings` | `feature/settings/SettingsActions.kt` |
| `model/Ids.kt` | all value-class ids | `…core` | `core/Ids.kt` |
| `model/BoardModels.kt` | Board, Column, Card, Attachment, Label, `newBoardColumns` | `…feature.board` | `feature/board/BoardModels.kt` |
| `model/BoardSlice.kt` | FilterModel, SyncModel | `…feature.board` | `feature/board/BoardSliceModels.kt` |
| `model/BoardSlice.kt` | UndoModel | `…feature.undo` | `feature/undo/UndoModel.kt` |
| `model/BoardSlice.kt` | ActivityModel, ActivityEntry | `…feature.activity` | `feature/activity/ActivityModels.kt` |
| `model/RootModels.kt` | AccountsModel, AccountSummary, AuthFlowModel, AuthMode | `…feature.account` | `feature/account/AccountModels.kt` |
| `model/RootModels.kt` | AppSettingsModel, Theme, FakeServiceConfig | `…feature.settings` | `feature/settings/SettingsModels.kt` |
| `model/AccountModels.kt` | SessionModel, AccountDetail | `…feature.account` | merge into `feature/account/AccountModels.kt` |
| `model/AccountModels.kt` | NavModel, Route | `…app.nav` | `app/nav/NavModels.kt` |
| `model/AccountModels.kt` | BoardListModel, BoardSummary | `…feature.boardlist` | `feature/boardlist/BoardListModels.kt` |
| `model/AccountModels.kt` | CollaboratorsModel | `…feature.collaborators` | `feature/collaborators/CollaboratorsModels.kt` |
| `reducer/BoardReducers.kt` | boardReducer, filterReducer, syncReducer, `DEFAULT_BOARD_COLOR`* | `…feature.board` | `feature/board/BoardReducers.kt` |
| `reducer/AccountReducers.kt` | navReducer | `…app.nav` | `app/nav/NavReducer.kt` |
| `reducer/AccountReducers.kt` | sessionReducer | `…feature.account` | `feature/account/AccountReducers.kt` |
| `reducer/AccountReducers.kt` | boardListReducer, `DEFAULT_BOARD_COLOR` | `…feature.boardlist` | `feature/boardlist/BoardListReducer.kt` |
| `reducer/AccountReducers.kt` | collaboratorsReducer | `…feature.collaborators` | `feature/collaborators/CollaboratorsReducer.kt` |
| `reducer/RootReducers.kt` | accountsReducer, authFlowReducer | `…feature.account` | merge into `feature/account/AccountReducers.kt` |
| `reducer/RootReducers.kt` | appSettingsReducer | `…feature.settings` | `feature/settings/SettingsReducer.kt` |
| `reducer/UndoReducers.kt` | undoModelReducer, undoReducer, pushUndo, UndoResult | `…feature.undo` | `feature/undo/UndoReducers.kt` |
| `middleware/EffectsMiddleware.kt` | effectsMiddleware + all handlers | `…feature.board` | `feature/board/EffectsMiddleware.kt` |
| `middleware/UndoMiddleware.kt` | undoMiddleware | `…feature.undo` | `feature/undo/UndoMiddleware.kt` |
| `middleware/ActivityLoggerMiddleware.kt` | activityLoggerMiddleware, `BOT_ACCOUNT_ID` | `…feature.activity` | `feature/activity/ActivityLoggerMiddleware.kt` |
| `middleware/BotCollaborator.kt` | startBot | `…feature.collaborators` | `feature/collaborators/BotCollaborator.kt` |
| `store/AppStore.kt`, `AccountStore.kt`, `AccountRegistry.kt`, `StoreExt.kt` | all | `…app` | `app/` (keep filenames) |
| `data/**`, `db/**`, `data/SeedData.kt`, `util/IdGenerator.kt` | all | `…infra.*` | `infra/data/**`, `infra/db/**`, `infra/SeedData.kt`, `infra/util/IdGenerator.kt` |
| `platform/**` (common + per-platform actuals) | all | `…infra.platform` | `infra/platform/` in every source set |
| `ui/theme/**`, `ui/adaptive/**`, `ui/image/**` | all | `…ui.*` | `ui/theme/**`, `ui/adaptive/**`, `ui/image/**` |
| `ui/Avatar`?, `ui/components/{Avatar,MarkdownView,MarkdownEditor}` | shared widgets | `…ui` | `ui/` |
| `ui/components/*` (feature-specific) | see per-feature tasks | feature pkgs | feature dirs |
| `ui/screens/*` | see per-feature tasks | feature pkgs | feature dirs |
| `ui/{BackHandler,SystemBarAppearance,Locals}.kt` (+ platform actuals) | UI shims/locals | `…ui` | `ui/` in every source set |
| `App.kt` | App + shell + lifecycle composables | `…app` | `app/App.kt` |

`*DEFAULT_BOARD_COLOR` currently lives in `AccountReducers.kt` but is used by `EffectsMiddleware.onCreateBoard` and `boardListReducer`. Home it in `feature.boardlist` (board-creation concern) and import from board's effects. Confirm the single definition compiles for both consumers.

UI component classification (rule: used by exactly one feature → that feature; ≥2 → `ui`):
- **board:** KanbanCard, ColumnHeader, FilterBar, MoveToGroup, AttachmentChip, LabelChip, SyncIndicator, SyncToast, FabMenu, BoardScreen, CardDetailScreen, BoardSelectors
- **boardlist:** BoardListScreen, BoardSummaryCard
- **account:** LoginScreen, SwitcherScreen, ProfileScreen, AccountRow
- **settings:** SettingsScreen, SettingsSlider
- **app.nav:** AdaptiveNav
- **ui (shared):** Avatar, MarkdownView, MarkdownEditor, theme/*, adaptive/WindowSize, image/*, Locals, BackHandler, SystemBarAppearance

---

## Task 0: Confirm green baseline

**Files:** none (verification only).

- [ ] **Step 1: Compile JVM**

Run: `./gradlew :examples:taskflow:composeApp:compileKotlinJvm`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run jvmTest baseline**

Run: `./gradlew :examples:taskflow:composeApp:jvmTest`
Expected: `BUILD SUCCESSFUL`, all suites pass. Record the test count — every later task must keep it identical (tests move, none are added or dropped).

- [ ] **Step 3: Confirm apiCheck N/A**

Run: `ls examples/taskflow/composeApp/api 2>/dev/null || echo "no api dir — apiCheck N/A (convention.control)"`
Expected: "no api dir…". If an `api/` dir unexpectedly exists, flag before proceeding.

No commit (no changes).

---

## Task 1: Create `core` (ids + Action/Undoable markers)

**Files:**
- Move: `CM/model/Ids.kt` → `CM/core/Ids.kt` (package → `…core`)
- Create: `CM/core/Action.kt` — extract the two markers from `CM/action/Actions.kt`:

```kotlin
package org.reduxkotlin.sample.taskflow.core

/** User card mutations only — drives the undo/redo stack. */
public sealed interface Undoable

/** Every concrete action implements Action. */
public sealed interface Action
```

(If the markers are currently non-`public`/no-KDoc, note `explicitApi()` requires both — add the KDoc shown above. Keep them `sealed`.)

- [ ] **Step 1:** `git mv` Ids.kt to `core/`; rewrite its package to `…core`. Delete the `sealed interface Action`/`Undoable` lines from `action/Actions.kt`; create `core/Action.kt` as above; add `import …core.Action` / `…core.Undoable` to the now-orphaned `Actions.kt` (it still defines the leaves implementing them until later tasks).
- [ ] **Step 2:** Rewrite tree-wide imports: `…model.AccountId/BoardId/ColumnId/CardId/LabelId/OpId` → `…core.<Id>`; `…action.Action`/`…action.Undoable` → `…core.Action`/`…core.Undoable`.
- [ ] **Step 3:** Gate — `compileKotlinJvm`. Expected SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. Expected SUCCESS (KDoc present on the markers).
- [ ] **Step 5:** Gate — `jvmTest`. Expected SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract core ids + Action markers"`

---

## Task 2: Create `infra` data/db/util + SeedData

**Files:**
- Move dir `CM/data/` → `CM/infra/data/` (local/, remote/, sync/, `SeedData.kt`) — packages `…data.*` → `…infra.data.*` and `…data.SeedData` → `…infra.SeedData`.
- Move dir `CM/db/` → `CM/infra/db/` — package `…db` → `…infra.db`.
- Move `CM/util/IdGenerator.kt` → `CM/infra/util/IdGenerator.kt` — package `…util` → `…infra.util`.
- Tests move with code: `CT/data/*` → `CT/infra/data/*` (rewrite package); `JT/data/*` → `JT/infra/data/*`; `CT/util/*` → `CT/infra/util/*`.

> SQLDelight generated package: the `.sq` schema lives under `commonMain/sqldelight/org/reduxkotlin/sample/taskflow/db/` and generates `…db.TaskFlowDb`. **Do not move the `.sq` files' package** (generated code stays at `…db`); only the hand-written `db/Adapters.kt` moves to `…infra.db`. Verify `Adapters.kt` imports the generated `…db.*` types after the move.

- [ ] **Step 1:** `git mv` the dirs/files above; rewrite package decls in moved hand-written files.
- [ ] **Step 2:** Rewrite tree-wide imports for every moved symbol's FQN (`…data.*`→`…infra.data.*`, `…db.Adapters`/adapter symbols→`…infra.db.*`, `…util.IdGenerator`→`…infra.util.IdGenerator`, `…SeedData`→`…infra.SeedData`). Leave generated `…db.TaskFlowDb`/queries imports untouched.
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): move data/db/util under infra"`

---

## Task 3: Move platform shims to `infra.platform` (all source sets)

**Files (package `…platform` → `…infra.platform` in EVERY set):**
- `CM/platform/{DriverFactory,DynamicColor,HttpEngine,Ids,Notification}.kt`
- `AM/platform/{AndroidContextHolder,DriverFactory.android,DynamicColor.android,HttpEngine.android,Ids.android,Notification.android}.kt`
- `IM/platform/{DriverFactory.ios,DynamicColor.ios,HttpEngine.ios,Ids.ios,Notification.ios}.kt`
- `JM/platform/{DriverFactory.jvm,DynamicColor.jvm,HttpEngine.jvm,Ids.jvm,Notification.jvm}.kt`
- `WM/platform/{DriverFactory.wasmJs,DynamicColor.wasmJs,HttpEngine.wasmJs,Ids.wasmJs,Notification.wasmJs}.kt`
- `androidApp/.../app/MainActivity.kt` — references `AndroidContextHolder`; update its import.

> `expect`/`actual` must share the identical package; move all together in one commit so each `expect` keeps its `actual`s.

- [ ] **Step 1:** `git mv` every platform file into `infra/platform/` within its source set; rewrite package decls to `…infra.platform`.
- [ ] **Step 2:** Rewrite tree-wide imports `…platform.*` → `…infra.platform.*` (incl. androidApp).
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): move platform shims under infra.platform"`

---

## Task 4: Establish shared `ui` (theme/adaptive/image + shared widgets + UI shims)

**Files:**
- Move `CM/ui/theme/*` → `CM/ui/theme/*` is unchanged in dir but package `…ui.theme` is already correct — **no move needed** (theme/adaptive/image already sit under `…ui.*`). Verify and skip if already `…ui.theme` etc.
- Move shared widgets to top-level `…ui`: `CM/ui/components/{Avatar,MarkdownView,MarkdownEditor}.kt` → `CM/ui/{Avatar,MarkdownView,MarkdownEditor}.kt`, package `…ui.components` → `…ui`.
- `CM/ui/{Locals,BackHandler,SystemBarAppearance}.kt` already package `…ui` — confirm; their per-platform actuals (`AM/IM/JM/WM/ui/{BackHandler,SystemBarAppearance}.kt`) stay package `…ui` — no move, just confirm they remain consistent.

> The bulk of `ui/components/*` and all `ui/screens/*` move in the per-feature tasks (5–11), not here. This task only lifts the 3 cross-feature widgets out of `components/`.

- [ ] **Step 1:** `git mv` the 3 shared widgets up to `ui/`; rewrite their package to `…ui`.
- [ ] **Step 2:** Rewrite tree-wide imports `…ui.components.Avatar/MarkdownView/MarkdownEditor` → `…ui.Avatar/...`.
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): lift cross-feature widgets into ui"`

---

## Task 5: `feature.settings`

**Files (package → `…feature.settings`):**
- Create `feature/settings/SettingsModels.kt` from `model/RootModels.kt` symbols: AppSettingsModel, Theme, FakeServiceConfig.
- Create `feature/settings/SettingsActions.kt` from `action/Actions.kt`: SetTheme, SetLatency, SetFailureRate, SetBotEnabled, SetOnline (delete from Actions.kt; each `: Action` import from `…core`).
- Create `feature/settings/SettingsReducer.kt` from `reducer/RootReducers.kt`: appSettingsReducer.
- Move `CM/ui/screens/SettingsScreen.kt` → `feature/settings/SettingsScreen.kt`.
- Move `CM/ui/components/SettingsSlider.kt` → `feature/settings/SettingsSlider.kt`.

- [ ] **Step 1:** Create the new feature files (cut the listed symbols from their old aggregate files, set package `…feature.settings`, add `import …core.Action`). `git mv` the screen + slider.
- [ ] **Step 2:** Rewrite tree-wide imports of the moved symbols → `…feature.settings.*` (notably `AppStore.kt` for appSettingsReducer; `App.kt`/theme consumers for Theme; effects/settings consumers for SetOnline).
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract feature.settings"`

---

## Task 6: `feature.account` (accounts + auth + session)

**Files (package → `…feature.account`):**
- `feature/account/AccountModels.kt` ← RootModels: AccountsModel, AccountSummary, AuthFlowModel, AuthMode; + AccountModels: SessionModel, AccountDetail.
- `feature/account/AccountActions.kt` ← Actions.kt: EditProfile, StartLogin, LoginRequested, AccountLoggedIn, LoginFailed, LoadAccountsRequested/Succeeded/Failed, SwitchAccount, LogoutAccount.
- `feature/account/AccountReducers.kt` ← RootReducers: accountsReducer, authFlowReducer; + AccountReducers: sessionReducer.
- Move screens `CM/ui/screens/{LoginScreen,SwitcherScreen,ProfileScreen}.kt` → `feature/account/`.
- Move `CM/ui/components/AccountRow.kt` → `feature/account/AccountRow.kt`.
- Tests: `CT/reducer/AccountReducersTest.kt` (the account-related cases) and `JT/ui/AccountSwitchTest.kt` move/retarget to `…feature.account` package. If `AccountReducersTest` covers both account and per-account-board reducers, split only the account-store reducer cases here; leave board/nav cases for their tasks (keep test methods identical, just relocate by ownership). `JT/store/AccountRegistryTest.kt` stays with `app` (Task 13).

> `EditProfile` is cross-cutting (also handled by collaborators in Task 7). It lives in `feature.account` (its primary domain); `collaboratorsReducer` imports it from `…feature.account`.

- [ ] **Step 1:** Create feature files (cut symbols, set package, fix marker imports). `git mv` screens, AccountRow, and the owned tests.
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.account.*` (AppStore for accountsReducer/authFlowReducer; AccountStore for sessionReducer/AccountDetail/SessionModel; App.kt; collaborators reducer for EditProfile; SeedData for AccountSummary/AccountDetail if referenced).
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract feature.account"`

---

## Task 7: `feature.collaborators`

**Files (package → `…feature.collaborators`):**
- `feature/collaborators/CollaboratorsModels.kt` ← AccountModels: CollaboratorsModel.
- `feature/collaborators/CollaboratorsReducer.kt` ← AccountReducers: collaboratorsReducer.
- Move `CM/middleware/BotCollaborator.kt` → `feature/collaborators/BotCollaborator.kt` (startBot).
- `seedCollaborators` currently in `store/AccountStore.kt`: move the function to `feature/collaborators/CollaboratorsSeed.kt` (package `…feature.collaborators`); AccountStore imports it.
- Tests: `JT/.../BotCollaboratorTest.kt` (currently `CT/middleware/BotCollaboratorTest.kt`) → `feature/collaborators/` package.

- [ ] **Step 1:** Create model/reducer/seed files; `git mv` BotCollaborator + its test; set packages; fix imports (`EditProfile` from `…feature.account`, ids from `…core`).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.collaborators.*` (AccountStore for CollaboratorsModel/seedCollaborators/collaboratorsReducer; AccountRegistry for startBot; board screen for CollaboratorsModel reads).
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract feature.collaborators"`

---

## Task 8: `feature.activity`

**Files (package → `…feature.activity`):**
- `feature/activity/ActivityModels.kt` ← BoardSlice: ActivityModel, ActivityEntry.
- `feature/activity/ActivityActions.kt` ← Actions.kt: RecordActivity.
- `feature/activity/ActivityReducer.kt` ← BoardReducers: activityReducer, `ACTIVITY_CAP`.
- Move `CM/middleware/ActivityLoggerMiddleware.kt` → `feature/activity/ActivityLoggerMiddleware.kt` (activityLoggerMiddleware, `BOT_ACCOUNT_ID`).
- Tests: `CT/middleware/ActivityLoggerTest.kt` → `feature/activity/` package.

> `BOT_ACCOUNT_ID` is referenced by the activity logger (to tag bot vs user activity). Keep its single definition here; if collaborators' bot also needs it, import from `…feature.activity` (or, if cleaner, home it in `core` — but only if both consumers genuinely share it; verify usages first and pick one home, no duplication).

- [ ] **Step 1:** Create feature files; `git mv` logger + test; set packages; fix imports (ActivityEntry from self; ids/core).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.activity.*` (AccountStore for activityReducer slot + middleware registration; any `BOT_ACCOUNT_ID` consumer).
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract feature.activity"`

---

## Task 9: `feature.undo`

**Files (package → `…feature.undo`):**
- `feature/undo/UndoModel.kt` ← BoardSlice: UndoModel.
- `feature/undo/UndoActions.kt` ← Actions.kt: Undo, Redo, PushUndo, SetUndoModel.
- `feature/undo/UndoReducers.kt` ← reducer/UndoReducers.kt: undoModelReducer, undoReducer, pushUndo, UndoResult.
- Move `CM/middleware/UndoMiddleware.kt` → `feature/undo/UndoMiddleware.kt`.
- Tests: `CT/middleware/UndoMiddlewareTest.kt` → `feature/undo/`.

> `PushUndo(snapshot: Board)` and `undoReducer(present: Board?)` reference `Board` (now `…feature.board.Board` after Task 11). Until Task 11, `Board` is still at `…model.Board`; import accordingly now, then the Task 11 rewrite updates it. This back-reference (undo → board) is acceptable: undo snapshots the board.

- [ ] **Step 1:** Create feature files; `git mv` middleware + test; set packages; fix imports.
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.undo.*` (AccountStore for UndoModel slot + undoMiddleware registration order; board reducer/`BoardClosed` consumers; any PushUndo dispatcher).
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract feature.undo"`

---

## Task 10: `feature.boardlist`

**Files (package → `…feature.boardlist`):**
- `feature/boardlist/BoardListModels.kt` ← AccountModels: BoardListModel, BoardSummary.
- `feature/boardlist/BoardListActions.kt` ← Actions.kt: LoadBoardListRequested/Succeeded/Failed, CreateBoard.
- `feature/boardlist/BoardListReducer.kt` ← AccountReducers: boardListReducer + `DEFAULT_BOARD_COLOR`.
- Move `CM/ui/screens/BoardListScreen.kt` → `feature/boardlist/BoardListScreen.kt`.
- Move `CM/ui/components/BoardSummaryCard.kt` → `feature/boardlist/BoardSummaryCard.kt`.

- [ ] **Step 1:** Create feature files; `git mv` screen + card; set packages; fix imports (BoardSummary self; ids/core).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.boardlist.*` (AccountStore for BoardListModel slot + boardListReducer; effects middleware for CreateBoard/`DEFAULT_BOARD_COLOR`/LoadBoardList*; App.kt nav for BoardSummary if read).
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract feature.boardlist"`

---

## Task 11: `feature.board` (largest — models, reducers, effects, screens, components)

**Files (package → `…feature.board`):**
- `feature/board/BoardModels.kt` ← model/BoardModels.kt (Board, Column, Card, Attachment, Label, `newBoardColumns`, helpers like `columnById`).
- `feature/board/BoardSliceModels.kt` ← BoardSlice: FilterModel, SyncModel.
- `feature/board/BoardActions.kt` ← Actions.kt: CardMoveRequested, AddCard, EditCard, DeleteCard, CardOpSucceeded, CardOpFailed, InverseOp, BotMovedCard, BotAddedCard, BoardClosed, BoardRestored, StartCreateCard, CancelCreateCard, AddColumn, LoadBoardRequested/Succeeded/Failed, SetFilterQuery, SetFilterAssignee, ToggleFilterLabel, SyncStatusChanged, Refresh. **Actions.kt should now be empty → delete it.**
- `feature/board/BoardReducers.kt` ← reducer/BoardReducers.kt: boardReducer, filterReducer, syncReducer.
- `feature/board/EffectsMiddleware.kt` ← middleware/EffectsMiddleware.kt (whole; preserve `handle` `when` order + `startCollectors`).
- Move screens `CM/ui/screens/{BoardScreen,CardDetailScreen,BoardSelectors}.kt` → `feature/board/`.
- Move components `CM/ui/components/{KanbanCard,ColumnHeader,FilterBar,MoveToGroup,AttachmentChip,LabelChip,SyncIndicator,SyncToast,FabMenu}.kt` → `feature/board/`.
- Tests: `CT/reducer/BoardReducersTest.kt`, `CT/action/ActionsTest.kt`, `JT/middleware/EffectsMiddlewareTest.kt`, `JT/ui/RenderIsolationTest.kt` → `feature/board/`. `CT/reducer/RootReducersTest.kt` cases split by ownership (settings/account cases already moved in their tasks; remaining board cases here — or leave RootReducersTest at `app` if it asserts whole-store wiring; decide by what it imports).

> This task finalizes the sealed-`Action` split. After it, `action/` and `model/` dirs should be empty (removed). `reducer/` and `middleware/` dirs likewise once Task 12–13 finish.

- [ ] **Step 1:** Create all board feature files (cut remaining symbols from Actions.kt/BoardModels.kt/BoardSlice.kt/BoardReducers.kt; move EffectsMiddleware whole). `git mv` screens/components/tests. Set packages; fix imports (ids/core, infra data/sync, ui shared widgets, collaborators model, undo PushUndo, boardlist DEFAULT_BOARD_COLOR).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.board.*` (AccountStore for Board*/Filter/Sync model slots + board/filter/sync reducers + effectsMiddleware registration; undo for Board ref; App.kt for BoardScreen/lifecycle; nav onLoadBoard `NavModel.activeBoardId` already in app.nav). Delete emptied `action/`, `model/` dirs.
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract feature.board + finalize Action split"`

---

## Task 12: `app.nav`

**Files (package → `…app.nav`):**
- `app/nav/NavModels.kt` ← AccountModels: NavModel, Route.
- `app/nav/NavActions.kt` ← (already cut to where?) Actions.kt nav leaves: Navigate, Back, EnterEditMode, OpenCard, CloseCard. (If Task 11 already deleted Actions.kt, these must have been carved here first — sequence: carve nav actions in THIS task BEFORE Task 11 deletes Actions.kt. Reorder if needed: do Task 12's action carve no later than Task 11.)
- `app/nav/NavReducer.kt` ← AccountReducers: navReducer.
- Move `CM/ui/components/AdaptiveNav.kt` → `app/nav/AdaptiveNav.kt`.

> **Sequencing note:** carve the nav action leaves out of `Actions.kt` no later than Task 11 (which deletes the file). Simplest: run Task 12's Step 1 action-carve immediately before Task 11 Step 1, or fold nav-action extraction into Task 11. The plan keeps them separate for clarity; the implementer must ensure no leaves are stranded when `Actions.kt` is deleted.

- [ ] **Step 1:** Create nav files; `git mv` AdaptiveNav; set packages; fix imports (Route used by App.kt routing, board onLoadBoard, screens).
- [ ] **Step 2:** Rewrite tree-wide imports → `…app.nav.*` (App.kt, AccountStore NavModel slot + navReducer, board effects `NavModel.activeBoardId`, screens' Navigate dispatches).
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Commit: `git commit -am "refactor(taskflow): extract app.nav routing"`

---

## Task 13: `app` (shell + store composition)

**Files (package → `…app`):**
- Move `CM/store/{AppStore,AccountStore,AccountRegistry,StoreExt}.kt` → `app/`.
- Move `CM/App.kt` → `app/App.kt`.
- Tests: `JT/store/{AppStoreTest,AccountRegistryTest,BridgeHandshakeSerializeTest}.kt` → `app/` package (these assert whole-store wiring — the composition root's home). `JT/ui/RenderIsolationTest` already moved to board (Task 11) — leave it.

> AppStore/AccountStore are the composition root: they import every feature's reducers/models/middleware. After all features moved, update their imports to the final feature packages. **Verify the per-account middleware order is byte-identical:** `activityLogger → undo → effects` inside the devTools pipeline, and the 9 model-slot declaration order unchanged.

- [ ] **Step 1:** `git mv` store files + App.kt + store tests; set package `…app`.
- [ ] **Step 2:** Rewrite tree-wide imports → `…app.*` (jvmMain/wasmJsMain `main.kt`, iosMain `MainViewController.kt`, androidApp `MainActivity.kt` all reference `App()`/`createAppStore`/registry). Confirm `StoreExt.getModel` consumers resolve.
- [ ] **Step 3:** Gate — `compileKotlinJvm`. SUCCESS.
- [ ] **Step 4:** Gate — `detektAll`. SUCCESS.
- [ ] **Step 5:** Gate — `jvmTest`. SUCCESS, same count.
- [ ] **Step 6:** Verify empty old dirs removed (`action/ model/ reducer/ middleware/ store/ data/ db/ util/ ui/screens/ ui/components/`) — `find CM -type d -empty -delete` equivalent, then confirm none of those package dirs remain.
- [ ] **Step 7:** Commit: `git commit -am "refactor(taskflow): move shell + store composition to app"`

---

## Task 14: Update `examples/taskflow/ARCHITECTURE.md`

**Files:** Modify `examples/taskflow/ARCHITECTURE.md`.

- [ ] **Step 1:** Rewrite the layout description: replace the package-by-layer narrative with the package-by-feature map (mirror this plan's structure: 7 features + core/infra/app/ui). Update §4 module/dep graph framing (still one Gradle module; now feature packages), §5 layered view (note layers now live *within* each feature; cross-cutting infra/core/app/ui called out).
- [ ] **Step 2:** Fix every moved `path:line` reference. Known references to update (re-resolve line numbers post-move):
  - `store/AppStore.kt:40` → `app/AppStore.kt:<new>`
  - `store/AccountStore.kt:123`, `:163` → `app/AccountStore.kt:<new>`
  - `store/AccountRegistry.kt:28` → `app/AccountRegistry.kt:<new>`
  - `action/Actions.kt` → per-feature action files (board/account/etc.)
  - `reducer/AccountReducers.kt:59` (navReducer) → `app/nav/NavReducer.kt:<new>`
  - `middleware/BotCollaborator.kt:37` → `feature/collaborators/BotCollaborator.kt:<new>`
  - `BoardSelectors.kt` → `feature/board/BoardSelectors.kt`
  - `App.kt` refs → `app/App.kt`
  - §18 test refs: BoardReducersTest→feature/board, ActionsTest→feature/board, SyncOpCodecTest→infra/data, FakeRemoteApiTest→infra/data, LocalStoreTest→infra/data, SyncEngineTest→infra/data, OfflineSyncE2ETest→infra/data
  - `db/Adapters.kt` → `infra/db/Adapters.kt`; `TaskFlowDb.sq` unchanged (generated pkg `…db`)
  - `composeApp/build.gradle.kts` unchanged
- [ ] **Step 2b:** Re-verify each cited line number by opening the new file (don't trust mechanical guesses).
- [ ] **Step 3:** Confirm Rules A–H text still accurate; update any rule prose that named an old path.
- [ ] **Step 4:** Commit: `git commit -am "docs(taskflow): update ARCHITECTURE.md for package-by-feature layout"`

---

## Task 15: Full multiplatform build verification

**Files:** none.

- [ ] **Step 1:** Full build (compile all host-buildable targets + all tests + detektAll):

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. This covers jvm/js/wasmJs/android compile + commonTest/jvmTest + detektAll.

- [ ] **Step 2:** Native/host-buildable targets present on this Mac:

Run: `./gradlew :examples:taskflow:composeApp:compileKotlinIosSimulatorArm64 :examples:taskflow:composeApp:compileKotlinIosArm64`
Expected: SUCCESS (compile only; iOS sim *tests* are host-gated — trust CI per CLAUDE.md). If an Xcode SDK error occurs, record it as environmental and note CI will cover it.

- [ ] **Step 3:** Android app shell (if Android SDK present):

Run: `./gradlew :examples:taskflow:androidApp:assembleDebug` (only if `:androidApp` is included — it is conditional on `hasAndroidSdk`). If absent, note it and rely on CI.

- [ ] **Step 4:** Confirm jvmTest count equals the Task 0 baseline (no tests lost in migration). If a count mismatch, investigate before proceeding — a dropped test is a refactor failure.

No commit (verification only; any fix gets its own `fix(taskflow): …` commit).

---

## Task 16: Open PR

**Files:** none.

- [ ] **Step 1:** Push branch: `git push -u origin taskflow-refactor`.
- [ ] **Step 2:** Open PR against `master` via `gh pr create` — title `refactor(taskflow): package-by-feature layout (Phase 0a)`, body summarizing: motivation (Phase 0a canon), the feature/shared map, behavior-preserving guarantee, gates run (build/detekt/jvmTest green; iOS compile; CI for sim tests), and ARCHITECTURE.md update. Link the design spec.

---

## Self-review notes

- **Spec coverage:** every spec target package has a task (core=T1, infra=T2-3, ui=T4, settings=T5, account=T6, collaborators=T7, activity=T8, undo=T9, boardlist=T10, board=T11, app.nav=T12, app=T13); ARCHITECTURE update=T14; gates=T0/each task/T15; deliverables 1-3 covered.
- **Sequencing risk:** the `Actions.kt` split spans T5-T12; T11 deletes the file. **Invariant:** never delete `Actions.kt` until all leaves are carved — T12 nav-action carve must precede T11's delete (flagged in T12). The implementer should treat T11 + T12 as a paired unit or reorder nav-action extraction earlier.
- **Cross-feature back-refs** (acceptable, documented): undo→board (Board snapshot), board→collaborators (CollaboratorsModel reads), board→boardlist (DEFAULT_BOARD_COLOR), collaborators→account (EditProfile), app→all (composition root).
- **No new tests** by design; the existing suite + compiler are the regression net; the jvmTest count is asserted constant across tasks.
- **Package casing:** `boardlist` lowercase (Kotlin convention), deviating from the spec's display name `boardList`.
