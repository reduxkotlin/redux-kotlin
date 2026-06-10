package org.reduxkotlin.sample.taskflow.app.persistence

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

    /** @see Route.CardDetail */
    @Serializable
    @SerialName("cardDetail")
    data class CardDetail(val cardId: String, val edit: Boolean) : RouteDto

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
