# TaskFlow Saveable UI-State Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the active account's volatile UI state (navigation stack + board filter) survive Android process death and configuration changes via a Compose `rememberSaveable` snapshot layered on top of the existing SQLDelight durable store.

**Architecture:** A small `@Serializable` `UiSnapshot` DTO captures only volatile UI state (nav stack as `RouteDto`, filter fields). At save time a custom Compose `Saver` reads the live per-account concurrent store (lock-free `getState` mirror) and serializes the snapshot into the Activity's `SavedStateRegistry`. On restore, the JSON is parsed and replayed once via a `RestoreUiState` action that the per-account routing wires into the `NavModel` and `FilterModel` slots inline (no reducer changes). Board content reloads automatically through the existing `BoardLifecycleEffect`, which keys on `nav.activeBoardId`. SQLDelight remains the sole authority for domain data and `activeAccountId`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (`rememberSaveable`, `Saver`), kotlinx.serialization JSON (already a `commonMain` dep), redux-kotlin bundle (`createConcurrentModelStore`, `getModel<T>()`), redux-kotlin routing (`RoutingBuilder.on<T>`).

**Scope boundaries (confirmed):**
- TaskFlow sample only. No published-module changes, no new module, no `apiDump`.
- Snapshot carries ONLY: nav stack + filter. It does NOT carry `activeAccountId` (SQLDelight owns it — putting it here races the disk load and double-builds the account store) nor `authMode` (login flow resets fine).
- All new symbols are `internal` (every call site is in the same `composeApp` module) to keep the public surface flat and avoid the `explicitApi` KDoc gate churn on `public` decls.
- **Out of scope (documented fast-follow):** eliminating the rotation spinner / avoiding the full per-account store + coroutine rebuild on configuration change. The manifest has no `android:configChanges`, so the Activity (and `remember { createAppStore() }`) is recreated on every rotation. `rememberSaveable` restores the *snapshot* but not the *live store instance*; truly skipping that rebuild needs live-store retention (a retained holder / ViewModel), which is a separate decision.

**Key facts verified in code (do not re-derive):**
- `Route` (`core/AccountEntities.kt:64-94`) is a clean sealed interface, all fields are value-class ids + an enum — trivially serializable. Variants: `BoardList`/`Profile`/`Settings` (`data object`), `Board(boardId: BoardId)`, `CardDetail(cardId: CardId, mode: Mode)` where `Mode = {View, Edit}`, `ComposeCard(columnId: ColumnId)`.
- Id value classes (`core/Ids.kt`): `AccountId(v: String)`, `BoardId(v: String)`, `ColumnId(v: String)`, `CardId(v: String)`, `LabelId(v: String)` — string accessor is `.v`.
- `NavModel(stack: PersistentList<Route>)` (`core/AccountEntities.kt:40`). `FilterModel(query: String, assignee: AccountId?, labelIds: PersistentSet<LabelId>)` (`feature/board/BoardSliceModels.kt:10`).
- `Action` base interface: `org.reduxkotlin.sample.taskflow.core.Action`.
- Per-account store built in `createAccountStore` / `declareAccountModels` (`app/AccountStore.kt:136-251`). Slots are routed by EXACT leaf action class via `on<T> { s, a -> ... }`; a slot can register a handler for any action and return the next slice directly.
- `ActiveAccount` composable (`app/App.kt:179-226`) builds the store via `remember(activeId) { registry.getOrCreate(activeId, SeedData.accountDetail(activeId)) }` and is the single place the active account's store + nav are in composition.
- `BoardLifecycleEffect` keys on `nav.activeBoardId` — restoring the nav stack to `[BoardList, Board(id)]` reloads the board automatically; no explicit board-load dispatch needed in restore.
- The existing `LocalStore.saveNav` / `loadNav` (`infra/data/local/SqlDelightLocalStore.kt:135,194`) are implemented but **never called** (dead code). This plan does not wire them; the Saveable layer supersedes them for the active account. Leave them as-is.
- `getModel<T>()` IS available on `Store<ModelState>` in this codebase (e.g. `app/App.kt:215`, `app/AccountStore.kt:146`). Reads go through the concurrent store's lock-free mirror.

---

## File Structure

- **Create** `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt`
  Responsibility: the `UiSnapshot`/`RouteDto` DTOs, `Route`↔`RouteDto` + model↔snapshot mapping, JSON encode/decode, the `RestoreUiState` action, the live-store bridge (`encodeUiSnapshot`/`decodeUiSnapshot`), and the `RestoreUiStateEffect` composable + `RestoreSlot` holder.
- **Modify** `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt`
  Add `on<RestoreUiState>` handlers to the `NavModel` and `FilterModel` slots in `declareAccountModels`.
- **Modify** `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/App.kt`
  Call `RestoreUiStateEffect(accountStore, activeId)` inside `ActiveAccount`.
- **Create** `examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshotTest.kt`
  Round-trip codec tests + a store-level test that dispatching `RestoreUiState` updates the nav + filter slots.

---

### Task 1: UiSnapshot DTOs + JSON codec (pure, no store)

**Files:**
- Create: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt`
- Test: `examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshotTest.kt`

- [ ] **Step 1: Write the failing test**

Create `UiSnapshotTest.kt`:

```kotlin
package org.reduxkotlin.sample.taskflow.app.persistence

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel
import kotlin.test.Test
import kotlin.test.assertEquals

class UiSnapshotTest {

    @Test
    fun fullStackAndFilterRoundTripThroughJson() {
        val nav = NavModel(
            persistentListOf(
                Route.BoardList,
                Route.Board(BoardId("b1")),
                Route.CardDetail(CardId("c9"), Route.CardDetail.Mode.Edit),
            ),
        )
        val filter = FilterModel(
            query = "urgent",
            assignee = AccountId("a3"),
            labelIds = persistentSetOf(LabelId("l1"), LabelId("l2")),
        )

        val json = encodeUiSnapshot(nav, filter)
        val restored = decodeUiSnapshot(json)

        assertEquals(nav, restored.nav)
        assertEquals(filter, restored.filter)
    }

    @Test
    fun composeCardAndDefaultsRoundTrip() {
        val nav = NavModel(persistentListOf(Route.Settings, Route.ComposeCard(ColumnId("col2"))))
        val filter = FilterModel()

        val restored = decodeUiSnapshot(encodeUiSnapshot(nav, filter))

        assertEquals(nav, restored.nav)
        assertEquals(filter, restored.filter)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :examples:taskflow:composeApp:jvmTest --tests "org.reduxkotlin.sample.taskflow.app.persistence.UiSnapshotTest"`
Expected: FAIL — `encodeUiSnapshot` / `decodeUiSnapshot` / `RestoreUiState` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `UiSnapshot.kt` (codec + action only for now; store bridge + composable added in later tasks but defined here together to keep the file cohesive — only the codec is exercised by Task 1's test):

```kotlin
package org.reduxkotlin.sample.taskflow.app.persistence

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel

/**
 * Action that replays a restored [UiSnapshot] into the per-account store. Routed inline onto the
 * [NavModel] and [FilterModel] slots in `declareAccountModels` — it is never handled by a reducer.
 */
internal data class RestoreUiState(val nav: NavModel, val filter: FilterModel) : Action

/** Serializable mirror of [Route] (value-class ids flattened to their `.v` strings). */
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

    /** @see Route.CardDetail */
    @Serializable @SerialName("cardDetail") data class CardDetail(val cardId: String, val edit: Boolean) : RouteDto

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

private val snapshotJson = Json { classDiscriminator = "t" }

private fun Route.toDto(): RouteDto = when (this) {
    is Route.BoardList -> RouteDto.BoardList
    is Route.Board -> RouteDto.Board(boardId.v)
    is Route.Profile -> RouteDto.Profile
    is Route.Settings -> RouteDto.Settings
    is Route.CardDetail -> RouteDto.CardDetail(cardId.v, mode == Route.CardDetail.Mode.Edit)
    is Route.ComposeCard -> RouteDto.ComposeCard(columnId.v)
}

private fun RouteDto.toRoute(): Route = when (this) {
    is RouteDto.BoardList -> Route.BoardList
    is RouteDto.Board -> Route.Board(BoardId(boardId))
    is RouteDto.Profile -> Route.Profile
    is RouteDto.Settings -> Route.Settings
    is RouteDto.CardDetail ->
        Route.CardDetail(CardId(cardId), if (edit) Route.CardDetail.Mode.Edit else Route.CardDetail.Mode.View)
    is RouteDto.ComposeCard -> Route.ComposeCard(ColumnId(columnId))
}

/** Serializes [nav] + [filter] to a JSON snapshot string. */
internal fun encodeUiSnapshot(nav: NavModel, filter: FilterModel): String {
    val snapshot = UiSnapshot(
        stack = nav.stack.map { it.toDto() },
        filterQuery = filter.query,
        filterAssignee = filter.assignee?.v,
        filterLabelIds = filter.labelIds.map { it.v },
    )
    return snapshotJson.encodeToString(UiSnapshot.serializer(), snapshot)
}

/**
 * Parses a JSON snapshot into a [RestoreUiState]. An empty restored stack falls back to the
 * [NavModel] default (`[BoardList]`) so a malformed/empty snapshot can never strand the user on a
 * blank stack.
 */
internal fun decodeUiSnapshot(json: String): RestoreUiState {
    val snapshot = snapshotJson.decodeFromString(UiSnapshot.serializer(), json)
    val stack = snapshot.stack.map { it.toRoute() }.toPersistentList()
    val nav = if (stack.isEmpty()) NavModel() else NavModel(stack)
    val filter = FilterModel(
        query = snapshot.filterQuery,
        assignee = snapshot.filterAssignee?.let { AccountId(it) },
        labelIds = snapshot.filterLabelIds.map { LabelId(it) }.toPersistentSet(),
    )
    return RestoreUiState(nav, filter)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :examples:taskflow:composeApp:jvmTest --tests "org.reduxkotlin.sample.taskflow.app.persistence.UiSnapshotTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt \
        examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshotTest.kt
git commit -m "feat(taskflow): UiSnapshot DTO + JSON codec for saveable UI state"
```

---

### Task 2: Route `RestoreUiState` into the nav + filter slots

**Files:**
- Modify: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt:198-232`
- Test: `examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshotTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `UiSnapshotTest.kt` (uses the routing DSL directly so the test stays pure — it mirrors how `declareAccountModels` wires the two slots, without building the whole account store):

```kotlin
    @Test
    fun restoreUiStateReplacesNavAndFilterSlots() {
        val target = decodeUiSnapshot(
            encodeUiSnapshot(
                NavModel(persistentListOf(Route.BoardList, Route.Board(BoardId("b7")))),
                FilterModel(query = "q"),
            ),
        )

        // Apply the two inline handlers exactly as declareAccountModels wires them.
        val navAfter: NavModel = (target as RestoreUiState).nav
        val filterAfter: FilterModel = target.filter

        assertEquals(BoardId("b7"), navAfter.activeBoardId)
        assertEquals("q", filterAfter.query)
    }
```

> NOTE: This test pins the contract the routing relies on (the action carries the exact next slices). The wiring itself is verified end-to-end in Task 4's store-level test.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :examples:taskflow:composeApp:jvmTest --tests "org.reduxkotlin.sample.taskflow.app.persistence.UiSnapshotTest"`
Expected: PASS for the codec tests, and this new test FAILS only if `RestoreUiState` isn't `internal`-visible to the test (same module/source-set → it compiles). If it already passes, proceed — the behavioral wiring is added next and covered in Task 4.

- [ ] **Step 3: Add the routing wiring**

In `AccountStore.kt`, add the import near the other feature imports:

```kotlin
import org.reduxkotlin.sample.taskflow.app.persistence.RestoreUiState
```

In `declareAccountModels`, add one handler to the `NavModel` slot (after the existing `on<CancelCreateCard>` line at `:205`):

```kotlin
        on<RestoreUiState> { _, a -> a.nav }
```

And one to the `FilterModel` slot (after the existing `on<BoardClosed>` line at `:231`):

```kotlin
        on<RestoreUiState> { _, a -> a.filter }
```

- [ ] **Step 4: Run test + compile to verify**

Run: `./gradlew :examples:taskflow:composeApp:compileKotlinJvm :examples:taskflow:composeApp:jvmTest --tests "org.reduxkotlin.sample.taskflow.app.persistence.UiSnapshotTest"`
Expected: compiles; all `UiSnapshotTest` tests PASS.

- [ ] **Step 5: Commit**

```bash
git add examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt \
        examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshotTest.kt
git commit -m "feat(taskflow): route RestoreUiState onto nav + filter slots"
```

---

### Task 3: Live-store bridge + end-to-end store restore test

**Files:**
- Modify: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt`
- Test: `examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshotStoreTest.kt`

- [ ] **Step 1: Write the failing test**

Create `UiSnapshotStoreTest.kt` in **jvmTest** (it builds a real account store; mirror the setup pattern in `app/AccountRegistryTest.kt` for the `rootStore` / `localStore` / scope fixtures — reuse its in-memory `LocalStore` test double and root-store helper):

```kotlin
package org.reduxkotlin.sample.taskflow.app.persistence

import kotlinx.coroutines.test.runTest
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel
import kotlin.test.Test
import kotlin.test.assertEquals

class UiSnapshotStoreTest {

    @Test
    fun encodeFromLiveStoreThenDecodeRestoresNavAndFilter() = runTest {
        // Build a per-account store via the same fixtures AccountRegistryTest uses.
        val handle = newTestAccountStore() // helper from the shared test fixtures (see note)
        val store = handle.store

        // Drive it to a non-default nav + filter through real actions.
        store.dispatch(org.reduxkotlin.sample.taskflow.app.nav.Navigate(Route.Board(BoardId("b1"))))
        store.dispatch(org.reduxkotlin.sample.taskflow.feature.board.SetFilterQuery("hello"))

        // Snapshot the live store, then restore into a fresh store.
        val json = encodeUiSnapshot(store)

        val fresh = newTestAccountStore().store
        fresh.dispatch(decodeUiSnapshot(json))

        assertEquals(BoardId("b1"), fresh.getModel<NavModel>().activeBoardId)
        assertEquals("hello", fresh.getModel<FilterModel>().query)
    }
}
```

> NOTE: `newTestAccountStore()` — if `AccountRegistryTest` already exposes a fixture helper, reuse it; otherwise extract its store-building setup into a small `TestAccountStore.kt` under `jvmTest/.../app/` and call it from both. Do NOT duplicate the wiring inline (DRY).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :examples:taskflow:composeApp:jvmTest --tests "org.reduxkotlin.sample.taskflow.app.persistence.UiSnapshotStoreTest"`
Expected: FAIL — `encodeUiSnapshot(store)` overload does not exist yet.

- [ ] **Step 3: Add the live-store bridge overload**

Append to `UiSnapshot.kt` (and add the imports):

```kotlin
import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState

/** Reads the live per-account [store]'s [NavModel] + [FilterModel] (lock-free) into a snapshot string. */
internal fun encodeUiSnapshot(store: Store<ModelState>): String =
    encodeUiSnapshot(store.getModel<NavModel>(), store.getModel<FilterModel>())
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :examples:taskflow:composeApp:jvmTest --tests "org.reduxkotlin.sample.taskflow.app.persistence.UiSnapshotStoreTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt \
        examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/
git commit -m "feat(taskflow): encode UI snapshot from live concurrent store"
```

---

### Task 4: Compose Saveable wiring in `ActiveAccount`

**Files:**
- Modify: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt`
- Modify: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/App.kt:179-200`

- [ ] **Step 1: Add the `RestoreSlot` + `RestoreUiStateEffect` composable**

Append to `UiSnapshot.kt` (add imports):

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** One-shot carrier for a restored snapshot JSON; [consume] returns it exactly once. */
internal class RestoreSlot(private var pending: String?) {
    /** Returns the pending snapshot JSON once (then null), so restore replays exactly once. */
    internal fun consume(): String? {
        val p = pending
        pending = null
        return p
    }
}

/**
 * Persists the active account's volatile UI (nav + filter) across process death / config change.
 *
 * At save time the [Saver] reads [accountStore] directly (lock-free `getState` mirror) and serializes
 * the current snapshot into the Activity `SavedStateRegistry`. On restore, the JSON is parked in a
 * [RestoreSlot] and replayed exactly once from a [LaunchedEffect] (off the composition/dispatch path)
 * via [RestoreUiState]. Board content reloads via the existing `BoardLifecycleEffect`.
 *
 * @param accountStore the active account's store.
 * @param key the active account id — re-scopes the saved slot per account.
 */
@Composable
internal fun RestoreUiStateEffect(accountStore: Store<ModelState>, key: Any) {
    val slot = rememberSaveable(
        key,
        saver = Saver(
            save = { encodeUiSnapshot(accountStore) },
            restore = { json -> RestoreSlot(json) },
        ),
    ) { RestoreSlot(null) }

    LaunchedEffect(key) {
        val json = slot.consume() ?: return@LaunchedEffect
        accountStore.dispatch(decodeUiSnapshot(json))
    }
}
```

- [ ] **Step 2: Call it from `ActiveAccount`**

In `App.kt`, add the import:

```kotlin
import org.reduxkotlin.sample.taskflow.app.persistence.RestoreUiStateEffect
```

In `ActiveAccount`, immediately after `val accountStore = handle.store` (`App.kt:182`), add:

```kotlin
    // Restore volatile UI (nav + filter) saved across process death / config change (§ saveable plan).
    RestoreUiStateEffect(accountStore = accountStore, key = activeId)
```

- [ ] **Step 3: Compile (all targets the host can run)**

Run: `./gradlew :examples:taskflow:composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL. (If the host has the Android SDK: also `:examples:taskflow:composeApp:compileDebugKotlinAndroid`.)

- [ ] **Step 4: Manual verify — Android process death**

This is Compose-on-Android behavior; verify on a device/emulator (TDD unit coverage lives in Tasks 1-3):
1. Build & install the Android app: `./gradlew :examples:taskflow:androidApp:installDebug`.
2. Log in, open a board, drill into a card (Edit mode), set a filter query.
3. Enable **Settings → Developer options → Don't keep activities** (or `adb shell am kill org.reduxkotlin.sample.taskflow` while backgrounded).
4. Background the app (Home), then return.
5. Expected: the same board + card detail (back in **View** mode — Edit isn't persisted by design) and the filter query are restored, not reset to the board list.
6. Also test rotation (config change): the nav + filter survive (a brief driver-init spinner on rotation is expected and out of scope — note it, don't fix it here).

- [ ] **Step 5: Commit**

```bash
git add examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt \
        examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/App.kt
git commit -m "feat(taskflow): wire rememberSaveable UI restore into ActiveAccount"
```

---

### Task 5: Gate, lint, and docs

**Files:**
- Modify: `examples/taskflow/ARCHITECTURE.md` (persistence section — add a short note on the Saveable UI layer vs SQLDelight durable layer)

- [ ] **Step 1: Full lint + build gate**

Run: `./gradlew detektAll`
Expected: PASS. The pre-commit hook runs `detektAll --auto-correct`; if it rewrites formatting, re-stage and amend. Add KDoc to any `internal`/`public` decl detekt flags (the `RouteDto` variants already carry `@see` KDocs).

Run: `./gradlew :examples:taskflow:composeApp:jvmTest`
Expected: PASS (all `UiSnapshot*` tests).

- [ ] **Step 2: Document the layer split**

In `examples/taskflow/ARCHITECTURE.md`, under the persistence/offline section, add a paragraph:

> **Volatile UI persistence (Saveable).** Domain data is durable via SQLDelight (`LocalStore`). The active account's *volatile* UI — the nav stack and board filter — is additionally snapshotted into the Android `SavedStateRegistry` via `RestoreUiStateEffect` (`app/persistence/UiSnapshot.kt`) so it survives process death and configuration changes. The snapshot carries UI state only; `activeAccountId` and all domain data stay owned by SQLDelight (single source of truth — restoring `activeAccountId` from two places would race the disk load). Restoring the nav stack reloads the board through the existing `BoardLifecycleEffect`. CardDetail **Edit** mode is intentionally restored as **View**.

- [ ] **Step 3: Commit**

```bash
git add examples/taskflow/ARCHITECTURE.md
git commit -m "docs(taskflow): document saveable UI-persistence layer"
```

---

## Self-Review

**Spec coverage:**
- Volatile UI survives process death → Tasks 1-4 (snapshot, route, bridge, Compose wiring). ✅
- Nav stack persisted (currently dead `saveNav`) → Task 1 `RouteDto` + Task 4 wiring. ✅
- Filter persisted → Tasks 1-4. ✅
- `activeAccountId` NOT double-sourced → enforced by omitting it from `UiSnapshot` (documented Task 5). ✅
- Restore replayed once, off the dispatch path → `RestoreSlot.consume()` + `LaunchedEffect` (Task 4). ✅
- Threading: snapshot read is lock-free mirror; restore dispatch from `LaunchedEffect` → Task 3/4. ✅
- Board reload after restore → relies on existing `BoardLifecycleEffect` (verified Task 4 manual). ✅
- Out-of-scope rotation-spinner / live-store retention → documented, not implemented. ✅

**Placeholder scan:** No TBD/TODO. The only deferred detail is `newTestAccountStore()` in Task 3, with explicit instruction to reuse/extract from `AccountRegistryTest` (the executor must read that file) — this is a real fixture decision, not a code placeholder.

**Type consistency:** `RestoreUiState(nav: NavModel, filter: FilterModel)` used identically in Tasks 1-4. `encodeUiSnapshot` has two overloads — `(NavModel, FilterModel)` (Task 1) and `(Store<ModelState>)` (Task 3) — both return `String`, consistent. `decodeUiSnapshot(String): RestoreUiState` consistent. Id accessor `.v` matches `core/Ids.kt`. `getModel<T>()` matches existing call sites.
