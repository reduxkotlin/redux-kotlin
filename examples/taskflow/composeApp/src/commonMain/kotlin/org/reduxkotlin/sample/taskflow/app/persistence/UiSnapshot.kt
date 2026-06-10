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

/**
 * Action dispatched when a persisted [UiSnapshot] is restored into the account store.
 * Each model slot's handler replaces its slice with the restored data.
 *
 * @property nav restored navigation stack model.
 * @property filter restored filter model.
 */
internal data class RestoreUiState(val nav: NavModel, val filter: FilterModel) : Action

/**
 * Serializable DTO representing a single navigation destination.
 * Produced by [Route.toDto] and converted back via [RouteDto.toRoute].
 *
 * [CardDetail] always restores in [Route.CardDetail.Mode.View] — the mode field is dropped.
 */
@Serializable
internal sealed class RouteDto {

    /** DTO for [Route.BoardList]. */
    @Serializable
    @SerialName("bl")
    data object BoardList : RouteDto()

    /** DTO for [Route.Profile]. */
    @Serializable
    @SerialName("pr")
    data object Profile : RouteDto()

    /** DTO for [Route.Settings]. */
    @Serializable
    @SerialName("se")
    data object Settings : RouteDto()

    /**
     * DTO for [Route.Board].
     *
     * @property boardId raw board id string.
     */
    @Serializable
    @SerialName("bo")
    data class Board(val boardId: String) : RouteDto()

    /**
     * DTO for [Route.CardDetail]. Mode is omitted; restores as [Route.CardDetail.Mode.View].
     *
     * @property cardId raw card id string.
     */
    @Serializable
    @SerialName("cd")
    data class CardDetail(val cardId: String) : RouteDto()

    /**
     * DTO for [Route.ComposeCard].
     *
     * @property columnId raw column id string.
     */
    @Serializable
    @SerialName("cc")
    data class ComposeCard(val columnId: String) : RouteDto()
}

/**
 * Minimal serializable snapshot of per-account UI state: the navigation stack and board filter.
 *
 * @property stack list of [RouteDto] entries representing the persisted nav stack.
 * @property filterQuery persisted search query string.
 * @property filterAssignee persisted assignee id string, or null.
 * @property filterLabelIds persisted label id strings.
 */
@Serializable
internal data class UiSnapshot(
    val stack: List<RouteDto>,
    val filterQuery: String,
    val filterAssignee: String?,
    val filterLabelIds: List<String>,
)

/** Maps a [Route] to its [RouteDto] counterpart. */
private fun Route.toDto(): RouteDto = when (this) {
    is Route.BoardList -> RouteDto.BoardList
    is Route.Profile -> RouteDto.Profile
    is Route.Settings -> RouteDto.Settings
    is Route.Board -> RouteDto.Board(boardId.v)
    is Route.CardDetail -> RouteDto.CardDetail(cardId.v)
    is Route.ComposeCard -> RouteDto.ComposeCard(columnId.v)
}

/** Maps a [RouteDto] back to a [Route]. [RouteDto.CardDetail] always restores as [Route.CardDetail.Mode.View]. */
private fun RouteDto.toRoute(): Route = when (this) {
    is RouteDto.BoardList -> Route.BoardList
    is RouteDto.Profile -> Route.Profile
    is RouteDto.Settings -> Route.Settings
    is RouteDto.Board -> Route.Board(BoardId(boardId))
    is RouteDto.CardDetail -> Route.CardDetail(CardId(cardId), Route.CardDetail.Mode.View)
    is RouteDto.ComposeCard -> Route.ComposeCard(ColumnId(columnId))
}

/**
 * Per-account [StateSaver] that snapshots the [NavModel] stack and [FilterModel] into a
 * JSON-serializable [UiSnapshot], and restores them via a [RestoreUiState] action.
 *
 * [Route.CardDetail] is always restored in [Route.CardDetail.Mode.View] so an interrupted
 * edit session never re-opens in edit mode.
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
