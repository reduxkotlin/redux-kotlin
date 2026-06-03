package org.reduxkotlin.sample.taskflow.feature.undo

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.reduxkotlin.sample.taskflow.core.Board

/**
 * Per-account undo/redo stacks.
 *
 * [past] holds board snapshots captured before each undoable mutation (newest last); [future]
 * holds the snapshots pushed when the user steps back with Undo (newest last). Both lists are
 * capped to [cap] entries — the oldest entry falls off the [past] end when a push would exceed it.
 *
 * @property past snapshots available for Undo (newest last).
 * @property future snapshots available for Redo (newest last).
 * @property cap maximum number of entries in each stack.
 */
public data class UndoModel(
    val past: PersistentList<Board> = persistentListOf(),
    val future: PersistentList<Board> = persistentListOf(),
    val cap: Int = 15,
)
