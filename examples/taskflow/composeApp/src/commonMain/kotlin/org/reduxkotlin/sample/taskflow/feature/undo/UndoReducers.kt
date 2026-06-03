package org.reduxkotlin.sample.taskflow.feature.undo

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.action.BoardClosed
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.Board

/**
 * Pure per-account reducer for the [UndoModel] slot.
 *
 * Folds the undo plumbing the middleware emits: [PushUndo] snapshots the present board (via [pushUndo],
 * which also clears `future`), [SetUndoModel] replaces the stacks with the result the middleware computed
 * from [undoReducer] after an `Undo`/`Redo` step, and [BoardClosed] clears both stacks. Returns the same
 * [model] instance unchanged for actions it does not handle. The `Undo`/`Redo` step itself lives in the
 * middleware (it needs the present board), so this slot never sees `Undo`/`Redo` directly.
 *
 * @param model the current undo stacks.
 * @param action the dispatched action.
 * @return the next undo stacks, or [model] unchanged when [action] is not handled.
 */
public fun undoModelReducer(model: UndoModel, action: Action): UndoModel = when (action) {
    is PushUndo -> pushUndo(model, action.snapshot)
    is SetUndoModel -> action.model
    is BoardClosed -> undoReducer(model, BoardClosed, null).model
    else -> model
}

/** Outcome of an [undoReducer] step: the next [UndoModel] plus the board snapshot to restore (if any). */
public data class UndoResult(
    /** The next undo stacks. */
    val model: UndoModel,
    /** The board snapshot to apply, or `null` when nothing was restored (empty stack / no-op). */
    val restored: Board?,
)

/**
 * Pushes [snapshot] (the present board, captured before an undoable mutation) onto the undo `past`,
 * dropping the oldest entry beyond `model.cap`, and clears `future` (a new edit invalidates redo).
 *
 * @param model the current undo stacks.
 * @param snapshot the present board to snapshot.
 * @return the updated undo stacks.
 */
public fun pushUndo(model: UndoModel, snapshot: Board): UndoModel {
    val pushed = model.past.add(snapshot)
    val capped = if (pushed.size > model.cap) {
        pushed.subList(pushed.size - model.cap, pushed.size).toPersistentList()
    } else {
        pushed
    }
    return model.copy(past = capped, future = persistentListOf())
}

/**
 * Pure undo/redo step over the [UndoModel] stacks, returning the next stacks and the board to restore.
 *
 * [Undo] pops the newest `past` snapshot (pushing the [present] onto `future`); [Redo] is symmetric
 * over `future`. An empty stack is a no-op (`restored == null`, same [model]). [BoardClosed] clears
 * both stacks. Returns `null` `restored` with the same [model] for actions it does not handle.
 *
 * @param model the current undo stacks.
 * @param action the dispatched action.
 * @param present the current board (pushed to the opposite stack so the move is reversible), or `null`.
 * @return the next stacks and the board snapshot to apply (if any).
 */
public fun undoReducer(model: UndoModel, action: Action, present: Board?): UndoResult = when (action) {
    is Undo ->
        if (model.past.isEmpty()) {
            UndoResult(model, null)
        } else {
            val restored = model.past.last()
            UndoResult(
                model.copy(
                    past = model.past.removeAt(model.past.size - 1),
                    future = present?.let { model.future.add(it) } ?: model.future,
                ),
                restored,
            )
        }

    is Redo ->
        if (model.future.isEmpty()) {
            UndoResult(model, null)
        } else {
            val restored = model.future.last()
            UndoResult(
                model.copy(
                    future = model.future.removeAt(model.future.size - 1),
                    past = present?.let { model.past.add(it) } ?: model.past,
                ),
                restored,
            )
        }

    is BoardClosed -> UndoResult(UndoModel(), null)

    else -> UndoResult(model, null)
}
