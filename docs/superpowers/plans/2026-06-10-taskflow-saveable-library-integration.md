# TaskFlow — adopt the saveable/binding APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`).

**Goal:** Re-implement TaskFlow's volatile-UI persistence on the new library APIs (replacing #331's hand-rolled approach): `StateSaver` + `Store.rememberSaveableState` (now flash-free via Solution 1), `coalescingNotificationContext`, and the lag-free bindings — and remove dead/legacy paths.

**Architecture:** Per-account nav+filter persist via a single `StateSaver<ModelState, UiSnapshot>` and one `accountStore.rememberSaveableState(accountUiSaver, key="account-ui-<id>")` in `ActiveAccount`. The library applies the restore synchronously in composition (Solution 1) → no flash. `mainNotificationContext` actuals use `coalescingNotificationContext` (inline on main, else post) → no async-notify lag for any subscriber. The root store keeps a `booted` gate + a plain lag-free `fieldStateOf` for `activeAccountId` (durable in SQLDelight, single-sourced). CardDetail draft survives via `rememberSaveable`. Dead `saveNav`/`loadNav` removed.

**Tech Stack:** redux-kotlin-compose-saveable (`StateSaver`, `rememberSaveableState`), redux-kotlin-concurrent (`coalescingNotificationContext`), lag-free `fieldStateOf`/`selectorState`, kotlinx.serialization. All already available via `redux-kotlin-bundle-compose` (already a dep). Branch `feat/taskflow-saveable-integration` stacked on `feat/concurrent-binding-consistency` (PR #332). examples/taskflow = `convention.control` (no apiDump/explicitApi gate, but detekt KDoc still applies to public decls — keep new symbols `internal`).

**Verified facts:**
- `StateSaver<S, Snapshot>(serializer, save: (S)->Snapshot, restore: (Snapshot)->Any, json=Json)`. `Store<S>.rememberSaveableState(saver, key: String?=null)` — restores synchronously in composition (Solution 1), registers save provider.
- `coalescingNotificationContext(isOnTargetThread: ()->Boolean, post: (()->Unit)->Unit): NotificationContext` (redux-kotlin-concurrent).
- `ModelState.get<reified M>()` reads a slot (throws if undeclared). Routing: `model(NavModel()) { on<A> { s,a -> ... } }`.
- `Route` (core/AccountEntities.kt): BoardList/Profile/Settings (data object), Board(boardId: BoardId), CardDetail(cardId: CardId, mode: Mode{View,Edit}), ComposeCard(columnId: ColumnId). Ids: `value class X(val v: String)`.
- `NavModel(stack: PersistentList<Route>)`; `FilterModel(query: String, assignee: AccountId?, labelIds: PersistentSet<LabelId>)`.
- Current actuals: Android `Handler.post` (always), iOS `dispatch_async` (always), JVM coalesces manually (EDT), wasmJs inline.
- `saveNav`/`loadNav` defined in LocalStore + SqlDelightLocalStore + re-exposed in SyncRepository, but UNUSED — verify then remove.

---

### Task 1: coalescing NotificationContext actuals

**Files:** Modify the 4 `Notification.<platform>.kt` actuals under `examples/taskflow/composeApp/src/{androidMain,iosMain,jvmMain,wasmJsMain}/.../infra/platform/`.

- [ ] **Step 1: Android** — replace body with:
```kotlin
public actual fun mainNotificationContext(): NotificationContext {
    val handler = Handler(Looper.getMainLooper())
    return coalescingNotificationContext(
        isOnTargetThread = { Looper.myLooper() == Looper.getMainLooper() },
        post = { block -> handler.post(block) },
    )
}
```
Add import `org.reduxkotlin.concurrent.coalescingNotificationContext`. Update KDoc to mention inline-on-main.

- [ ] **Step 2: iOS** — replace body with:
```kotlin
public actual fun mainNotificationContext(): NotificationContext = coalescingNotificationContext(
    isOnTargetThread = { NSThread.isMainThread() },
    post = { block -> dispatch_async(dispatch_get_main_queue()) { block() } },
)
```
Add imports `platform.Foundation.NSThread` and `org.reduxkotlin.concurrent.coalescingNotificationContext` (keep the darwin imports). Update KDoc.

- [ ] **Step 3: JVM** — refactor the manual coalesce to the helper:
```kotlin
public actual fun mainNotificationContext(): NotificationContext = coalescingNotificationContext(
    isOnTargetThread = { SwingUtilities.isEventDispatchThread() },
    post = { block -> SwingUtilities.invokeLater(block) },
)
```
Add import. (wasmJs stays inline — no change.)

- [ ] **Step 4: Compile**
Run: `./gradlew :examples:taskflow:composeApp:compileKotlinJvm` → BUILD SUCCESSFUL. If Android SDK present: `:examples:taskflow:composeApp:compileAndroidMain`. iOS: `:examples:taskflow:composeApp:compileKotlinIosSimulatorArm64` (host-gated; if it fails on SDK env, report, don't treat as code failure).
Run: `./gradlew detektAll` → clean.

- [ ] **Step 5: Commit**
```bash
git add examples/taskflow/composeApp/src/androidMain examples/taskflow/composeApp/src/iosMain examples/taskflow/composeApp/src/jvmMain
git commit -m "refactor(taskflow): main NotificationContext via coalescingNotificationContext (inline on main)"
```

---

### Task 2: StateSaver + UiSnapshot codec + RestoreUiState routing

**Files:**
- Create: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt`
- Modify: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt` (route `RestoreUiState`)
- Test: `examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshotSaverTest.kt`

- [ ] **Step 1: Write the failing test**
```kotlin
package org.reduxkotlin.sample.taskflow.app.persistence

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel
import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiSnapshotSaverTest {
    @Test
    fun saverRoundTripsNavAndFilterThroughJson() {
        val nav = NavModel(persistentListOf(Route.BoardList, Route.Board(BoardId("b1")), Route.CardDetail(CardId("c9"))))
        val filter = FilterModel(query = "urgent", assignee = AccountId("a3"), labelIds = persistentSetOf(LabelId("l1")))
        val ms = ModelState.of(nav, filter)

        val snap = accountUiSaver.save(ms)
        val json = accountUiSaver.json.encodeToString(accountUiSaver.serializer, snap)
        val decoded = accountUiSaver.json.decodeFromString(accountUiSaver.serializer, json)
        val action = accountUiSaver.restore(decoded) as RestoreUiState

        assertEquals(BoardId("b1"), action.nav.activeBoardId)
        assertEquals("urgent", action.filter.query)
        assertEquals(AccountId("a3"), action.filter.assignee)
    }

    @Test
    fun cardDetailRestoresInViewMode() {
        val nav = NavModel(persistentListOf(Route.BoardList, Route.Board(BoardId("b1")), Route.CardDetail(CardId("c9"), Route.CardDetail.Mode.Edit)))
        val ms = ModelState.of(nav, FilterModel())
        val action = accountUiSaver.restore(accountUiSaver.save(ms)) as RestoreUiState
        val top = action.nav.stack.last()
        assertTrue(top is Route.CardDetail && top.mode == Route.CardDetail.Mode.View)
    }
}
```
(`ModelState.of(nav, filter)` builds a 2-slot state; `accountUiSaver.save` reads those slots.)

- [ ] **Step 2: Run; expect FAIL**
`./gradlew :examples:taskflow:composeApp:jvmTest --tests "*UiSnapshotSaverTest"` → FAIL (unresolved `accountUiSaver`/`RestoreUiState`).

- [ ] **Step 3: Create UiSnapshot.kt**
```kotlin
package org.reduxkotlin.sample.taskflow.app.persistence

import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.reduxkotlin.compose.saveable.StateSaver
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel

/** Action that replays a restored [UiSnapshot]; routed onto the NavModel + FilterModel slots. */
internal data class RestoreUiState(val nav: NavModel, val filter: FilterModel) : Action

/** Serializable mirror of [Route] (value-class ids flattened to `.v` strings). */
@Serializable
internal sealed interface RouteDto {
    /** @see Route.BoardList */
    @Serializable @SerialName("boardList") data object BoardList : RouteDto

    /** @see Route.Board */
    @Serializable @SerialName("board") data class Board(val boardId: String) : RouteDto

    /** @see Route.Profile */
    @Serializable @SerialName("profile") data object Profile : RouteDto

    /** @see Route.Settings */
    @Serializable @SerialName("settings") data object Settings : RouteDto

    /** @see Route.CardDetail — edit mode is intentionally not persisted (always restores to View). */
    @Serializable @SerialName("cardDetail") data class CardDetail(val cardId: String) : RouteDto

    /** @see Route.ComposeCard */
    @Serializable @SerialName("composeCard") data class ComposeCard(val columnId: String) : RouteDto
}

/** Serializable volatile-UI snapshot of the active account: nav stack + filter only. */
@Serializable
internal data class UiSnapshot(
    val stack: List<RouteDto>,
    val filterQuery: String,
    val filterAssignee: String?,
    val filterLabelIds: List<String>,
)

private fun Route.toDto(): RouteDto = when (this) {
    is Route.BoardList -> RouteDto.BoardList
    is Route.Board -> RouteDto.Board(boardId.v)
    is Route.Profile -> RouteDto.Profile
    is Route.Settings -> RouteDto.Settings
    is Route.CardDetail -> RouteDto.CardDetail(cardId.v)
    is Route.ComposeCard -> RouteDto.ComposeCard(columnId.v)
}

private fun RouteDto.toRoute(): Route = when (this) {
    is RouteDto.BoardList -> Route.BoardList
    is RouteDto.Board -> Route.Board(BoardId(boardId))
    is RouteDto.Profile -> Route.Profile
    is RouteDto.Settings -> Route.Settings
    is RouteDto.CardDetail -> Route.CardDetail(CardId(cardId), Route.CardDetail.Mode.View)
    is RouteDto.ComposeCard -> Route.ComposeCard(ColumnId(columnId))
}

/**
 * The per-account volatile-UI saver (nav stack + board filter). One stateless instance, reused across
 * accounts. Restore overlays the saved nav/filter via a [RestoreUiState] action; CardDetail restores
 * in View mode. The library applies restore synchronously in composition, so there is no stale frame.
 */
internal val accountUiSaver: StateSaver<ModelState, UiSnapshot> = StateSaver(
    serializer = UiSnapshot.serializer(),
    save = { ms ->
        val nav = ms.get<NavModel>()
        val filter = ms.get<FilterModel>()
        UiSnapshot(
            stack = nav.stack.map { it.toDto() },
            filterQuery = filter.query,
            filterAssignee = filter.assignee?.v,
            filterLabelIds = filter.labelIds.map { it.v },
        )
    },
    restore = { snap ->
        val stack = snap.stack.map { it.toRoute() }.toPersistentList()
        val nav = if (stack.isEmpty()) NavModel() else NavModel(stack)
        val filter = FilterModel(
            query = snap.filterQuery,
            assignee = snap.filterAssignee?.let { AccountId(it) },
            labelIds = snap.filterLabelIds.map { LabelId(it) }.toPersistentSet(),
        )
        RestoreUiState(nav, filter)
    },
    json = Json { classDiscriminator = "t" },
)
```

- [ ] **Step 4: Route RestoreUiState in AccountStore.kt**
Add import `org.reduxkotlin.sample.taskflow.app.persistence.RestoreUiState`. In `declareAccountModels`, in the `model(NavModel()) { ... }` block add `on<RestoreUiState> { _, a -> a.nav }`, and in `model(FilterModel()) { ... }` add `on<RestoreUiState> { _, a -> a.filter }`. (If adding lines pushes `declareAccountModels` past the detekt LongMethod limit, extract a helper as needed.)

- [ ] **Step 5: Run + detekt**
`./gradlew :examples:taskflow:composeApp:jvmTest --tests "*UiSnapshotSaverTest"` → PASS.
`./gradlew detektAll` → clean.

- [ ] **Step 6: Commit**
```bash
git add examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshotSaverTest.kt
git commit -m "feat(taskflow): per-account UI StateSaver (nav+filter) + RestoreUiState routing"
```

---

### Task 3: Wire rememberSaveableState + booted gate + lag-free activeId (App.kt)

**Files:** Modify `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/App.kt`.

- [ ] **Step 1: ActiveAccount** — read it first. After `val accountStore = handle.store` and BEFORE `val nav by ...fieldStateOf(NavModel::class)`, add:
```kotlin
    // Persist + restore this account's volatile UI (nav + filter) across process death / config change.
    // The library applies the restore synchronously in composition (before the nav read below), so the
    // first frame already shows the restored stack. Account-scoped key: the default composite key would
    // collide across accounts at this same call site.
    accountStore.rememberSaveableState(accountUiSaver, key = "account-ui-${activeId.v}")
```
Add imports `org.reduxkotlin.compose.saveable.rememberSaveableState` and `org.reduxkotlin.sample.taskflow.app.persistence.accountUiSaver`.

- [ ] **Step 2: AppShell booted gate + lag-free activeId** — read AppShell. Add a `booted` flag set after the bootstrap dispatch, gate the first paint, and read activeId via a plain (now lag-free) `fieldStateOf`:
```kotlin
    var booted by remember(localStore) { mutableStateOf(false) }
    LaunchedEffect(localStore) {
        localStore.ensureSeeded()
        val activeId = localStore.loadActiveAccountId()
        val accounts = localStore.loadAccounts()
        appStore.dispatch(LoadAccountsSucceeded(accounts, activeId))
        booted = true
    }
    val theme by rememberStableStore(appStore).value.fieldStateOf(AppSettingsModel::class) { it.theme }
    val activeId by rememberStableStore(appStore).value.fieldStateOf(AccountsModel::class) { it.activeAccountId }
    // ... inside the root Surface:
    when {
        !booted -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        activeId == null -> LoginScreen(appStore)
        else -> {
            LaunchedEffect(activeId) { localStore.saveActiveAccountId(activeId!!) }
            ActiveAccount(appStore = appStore, registry = registry, activeId = activeId!!)
        }
    }
```
Match the existing AppShell structure exactly (theme/Surface/CompositionLocalProvider). The lag-free `fieldStateOf` + coalescing context mean `activeId` is current right after the dispatch; the `booted` gate only covers the disk-load window (no Login flash). Keep `Box`/`CircularProgressIndicator`/`Alignment`/`Modifier.fillMaxSize` imports (already used by the splash branch).

- [ ] **Step 3: Compile**
`./gradlew :examples:taskflow:composeApp:compileKotlinJvm` → BUILD SUCCESSFUL. `./gradlew detektAll` → clean.

- [ ] **Step 4: Commit**
```bash
git add examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/App.kt
git commit -m "feat(taskflow): rememberSaveableState restore + booted gate + lag-free activeId"
```

---

### Task 4: CardDetail create-draft survives via rememberSaveable

**Files:** Modify `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/CardDetailScreen.kt` (the `CreateMode` composable).

- [ ] **Step 1** — read `CreateMode`. Change the two draft fields from `remember` to `rememberSaveable`:
```kotlin
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
```
Add import `androidx.compose.runtime.saveable.rememberSaveable`. Update the KDoc/comment to note the draft rides the SavedStateRegistry (Rule C still holds — keystrokes stay local).

- [ ] **Step 2: Compile + detekt**
`./gradlew :examples:taskflow:composeApp:compileKotlinJvm` → SUCCESSFUL. `./gradlew detektAll` → clean.

- [ ] **Step 3: Commit**
```bash
git add examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/CardDetailScreen.kt
git commit -m "feat(taskflow): persist new-card draft via rememberSaveable"
```

---

### Task 5: Remove dead saveNav/loadNav

**Files:** `infra/data/local/LocalStore.kt`, `infra/data/local/SqlDelightLocalStore.kt`, `infra/data/sync/SyncRepository.kt` (and the SQLDelight `.sq` query if `selectNav`/`upsertNav` exist only for this).

- [ ] **Step 1: Verify dead** — run `grep -rn "saveNav\|loadNav" examples/taskflow/composeApp/src` and confirm the ONLY occurrences are the declarations/impls + the SyncRepository pass-throughs, with NO real callers. If anything calls them, STOP and report (do not remove).

- [ ] **Step 2: Remove** the `saveNav`/`loadNav` from the `LocalStore` interface, their `override` impls in `SqlDelightLocalStore`, and the pass-throughs in `SyncRepository`. If the `.sq` file has `selectNav`/`upsertNav`/an `account_nav` table used ONLY by these (verify via grep), leave the table (data) but remove now-unused generated query usages only if they cause unused warnings; prefer minimal removal — do not drop the table/migration. Keep `ROUTE_*` constants only if still referenced.

- [ ] **Step 3: Compile + test**
`./gradlew :examples:taskflow:composeApp:compileKotlinJvm :examples:taskflow:composeApp:jvmTest` → SUCCESSFUL. `./gradlew detektAll` → clean.

- [ ] **Step 4: Commit**
```bash
git add -A examples/taskflow/composeApp/src
git commit -m "chore(taskflow): remove dead saveNav/loadNav (superseded by saveable UI persistence)"
```

---

### Task 6: Docs + full gate + on-device verification

**Files:** `examples/taskflow/ARCHITECTURE.md`.

- [ ] **Step 1: Docs** — update the persistence section: volatile UI (nav+filter) now persists via `redux-kotlin-compose-saveable` (`StateSaver` + `rememberSaveableState`, account-scoped key) with synchronous restore (no flash); main-thread notifications via `coalescingNotificationContext`; `activeAccountId`/domain stay in SQLDelight; CardDetail Edit restores as View; new-card draft via `rememberSaveable`.

- [ ] **Step 2: Full gate**
`./gradlew :examples:taskflow:composeApp:jvmTest` → PASS. `./gradlew detektAll` → PASS. `./gradlew :examples:taskflow:composeApp:compileKotlinJvm` (+ `compileAndroidMain` if SDK present) → SUCCESSFUL.

- [ ] **Step 3: On-device verification (Android emulator/device)**
Build + install: `./gradlew :examples:taskflow:androidApp:installDebug`.
Verify the full restore matrix (toggle bot OFF in Settings first):
  1. Open a board → drill into a card → set a filter query → background → `adb shell am kill org.reduxkotlin.sample.taskflow` → reopen. Expect: spinner → restored screen (board + card in **View** + filter), NO Login/board-list/board-detail flash.
  2. Add-card draft: open add-card, type a title + description → background → `am kill` → reopen. Expect: add-card with the typed draft restored.
  3. Cold start (fresh) still shows board list (no crash).
Capture logcat/screens as needed. Report what was observed.

- [ ] **Step 4: Commit + finish**
```bash
git add examples/taskflow/ARCHITECTURE.md
git commit -m "docs(taskflow): document library-based saveable UI persistence"
```

---

## Self-Review
- Coalescing notify (A) → Task 1. StateSaver+routing (B core) → Task 2. rememberSaveableState placement + booted gate + lag-free activeId cleanup → Task 3. Draft → Task 4. Dead-code cleanup → Task 5. Docs+gate+device → Task 6.
- No new published-module API (TaskFlow is convention.control). Library deps already present via redux-kotlin-bundle-compose.
- Flash-free guaranteed by Solution 1 (sync restore) + lag-free bindings + coalescing context — verified on device in Task 6.
- Types: `accountUiSaver: StateSaver<ModelState, UiSnapshot>`, `RestoreUiState(nav, filter)` consistent across Tasks 2/3. Key `"account-ui-${activeId.v}"` in Task 3.
