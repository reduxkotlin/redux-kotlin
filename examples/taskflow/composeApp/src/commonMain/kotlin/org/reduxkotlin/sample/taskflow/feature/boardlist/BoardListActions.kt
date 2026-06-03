package org.reduxkotlin.sample.taskflow.feature.boardlist

import kotlinx.collections.immutable.PersistentList
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary
import kotlin.time.Instant

/** Triggers a load of the full board-list for the active account. */
public data object LoadBoardListRequested : Action

/** Delivers the freshly loaded board summaries (replaces the tile cache). */
public data class LoadBoardListSucceeded(val summaries: PersistentList<BoardSummary>) : Action

/** Reports a board-list load failure. */
public data class LoadBoardListFailed(val error: String) : Action

/** Creates a new board with a pre-minted [boardId] and [name], stamped at [now]. */
public data class CreateBoard(val boardId: BoardId, val name: String, val now: Instant) : Action
