package org.reduxkotlin.sample.taskflow.app.persistence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.app.getModel
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
    @Serializable
    @SerialName("boardList")
    data object BoardList : RouteDto

    /** @see Route.Board */
    @Serializable
    @SerialName("board")
    data class Board(val boardId: String) : RouteDto

    /** @see Route.Profile */
    @Serializable
    @SerialName("profile")
    data object Profile : RouteDto

    /** @see Route.Settings */
    @Serializable
    @SerialName("settings")
    data object Settings : RouteDto

    /**
     * @see Route.CardDetail
     *
     * Note: edit mode is intentionally not persisted — CardDetail always restores to View mode.
     * Edit is a transient inline-edit state with no durable buffer to recover.
     */
    @Serializable
    @SerialName("cardDetail")
    data class CardDetail(val cardId: String) : RouteDto

    /** @see Route.ComposeCard */
    @Serializable
    @SerialName("composeCard")
    data class ComposeCard(val columnId: String) : RouteDto
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

/** Reads the live per-account [store]'s [NavModel] + [FilterModel] (lock-free) into a snapshot string. */
internal fun encodeUiSnapshot(store: Store<ModelState>): String =
    encodeUiSnapshot(store.getModel<NavModel>(), store.getModel<FilterModel>())

/**
 * Parses a JSON snapshot into a [RestoreUiState]. Defensive: a malformed snapshot, or one whose
 * stack decodes empty, falls back to the [NavModel] default (`[BoardList]`) + an empty [FilterModel]
 * so a corrupt/truncated save can never strand the user on a blank stack.
 */
internal fun decodeUiSnapshot(json: String): RestoreUiState {
    val snapshot = runCatching { snapshotJson.decodeFromString(UiSnapshot.serializer(), json) }
        .getOrNull()
        ?: return RestoreUiState(NavModel(), FilterModel())
    val stack = snapshot.stack.map { it.toRoute() }.toPersistentList()
    val nav = if (stack.isEmpty()) NavModel() else NavModel(stack)
    val filter = FilterModel(
        query = snapshot.filterQuery,
        assignee = snapshot.filterAssignee?.let { AccountId(it) },
        labelIds = snapshot.filterLabelIds.map { LabelId(it) }.toPersistentSet(),
    )
    return RestoreUiState(nav, filter)
}

/** One-shot carrier for a restored snapshot JSON; [consume] returns it exactly once. */
internal class RestoreSlot(private var pending: String?) {
    /** Returns the pending snapshot JSON once (then null), so restore replays exactly once. */
    fun consume(): String? {
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
 * via [RestoreUiState]. Board content reloads via the existing board-lifecycle effect.
 *
 * @param accountStore the active account's store.
 * @param key the active account id — scopes the saved slot per account via [rememberSaveable].
 *   The [LaunchedEffect] keys on the [RestoreSlot] instance itself so an in-place
 *   [rememberSaveable] restore (where [key] is unchanged) still triggers the effect.
 */
@Composable
internal fun RestoreUiStateEffect(accountStore: Store<ModelState>, key: Any) {
    val slot = rememberSaveable(
        key,
        saver = Saver<RestoreSlot, String>(
            save = { encodeUiSnapshot(accountStore) },
            restore = { json -> RestoreSlot(json) },
        ),
    ) { RestoreSlot(null) }

    LaunchedEffect(slot) {
        val json = slot.consume() ?: return@LaunchedEffect
        accountStore.dispatch(decodeUiSnapshot(json))
    }
}
