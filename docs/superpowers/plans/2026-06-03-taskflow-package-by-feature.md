# TaskFlow Package-by-Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize `examples/taskflow` from package-by-layer to package-by-feature, behavior-identical, with a green multiplatform build.

**Architecture:** Single Gradle module (`:examples:taskflow:composeApp`). 7 `feature.<name>` packages + 4 shared homes (`core`, `infra`, `app`, `ui`). **Revision 1 (from the completeness review):** a shared **domain kernel** lives in `core` — the domain entities, persisted state value-types, and card-mutation/sync-contract actions that the `data/` (→`infra`) layer structurally needs. This keeps `infra → core` only (no backward `infra → feature` edge). Features keep their `ModelState` slot models, reducers, effects/middleware, selectors, UI, and feature-specific actions. The sealed `Action`/`Undoable` markers move to `core`; concrete leaves split across `core` + 6 feature/app packages (legal Kotlin 1.5+). `effectsMiddleware` stays whole in `feature.board`. The `createConcurrentModelStore { model<M>{ on<T>{} } }` routing DSL and the `activityLogger → undo → effects` middleware order are preserved verbatim.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, SQLDelight, redux-kotlin (concurrent + multimodel + compose-multimodel bundle), detekt 2.0.0-alpha.3 (`explicitApi()` on), kotlin-test.

---

## Refactor discipline (read before any task)

Pure restructure — **no logic changes, no new tests.** The regression net is the **existing test suite**; tests migrate alongside the code they cover. Every task is the same shape:

1. `git mv` the listed files to their target directory.
2. Rewrite each moved file's `package` declaration.
3. Tree-wide, rewrite every `import` of the moved symbols' **old FQN → new FQN** across ALL source sets (commonMain, androidMain, iosMain, jvmMain, wasmJsMain, commonTest, jvmTest, androidApp).
4. **Gate — compile:** `./gradlew :examples:taskflow:composeApp:compileKotlinJvm` SUCCESS (catches missed imports).
5. **Gate — lint:** `./gradlew detektAll` passes (KDoc travels with moved code; let the pre-commit hook auto-correct formatting and re-stage if it rewrites).
6. **Gate — test:** `./gradlew :examples:taskflow:composeApp:jvmTest` SUCCESS, **@Test count unchanged** (see Task 0 baseline).
7. Commit `refactor(taskflow): …`.

**Gradle vs filesystem:** `:examples:taskflow:composeApp` is the Gradle path. Filesystem shorthand below: `CM`=`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow`; `AM`/`IM`/`JM`/`WM`=android/ios/jvm/wasmJsMain; `CT`/`JT`=commonTest/jvmTest (same package suffix). Packages are all-lowercase: `feature.boardlist` (not `boardList`).

**Mechanical rewrite helper** (illustrative; IDE refactor or sed both fine — the compile gate is the proof). macOS `sed` needs the `''` after `-i`:
```bash
grep -rl 'sample.taskflow.model.Board' examples/taskflow --include=*.kt \
  | xargs sed -i '' 's#sample\.taskflow\.model\.Board#sample.taskflow.core.Board#g'
```

## Authoritative symbol → target-package map

### `core` (domain kernel — everything `infra` needs)
| Old (file) | Symbols | New file |
|---|---|---|
| `model/Ids.kt` | AccountId, BoardId, ColumnId, CardId, LabelId, OpId | `core/Ids.kt` |
| `action/Actions.kt` | `Action`, `Undoable` markers | `core/Action.kt` |
| `model/BoardModels.kt` | Board, Column, Card, Attachment, Label (entities only) | `core/BoardEntities.kt` |
| `model/AccountModels.kt` | BoardSummary, AccountDetail, NavModel, Route | `core/AccountEntities.kt` |
| `model/RootModels.kt` | AccountSummary, AppSettingsModel, FakeServiceConfig, Theme | `core/RootEntities.kt` |
| `model/BoardSlice.kt` | ActivityEntry | `core/ActivityEntry.kt` |
| `action/Actions.kt` | CardMoveRequested, AddCard, EditCard, DeleteCard, CardOpSucceeded, CardOpFailed, InverseOp (+ nested MoveBack, DeleteAdded, RestoreEdited, ReAddDeleted) | `core/CardActions.kt` |

### Features (state-slot models + logic + UI + non-kernel actions)
| Package | Models (slots) | Reducers | Middleware/effects | Actions (non-kernel) | UI |
|---|---|---|---|---|---|
| `feature.board` | BoardModel, FilterModel, SyncModel; `newBoardColumns`,`columnById` helpers | boardReducer, filterReducer, syncReducer | **effectsMiddleware** (whole) | BotMovedCard, BotAddedCard, BoardClosed, BoardRestored, StartCreateCard, CancelCreateCard, AddColumn, LoadBoardRequested/Succeeded/Failed, SetFilterQuery, SetFilterAssignee, ToggleFilterLabel, SyncStatusChanged, Refresh | BoardScreen, CardDetailScreen, BoardSelectors; KanbanCard, ColumnHeader, FilterBar, MoveToGroup, AttachmentChip, LabelChip, SyncIndicator, SyncToast, FabMenu |
| `feature.boardlist` | BoardListModel | boardListReducer (`DEFAULT_BOARD_COLOR`) | — | LoadBoardListRequested/Succeeded/Failed, CreateBoard | BoardListScreen, BoardSummaryCard |
| `feature.undo` | UndoModel | undoModelReducer, undoReducer, `pushUndo`, UndoResult | undoMiddleware | Undo, Redo, PushUndo, SetUndoModel | — |
| `feature.activity` | ActivityModel | activityReducer (`ACTIVITY_CAP`) | activityLoggerMiddleware (`BOT_ACCOUNT_ID`) | RecordActivity | — |
| `feature.collaborators` | CollaboratorsModel | collaboratorsReducer | BotCollaborator (`startBot`) | — | — |
| `feature.account` | AccountsModel, AuthFlowModel, AuthMode, SessionModel | accountsReducer, authFlowReducer, sessionReducer | — | EditProfile, StartLogin, LoginRequested, AccountLoggedIn, LoginFailed, LoadAccountsRequested/Succeeded/Failed, SwitchAccount, LogoutAccount | LoginScreen, SwitcherScreen, ProfileScreen, AccountRow |
| `feature.settings` | — (AppSettingsModel/Theme/FakeServiceConfig in core) | appSettingsReducer | — | SetTheme, SetLatency, SetFailureRate, SetBotEnabled, SetOnline | SettingsScreen, SettingsSlider |

### Other shared homes
| Package | Contents |
|---|---|
| `infra` | `infra.platform` (expect/actual: DriverFactory, DynamicColor, HttpEngine, Ids, Notification, AndroidContextHolder); `infra.db` (Adapters.kt only — generated `…db.*` stays); `infra.data.local`, `infra.data.remote`, `infra.data.sync`; `infra.SeedData`; `infra.util.IdGenerator`. **Depends only on `core`.** |
| `app` | `app.nav` (navReducer, nav actions, AdaptiveNav — NavModel/Route in core); `app` (App.kt shell + lifecycle; AppStore, AccountStore, AccountRegistry, StoreExt). Composition root — imports every feature. |
| `ui` | `ui.theme`, `ui.adaptive`, `ui.image`; shared widgets Avatar, MarkdownView, MarkdownEditor; UI shims Locals, BackHandler, SystemBarAppearance (already `…ui`). |

### Test destinations (20 files; @Test counts must stay constant)
- → `core`: `model/IdsTest.kt`; `action/ActionsTest.kt` (asserts kernel card-action/InverseOp shapes — verify and home in `core`, importing any non-kernel actions cross-package).
- → `infra.data`: `data/{FakeRemoteApiTest,SyncOpCodecTest}.kt` (CT), `data/{LocalStoreTest,OfflineSyncE2ETest,SyncEngineTest}.kt` (JT). → `infra.util`: `util/IdGeneratorTest.kt` (CT).
- → `feature.activity`: `middleware/ActivityLoggerTest.kt` (CT). → `feature.collaborators`: `middleware/BotCollaboratorTest.kt` (CT). → `feature.undo`: `middleware/UndoMiddlewareTest.kt` (CT). → `feature.board`: `reducer/BoardReducersTest.kt` (CT), `middleware/EffectsMiddlewareTest.kt` (JT), `ui/RenderIsolationTest.kt` (JT). → `feature.account`: `ui/AccountSwitchTest.kt` (JT).
- **Cross-package tests kept whole** (moved late, import distributed reducers): `reducer/AccountReducersTest.kt` (CT) → `feature.account` in Task 12 (imports navReducer@app.nav, boardListReducer@boardlist, collaboratorsReducer@collaborators, sessionReducer@account). `reducer/RootReducersTest.kt` (CT), `store/AppStoreTest.kt` (CT), `store/{AccountRegistryTest,BridgeHandshakeSerializeTest}.kt` (JT) → `app` in Task 13.

> **No method-level test splitting.** Keeping cross-cutting test files whole (importing reducers across packages) avoids losing/duplicating `@Test` methods. A test importing several packages is fine.

---

## Task 0: Confirm green baseline + record invariants

**Files:** none.

- [ ] **Step 1: Compile.** Run `./gradlew :examples:taskflow:composeApp:compileKotlinJvm` → `BUILD SUCCESSFUL`.
- [ ] **Step 2: Test + record @Test counts.** Run `./gradlew :examples:taskflow:composeApp:jvmTest` → SUCCESS. Then record the baseline counts (must stay constant every task):
```bash
B=examples/taskflow/composeApp/src
echo "commonTest @Test:" $(grep -rho '@Test' $B/commonTest | wc -l)   # expect 147
echo "jvmTest @Test:"    $(grep -rho '@Test' $B/jvmTest | wc -l)      # expect 31
```
- [ ] **Step 3: Confirm apiCheck N/A.** Run `ls examples/taskflow/composeApp/api 2>/dev/null || echo "no api dir — apiCheck N/A"`. Rationale: `composeApp` uses `convention.control` (no binary-compatibility plugin); `apiCheck`/`apiDump` apply only to `library-mpp` modules. If an `api/` dir unexpectedly exists, stop and flag.

No commit.

---

## Task 1: `core` domain kernel

**Files:**
- Move `CM/model/Ids.kt` → `CM/core/Ids.kt` (pkg `…core`).
- Create `CM/core/Action.kt` with the two markers (KDoc required — `explicitApi()`):
```kotlin
package org.reduxkotlin.sample.taskflow.core

/** User card mutations only — drives the undo/redo stack. */
public sealed interface Undoable

/** Every concrete action implements Action. */
public sealed interface Action
```
- Create `CM/core/BoardEntities.kt` ← cut Board, Column, Card, Attachment, Label from `model/BoardModels.kt` (leave `newBoardColumns`,`columnById` and any `BoardModel` slot in `BoardModels.kt` for now — they go to feature.board in Task 11).
- Create `CM/core/AccountEntities.kt` ← cut BoardSummary, AccountDetail, NavModel, Route from `model/AccountModels.kt`.
- Create `CM/core/RootEntities.kt` ← cut AccountSummary, AppSettingsModel, FakeServiceConfig, Theme from `model/RootModels.kt`.
- Create `CM/core/ActivityEntry.kt` ← cut ActivityEntry from `model/BoardSlice.kt`.
- Create `CM/core/CardActions.kt` ← cut CardMoveRequested, AddCard, EditCard, DeleteCard, CardOpSucceeded, CardOpFailed, and `InverseOp` + its 4 nested leaves (MoveBack, DeleteAdded, RestoreEdited, ReAddDeleted) from `action/Actions.kt`. They keep `: Action`/`: Action, Undoable` — add `import …core` is unneeded (same package now).
- Move test `CT/model/IdsTest.kt` → `CT/core/IdsTest.kt` (pkg `…core`).

- [ ] **Step 1:** Create the `core/` files by cutting the listed symbols; `git mv` Ids.kt + IdsTest.kt. Delete the moved symbols from their old files. In the now-thinner `action/Actions.kt`, add `import org.reduxkotlin.sample.taskflow.core.Action` and `…core.Undoable` (its remaining leaves still implement them).
- [ ] **Step 2:** Rewrite tree-wide imports → `…core.*` for: all 6 ids; `Action`/`Undoable`; Board/Column/Card/Attachment/Label; BoardSummary/AccountDetail/NavModel/Route; AccountSummary/AppSettingsModel/FakeServiceConfig/Theme; ActivityEntry; CardMoveRequested/AddCard/EditCard/DeleteCard/CardOpSucceeded/CardOpFailed/InverseOp. This touches nearly every file (models, reducers, middleware, data, store, ui, App, tests).
- [ ] **Step 3:** Gate compile → SUCCESS. (M5 check: `grep -rn 'sealed interface InverseOp' CM` returns only `core/CardActions.kt`.)
- [ ] **Step 4:** Gate detekt → SUCCESS (markers carry KDoc).
- [ ] **Step 5:** Gate jvmTest → SUCCESS, counts 147/31.
- [ ] **Step 6:** Commit `refactor(taskflow): establish core domain kernel`.

---

## Task 2: `infra` data / db / util / SeedData (now core-only deps)

**Files (pkg `…data.*`→`…infra.data.*`, `…db`→`…infra.db`, `…util`→`…infra.util`, `…SeedData`→`…infra.SeedData`):**
- Move dirs `CM/data/local`, `CM/data/remote`, `CM/data/sync` → under `CM/infra/data/`; move `CM/data/SeedData.kt` → `CM/infra/SeedData.kt`.
- Move **only** the hand-written `CM/db/Adapters.kt` → `CM/infra/db/Adapters.kt`. **Do NOT move** the `.sq` files under `commonMain/sqldelight/org/reduxkotlin/sample/taskflow/db/` — they generate into package `…db` and must stay. After the move, add to `Adapters.kt`: `import org.reduxkotlin.sample.taskflow.db.*` (it loses same-package access to the generated `Account_nav, Activity, App_settings, Attachment, Board, Board_column, Card, Card_label, Collaborator, Label, Pending_op, Sync_meta, TaskFlowDb`). **Watch the name clash:** generated `db.Board`/`db.Card`/`db.Attachment`/`db.Label`/`db.Column` vs `core.Board` etc. — if both are imported in a file, alias the generated ones (e.g. `import org.reduxkotlin.sample.taskflow.db.Card as CardRow`) or fully-qualify; preserve existing aliasing in `Adapters.kt`/`SqlDelightLocalStore.kt`/`SyncMappers.kt`.
- Move `CM/util/IdGenerator.kt` → `CM/infra/util/IdGenerator.kt`.
- Move tests: `CT/data/{FakeRemoteApiTest,SyncOpCodecTest}.kt` → `CT/infra/data/`; `JT/data/{LocalStoreTest,OfflineSyncE2ETest,SyncEngineTest}.kt` → `JT/infra/data/`; `CT/util/IdGeneratorTest.kt` → `CT/infra/util/`. Rewrite their package decls + imports.

- [ ] **Step 1:** `git mv` the dirs/files + tests; rewrite package decls; add the generated-`…db.*` import (with aliases) to `Adapters.kt`.
- [ ] **Step 2:** Rewrite tree-wide imports `…data.*`→`…infra.data.*`, `…db.Adapters`+adapter symbols→`…infra.db.*`, `…util.IdGenerator`→`…infra.util.IdGenerator`, `…SeedData`→`…infra.SeedData`. Leave generated `…db.TaskFlowDb`/query/table imports untouched (still `…db`).
- [ ] **Step 3:** Gate compile → SUCCESS. (Verify no `import …feature.` appears in any `infra/` file — infra must depend only on core: `grep -rn 'sample.taskflow.feature' CM/infra IM/.. AM/..` should be empty.)
- [ ] **Step 4:** Gate detekt → SUCCESS.
- [ ] **Step 5:** Gate jvmTest → SUCCESS, 147/31.
- [ ] **Step 6:** Commit `refactor(taskflow): move data/db/util under infra`.

---

## Task 3: `infra.platform` shims (all source sets)

**Files (pkg `…platform`→`…infra.platform` in EVERY set; move expect + all actuals together):**
- `CM/platform/{DriverFactory,DynamicColor,HttpEngine,Ids,Notification}.kt`
- `AM/platform/{AndroidContextHolder,DriverFactory.android,DynamicColor.android,HttpEngine.android,Ids.android,Notification.android}.kt`
- `IM/platform/{DriverFactory.ios,DynamicColor.ios,HttpEngine.ios,Ids.ios,Notification.ios}.kt`
- `JM/platform/{DriverFactory.jvm,DynamicColor.jvm,HttpEngine.jvm,Ids.jvm,Notification.jvm}.kt`
- `WM/platform/{DriverFactory.wasmJs,DynamicColor.wasmJs,HttpEngine.wasmJs,Ids.wasmJs,Notification.wasmJs}.kt`
- `androidApp/src/main/kotlin/org/reduxkotlin/sample/taskflow/app/MainActivity.kt` — references `AndroidContextHolder`; update its import.

> **Do NOT move** `ui/BackHandler.kt` / `ui/SystemBarAppearance.kt` here — those UI shims stay in `…ui` (Task 4). Only `platform/*` moves.

- [ ] **Step 1:** `git mv` every `platform/*` file into `infra/platform/` within its source set; rewrite package decls.
- [ ] **Step 2:** Rewrite tree-wide imports `…platform.*`→`…infra.platform.*` (incl. androidApp `MainActivity.kt`).
- [ ] **Step 3:** Gate compile → SUCCESS.
- [ ] **Step 4:** Gate detekt → SUCCESS.
- [ ] **Step 5:** Gate jvmTest → SUCCESS, 147/31.
- [ ] **Step 6:** Commit `refactor(taskflow): move platform shims under infra.platform`.

---

## Task 4: shared `ui` (lift cross-feature widgets)

**Files:**
- `CM/ui/components/{Avatar,MarkdownView,MarkdownEditor}.kt` → `CM/ui/{Avatar,MarkdownView,MarkdownEditor}.kt` (pkg `…ui.components`→`…ui`).
- **Confirm-only (no move):** `ui/theme/*`, `ui/adaptive/WindowSize.kt`, `ui/image/*` already sit at `…ui.*`. `ui/Locals.kt` is a CompositionLocal holder (no expect/actual) — leave at `…ui`. `ui/{BackHandler,SystemBarAppearance}.kt` (expect in CM, actuals in AM/IM/JM/WM) are UI shims — leave at `…ui` in all sets. The remaining `ui/components/*` and all `ui/screens/*` move in the per-feature tasks.

- [ ] **Step 1:** `git mv` the 3 shared widgets up to `ui/`; rewrite their package to `…ui`.
- [ ] **Step 2:** Rewrite tree-wide imports `…ui.components.{Avatar,MarkdownView,MarkdownEditor}`→`…ui.…`.
- [ ] **Step 3–5:** Gates compile / detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): lift cross-feature widgets into ui`.

---

## Task 5: `feature.settings`

**Files (pkg `…feature.settings`):** (AppSettingsModel/Theme/FakeServiceConfig already in `core`.)
- Create `feature/settings/SettingsActions.kt` ← cut from `action/Actions.kt`: SetTheme, SetLatency, SetFailureRate, SetBotEnabled, SetOnline (import `…core.Action`).
- Create `feature/settings/SettingsReducer.kt` ← cut appSettingsReducer from `reducer/RootReducers.kt`.
- Move `CM/ui/screens/SettingsScreen.kt` → `feature/settings/SettingsScreen.kt`; `CM/ui/components/SettingsSlider.kt` → `feature/settings/SettingsSlider.kt`.

- [ ] **Step 1:** Create feature files; `git mv` screen + slider; set packages; fix imports (Theme/AppSettingsModel from `…core`).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.settings.*` for the 5 actions + appSettingsReducer. Consumers: `store/AppStore.kt` (appSettingsReducer), `feature/board/EffectsMiddleware` (SetOnline — still at old path until Task 11; will be re-pointed there), settings UI.
- [ ] **Step 3–5:** Gates compile / detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): extract feature.settings`.

---

## Task 6: `feature.account` (accounts + auth + session)

**Files (pkg `…feature.account`):** (AccountSummary/AccountDetail already in `core`.)
- Create `feature/account/AccountModels.kt` ← cut AccountsModel, AuthFlowModel, AuthMode from `model/RootModels.kt`; SessionModel from `model/AccountModels.kt`.
- Create `feature/account/AccountActions.kt` ← cut from `action/Actions.kt`: EditProfile, StartLogin, LoginRequested, AccountLoggedIn, LoginFailed, LoadAccountsRequested/Succeeded/Failed, SwitchAccount, LogoutAccount.
- Create `feature/account/AccountReducers.kt` ← cut accountsReducer, authFlowReducer from `reducer/RootReducers.kt`; sessionReducer from `reducer/AccountReducers.kt`.
- Move screens `CM/ui/screens/{LoginScreen,SwitcherScreen,ProfileScreen}.kt` → `feature/account/`; `CM/ui/components/AccountRow.kt` → `feature/account/AccountRow.kt`.
- Move test `JT/ui/AccountSwitchTest.kt` → `JT/feature/account/AccountSwitchTest.kt` (rewrite pkg/imports).

> `EditProfile` is cross-cutting: also handled by `collaboratorsReducer` (Task 7). It lives here; collaborators imports it from `…feature.account`.

- [ ] **Step 1:** Create feature files; `git mv` screens + AccountRow + AccountSwitchTest; set packages; fix imports.
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.account.*` (AppStore: accountsReducer/authFlowReducer; AccountStore: sessionReducer/SessionModel/AuthMode; App.kt; collaborators reducer: EditProfile).
- [ ] **Step 3:** Gate compile → SUCCESS. **M15 check:** `EditProfile` handled in exactly 3 reducers (accountsReducer, sessionReducer, collaboratorsReducer) — grep `EditProfile ->` / `is EditProfile`.
- [ ] **Step 4–5:** Gates detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): extract feature.account`.

---

## Task 7: `feature.collaborators`

**Files (pkg `…feature.collaborators`):**
- Create `feature/collaborators/CollaboratorsModels.kt` ← cut CollaboratorsModel from `model/AccountModels.kt`.
- Create `feature/collaborators/CollaboratorsReducer.kt` ← cut collaboratorsReducer from `reducer/AccountReducers.kt`.
- Move `CM/middleware/BotCollaborator.kt` → `feature/collaborators/BotCollaborator.kt`.
- Move `seedCollaborators` out of `store/AccountStore.kt` → new `feature/collaborators/CollaboratorsSeed.kt` (imports `SeedData` from `…infra`).
- Move test `CT/middleware/BotCollaboratorTest.kt` → `CT/feature/collaborators/` (confirmed present in commonTest).

> `BotCollaborator` imports nothing from `feature.activity`; `BOT_ACCOUNT_ID` is activity-logger-only (stays in Task 8's file).

- [ ] **Step 1:** Create model/reducer/seed files; `git mv` BotCollaborator + test; set packages; fix imports (EditProfile from `…feature.account`; entities from `…core`; SeedData from `…infra`).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.collaborators.*` (AccountStore: CollaboratorsModel/collaboratorsReducer/seedCollaborators; AccountRegistry: startBot; board UI reading CollaboratorsModel).
- [ ] **Step 3–5:** Gates compile / detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): extract feature.collaborators`.

---

## Task 8: `feature.activity`

**Files (pkg `…feature.activity`):** (ActivityEntry already in `core`.)
- Create `feature/activity/ActivityModels.kt` ← cut ActivityModel from `model/BoardSlice.kt`.
- Create `feature/activity/ActivityActions.kt` ← cut RecordActivity from `action/Actions.kt`.
- Create `feature/activity/ActivityReducer.kt` ← cut activityReducer **and `ACTIVITY_CAP`** from `reducer/BoardReducers.kt`.
- Move `CM/middleware/ActivityLoggerMiddleware.kt` → `feature/activity/ActivityLoggerMiddleware.kt` (keeps `BOT_ACCOUNT_ID`).
- Move test `CT/middleware/ActivityLoggerTest.kt` → `CT/feature/activity/`.

- [ ] **Step 1:** Create feature files; `git mv` logger + test; set packages; fix imports (ActivityEntry from `…core`).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.activity.*` (AccountStore: ActivityModel slot + activityReducer + activityLoggerMiddleware registration; any `BOT_ACCOUNT_ID` consumer).
- [ ] **Step 3–5:** Gates compile / detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): extract feature.activity`.

---

## Task 9: `feature.undo`

**Files (pkg `…feature.undo`):**
- Create `feature/undo/UndoModel.kt` ← cut UndoModel from `model/BoardSlice.kt`.
- Create `feature/undo/UndoActions.kt` ← cut Undo, Redo, PushUndo, SetUndoModel from `action/Actions.kt` (PushUndo carries `Board` — now `…core.Board`).
- Create `feature/undo/UndoReducers.kt` ← move `reducer/UndoReducers.kt` (undoModelReducer, undoReducer, pushUndo, UndoResult).
- Move `CM/middleware/UndoMiddleware.kt` → `feature/undo/UndoMiddleware.kt`.
- Move test `CT/middleware/UndoMiddlewareTest.kt` → `CT/feature/undo/`.

- [ ] **Step 1:** Create/`git mv` files + test; set packages; fix imports (Board from `…core`).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.undo.*` (AccountStore: UndoModel slot + undoMiddleware registration order; board reducer/BoardClosed consumers; PushUndo dispatchers).
- [ ] **Step 3–5:** Gates compile / detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): extract feature.undo`.

---

## Task 10: `feature.boardlist` (before Task 11 — board effects import `DEFAULT_BOARD_COLOR`)

**Files (pkg `…feature.boardlist`):** (BoardSummary already in `core`.)
- Create `feature/boardlist/BoardListModels.kt` ← cut BoardListModel from `model/AccountModels.kt`.
- Create `feature/boardlist/BoardListActions.kt` ← cut LoadBoardListRequested/Succeeded/Failed, CreateBoard from `action/Actions.kt`.
- Create `feature/boardlist/BoardListReducer.kt` ← cut boardListReducer **and `DEFAULT_BOARD_COLOR`** from `reducer/AccountReducers.kt`.
- Move `CM/ui/screens/BoardListScreen.kt` → `feature/boardlist/BoardListScreen.kt`; `CM/ui/components/BoardSummaryCard.kt` → `feature/boardlist/BoardSummaryCard.kt`.

- [ ] **Step 1:** Create feature files; `git mv` screen + card; set packages; fix imports (BoardSummary from `…core`).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.boardlist.*` **incl. `DEFAULT_BOARD_COLOR`**, for each consumer: `store/AccountStore.kt`, `reducer/AccountReducers.kt` (remaining navReducer file), `feature/board/EffectsMiddleware` (CreateBoard, LoadBoardList*, DEFAULT_BOARD_COLOR — currently still at old paths; re-point in Task 11), `BoardListScreen`.
- [ ] **Step 3–5:** Gates compile / detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): extract feature.boardlist`.

---

## Task 11: `feature.board` (largest)

**Files (pkg `…feature.board`):** (entities Board/Column/Card/Attachment/Label + card-mutation actions + InverseOp already in `core`.)
- Create `feature/board/BoardModels.kt` ← remaining `model/BoardModels.kt`: BoardModel slot + `newBoardColumns`, `columnById` helpers (operate on `…core` entities).
- Create `feature/board/BoardSliceModels.kt` ← remaining `model/BoardSlice.kt`: FilterModel, SyncModel.
- Create `feature/board/BoardActions.kt` ← remaining `action/Actions.kt` board leaves: BotMovedCard, BotAddedCard, BoardClosed, BoardRestored, StartCreateCard, CancelCreateCard, AddColumn, LoadBoardRequested/Succeeded/Failed, SetFilterQuery, SetFilterAssignee, ToggleFilterLabel, SyncStatusChanged, Refresh.
- Create `feature/board/BoardReducers.kt` ← remaining `reducer/BoardReducers.kt`: boardReducer, filterReducer, syncReducer.
- Move `CM/middleware/EffectsMiddleware.kt` → `feature/board/EffectsMiddleware.kt` whole (preserve `handle` `when` order + `startCollectors`). It imports card actions/InverseOp from `…core`, CreateBoard/LoadBoardList*/DEFAULT_BOARD_COLOR from `…feature.boardlist`, SetOnline from `…feature.settings`, NavModel from `…core`. (Verified: it has **no** nav-action imports.)
- Move screens `CM/ui/screens/{BoardScreen,CardDetailScreen,BoardSelectors}.kt` → `feature/board/`; components `CM/ui/components/{KanbanCard,ColumnHeader,FilterBar,MoveToGroup,AttachmentChip,LabelChip,SyncIndicator,SyncToast,FabMenu}.kt` → `feature/board/`.
- Move tests: `CT/reducer/BoardReducersTest.kt` → `CT/feature/board/`; `CT/action/ActionsTest.kt` → `CT/feature/board/` (or `CT/core/` if it purely asserts kernel actions — inspect; keep whole); `JT/middleware/EffectsMiddlewareTest.kt` → `JT/feature/board/`; `JT/ui/RenderIsolationTest.kt` → `JT/feature/board/`.

- [ ] **Step 1:** Create board feature files (cut remaining symbols); `git mv` effects/screens/components/tests; set packages; fix imports (core entities/actions; infra data/sync; ui shared widgets; collaborators model; undo PushUndo/Board; boardlist DEFAULT_BOARD_COLOR/CreateBoard/LoadBoardList*; settings SetOnline; nav NavModel from core).
- [ ] **Step 2:** Rewrite tree-wide imports → `…feature.board.*` (AccountStore: BoardModel/FilterModel/SyncModel slots + boardReducer/filterReducer/syncReducer + effectsMiddleware; App.kt: BoardScreen/lifecycle; undo: nothing now needs board). Delete the now-empty `CM/model/` and `CM/reducer/BoardReducers.kt` remnant if empty.
- [ ] **Step 3:** Gate compile → SUCCESS. **M16/M15 checks:** `BoardClosed` handled in exactly 4 reducers (boardReducer, filterReducer, syncReducer, undoModelReducer) — grep `is BoardClosed`/`BoardClosed ->`.
- [ ] **Step 4–5:** Gates detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): extract feature.board`.

---

## Task 12: `app.nav` + finalize Action split

> **B1 invariant:** `action/Actions.kt` is deleted ONLY in this task, after the last leaves (nav) are carved. By now every other leaf is gone (Tasks 1,5,6,8,9,10,11); only nav remains.

**Files (pkg `…app.nav`):** (NavModel/Route already in `core`.)
- Create `app/nav/NavActions.kt` ← cut the final leaves from `action/Actions.kt`: Navigate, Back, EnterEditMode, OpenCard, CloseCard. **Then delete the now-empty `action/Actions.kt`.**
- Create `app/nav/NavReducer.kt` ← cut navReducer from `reducer/AccountReducers.kt`. If `AccountReducers.kt` is now empty, delete it.
- Move `CM/ui/components/AdaptiveNav.kt` → `app/nav/AdaptiveNav.kt`.
- Move test `CT/reducer/AccountReducersTest.kt` → `CT/feature/account/AccountReducersTest.kt` whole (now imports navReducer@`app.nav`, boardListReducer@`boardlist`, collaboratorsReducer@`collaborators`, sessionReducer@`account`).

- [ ] **Step 1:** Carve nav actions → `app/nav/NavActions.kt`; **verify `action/Actions.kt` now contains only the (already-moved) markers' import or is empty, then `git rm` it.** Create NavReducer; `git mv` AdaptiveNav + AccountReducersTest; set packages.
- [ ] **Step 2:** Rewrite tree-wide imports → `…app.nav.*` (App.kt routing; AccountStore NavModel slot still core but navReducer now app.nav; board effects `NavModel.activeBoardId` reads NavModel from core — unaffected; screens' Navigate/OpenCard dispatches).
- [ ] **Step 3:** Gate compile → SUCCESS. Verify `find CM -path '*/action' -o -path '*/model'` returns nothing (dirs gone).
- [ ] **Step 4–5:** Gates detekt / jvmTest (147/31).
- [ ] **Step 6:** Commit `refactor(taskflow): extract app.nav + finalize Action split`.

---

## Task 13: `app` shell + store composition + entrypoints

**Files (pkg `…app`):**
- `git mv` `CM/store/{AppStore,AccountStore,AccountRegistry,StoreExt}.kt` → `CM/app/`; `CM/App.kt` → `CM/app/App.kt`.
- Move tests → `app` pkg: `CT/store/AppStoreTest.kt` (**commonTest**, not jvmTest) → `CT/app/AppStoreTest.kt`; `CT/reducer/RootReducersTest.kt` → `CT/app/RootReducersTest.kt`; `JT/store/{AccountRegistryTest,BridgeHandshakeSerializeTest}.kt` → `JT/app/`.
- **Entrypoints** (explicit `App()`/store imports to fix): `JM/main.kt`, `WM/main.kt`, `IM/MainViewController.kt` → add `import org.reduxkotlin.sample.taskflow.app.App` (+ any `createAppStore`/registry); `androidApp/.../app/MainActivity.kt` → change `…taskflow.App` to `…app.App` (its `AndroidContextHolder` already `…infra.platform` from Task 3).

> **M16 invariant (verify BEFORE compile):** in `AccountStore.kt`, the per-account `declareAccountModels()` slot order is unchanged (SessionModel, NavModel, BoardListModel, CollaboratorsModel, BoardModel, FilterModel, UndoModel, SyncModel, ActivityModel) and the devTools middleware pipeline order is unchanged (`activityLogger → undo → effects`). In `AppStore.kt`, root slot order (AccountsModel, AppSettingsModel, AuthFlowModel) unchanged. Only imports change — diff to confirm no reordering.

- [ ] **Step 1:** `git mv` store files + App.kt + the 4 tests; set package `…app`.
- [ ] **Step 2:** Rewrite tree-wide imports → `…app.*` (the 4 entrypoints; any other App/createAppStore/AccountRegistry reference). Confirm `StoreExt.getModel` consumers resolve.
- [ ] **Step 3:** Gate compile → SUCCESS. Run the M16 order diff check above.
- [ ] **Step 4–5:** Gates detekt / jvmTest (147/31).
- [ ] **Step 6:** Verify all old layer dirs are gone: `find CM -type d \( -name action -o -name model -o -name reducer -o -name middleware -o -name store -o -name data -o -name db -o -name util \) ; ls CM/ui/screens CM/ui/components 2>/dev/null` → empty. Remove any empty leftover dirs.
- [ ] **Step 7:** Commit `refactor(taskflow): move shell + store composition to app`.

---

## Task 14: Update `examples/taskflow/ARCHITECTURE.md`

**Files:** `examples/taskflow/ARCHITECTURE.md`.

> Line numbers below are current as of 2026-06-03 — **re-grep each at execution**, don't trust stale numbers.

- [ ] **Step 1:** Replace the package-by-layer narrative with the package-by-feature + domain-kernel map (7 features + core/infra/app/ui; note `infra → core` only; `app` = composition root). Update §4 module/dep graph framing (still one Gradle module; feature packages + kernel) and §5 layered view (layers now live *within* each feature; `core`/`infra`/`ui`/`app` are the cross-cutting homes).
- [ ] **Step 2:** Fix every moved `path:line` reference (grouped by file; re-verify each line post-move):
  - `store/AppStore.kt:40` → `app/AppStore.kt:<new>`
  - `store/AccountStore.kt:123`,`:163` → `app/AccountStore.kt:<new>`
  - `store/AccountRegistry.kt:28` → `app/AccountRegistry.kt:<new>`
  - `action/Actions.kt` → `core/CardActions.kt` + per-feature action files (board/account/settings/undo/activity/boardlist + `app/nav/NavActions.kt`)
  - `reducer/AccountReducers.kt:59` (navReducer) → `app/nav/NavReducer.kt:<new>`
  - `middleware/BotCollaborator.kt:37` → `feature/collaborators/BotCollaborator.kt:<new>`
  - `BoardSelectors.kt` → `feature/board/BoardSelectors.kt`
  - `App.kt` (×3 refs: BoxWithConstraintsRouting, BoardLifecycleEffect, etc.) → `app/App.kt`
  - §18 tests: BoardReducersTest→`feature/board`, ActionsTest→`feature/board` (or `core`), SyncOpCodecTest/FakeRemoteApiTest/LocalStoreTest/SyncEngineTest/OfflineSyncE2ETest→`infra/data`
  - `db/Adapters.kt` → `infra/db/Adapters.kt`; `TaskFlowDb.sq` unchanged (generated pkg `…db`)
  - `composeApp/build.gradle.kts` unchanged
- [ ] **Step 3:** Re-verify each cited line by opening the new file. Confirm Rules A–H prose still accurate; fix any rule text that named an old path.
- [ ] **Step 4:** Commit `docs(taskflow): update ARCHITECTURE.md for package-by-feature layout`.

---

## Task 15: Full multiplatform build verification

**Files:** none.

- [ ] **Step 1:** `./gradlew build` → `BUILD SUCCESSFUL` (compiles jvm/js/wasmJs/android + commonTest/jvmTest + detektAll).
- [ ] **Step 2:** iOS compile (host-buildable on this Mac; sim *tests* are host-gated — trust CI): `./gradlew :examples:taskflow:composeApp:compileKotlinIosSimulatorArm64 :examples:taskflow:composeApp:compileKotlinIosArm64` → SUCCESS. If an Xcode SDK error occurs, record as environmental; CI covers it.
- [ ] **Step 3:** Android shell (only if `:androidApp` included — conditional on `hasAndroidSdk`): `./gradlew :examples:taskflow:androidApp:assembleDebug`. If SDK absent, note and rely on CI.
- [ ] **Step 4:** Re-run the @Test counters from Task 0 Step 2; confirm **147/31** unchanged. A mismatch = a lost/dup test = refactor failure; investigate before PR.

No commit (verification). Any fix → its own `fix(taskflow): …` commit.

---

## Task 16: Open PR

**Files:** none.

- [ ] **Step 1:** `git push -u origin taskflow-refactor`.
- [ ] **Step 2:** `gh pr create` against `master` — title `refactor(taskflow): package-by-feature layout (Phase 0a)`; body: motivation (Phase 0a canon), the feature + domain-kernel map, behavior-preserving guarantee, gates run (build/detekt/jvmTest green @147/31; iOS compile; CI for sim/native), ARCHITECTURE.md update; link the design spec + this plan.

---

## Self-review notes

- **Spec coverage:** every kernel/feature/shared package has a task (core=T1, infra-data=T2, infra.platform=T3, ui=T4, settings=T5, account=T6, collaborators=T7, activity=T8, undo=T9, boardlist=T10, board=T11, app.nav=T12, app=T13); ARCHITECTURE=T14; gates=T0+each task+T15; deliverables 1–3 covered.
- **Layering:** `core` depends on nothing app-specific; `infra → core` (gated in T2 Step 3); features → core/infra/ui (+ documented cross-feature edges); `app` → everything. No `infra → feature` edge.
- **Action-split sequencing (B1):** `action/Actions.kt` deleted only in T12 after the final (nav) carve; all prior tasks remove their leaves. Kernel card-actions leave in T1.
- **Cross-feature edges (intended):** board.effects → boardlist (CreateBoard, LoadBoardList*, DEFAULT_BOARD_COLOR) + settings (SetOnline); collaborators → account (EditProfile); board UI → collaborators (CollaboratorsModel); undo → core (Board). All within the same module.
- **No new tests; @Test count invariant 147/31** asserted every task and in T15.
- **Test-count source of truth:** `grep -rho '@Test' <sourceset> | wc -l` (per Task 0).
- **Package casing:** `boardlist` lowercase (deviates from spec display `boardList`).
- **Ordering dependency:** T10 (boardlist) precedes T11 (board) because board.effects imports `DEFAULT_BOARD_COLOR`.
