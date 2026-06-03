package org.reduxkotlin.sample.taskflow.feature.undo

import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.Board

/** Undo the most recent undoable mutation; no-op when [UndoModel.past] is empty. */
public data object Undo : Action

/** Redo the most recently undone mutation; no-op when [UndoModel.future] is empty. */
public data object Redo : Action

/**
 * Internal undo plumbing — dispatched by undoMiddleware; folded by the UndoModel slot reducer.
 * Snapshot the present board before an undoable mutation.
 *
 * @property snapshot the present board captured before the mutation is applied.
 */
public data class PushUndo(val snapshot: Board) : Action

/**
 * Internal undo plumbing — commit the next undo stacks after an Undo/Redo step.
 *
 * @property model the next [UndoModel] to commit.
 */
public data class SetUndoModel(val model: UndoModel) : Action
