package org.reduxkotlin.sample.taskflow.feature.boardlist

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary

/** Holds the cached board tiles and their display order for one account. */
public data class BoardListModel(
    val boards: PersistentMap<BoardId, BoardSummary> = persistentMapOf(),
    val order: PersistentList<BoardId> = persistentListOf(),
)
