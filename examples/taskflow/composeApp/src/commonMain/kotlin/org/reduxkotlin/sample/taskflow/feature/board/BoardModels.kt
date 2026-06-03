package org.reduxkotlin.sample.taskflow.feature.board

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId

// Always-present slot. board == null is the NotLoaded sentinel (reset by BoardClosed).
data class BoardModel(val board: Board? = null)

// Pure helper used by the Board screen (Rule C) to bind per-column lists by ColumnId.
fun Board.columnById(id: ColumnId): Column? = columns.firstOrNull { it.id == id }

// The default empty columns (To Do / Doing / Done) a freshly created board starts with.
fun newBoardColumns(boardId: BoardId): PersistentList<Column> = persistentListOf(
    Column(ColumnId("${boardId.v}-todo"), "To Do", persistentListOf()),
    Column(ColumnId("${boardId.v}-doing"), "Doing", persistentListOf()),
    Column(ColumnId("${boardId.v}-done"), "Done", persistentListOf()),
)
