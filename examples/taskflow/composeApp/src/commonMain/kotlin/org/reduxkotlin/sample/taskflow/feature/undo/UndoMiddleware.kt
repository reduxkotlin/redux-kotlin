package org.reduxkotlin.sample.taskflow.feature.undo

import org.reduxkotlin.Middleware
import org.reduxkotlin.Store
import org.reduxkotlin.middleware
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.BoardRestored
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.Undoable
import org.reduxkotlin.sample.taskflow.model.BoardModel

/**
 * The undo/redo middleware: the SINGLE place that maintains the per-account undo history.
 *
 * It must live in middleware (not a reducer) because [Undoable] is a marker interface and the routing
 * layer matches on the EXACT leaf action class, so a reducer cannot route on "any undoable". The flow:
 *
 * - **Before** an [Undoable] user mutation it snapshots the *present* board by dispatching [PushUndo]
 *   (folded by the `UndoModel` slot via `pushUndo`, which also clears the redo `future`), then forwards
 *   the mutation so the optimistic reducers apply it. Bot mutations (`BotMovedCard`/`BotAddedCard`) are
 *   deliberately NOT [Undoable], so they never enter the user's history.
 * - On [Undo]/[Redo] it runs the pure [undoReducer] over the current stacks + present board, and — when a
 *   snapshot is restored — commits the next stacks with [SetUndoModel] and applies the board with
 *   [BoardRestored]. Both are intercepted (not forwarded): the work is done by re-dispatching.
 *
 * @return the assembled [Middleware] over [ModelState].
 */
public fun undoMiddleware(): Middleware<ModelState> = middleware { store, next, action ->
    when (action) {
        is Undoable -> {
            val present = store.state.get<BoardModel>().board
            if (present != null) store.dispatch(PushUndo(present))
            next(action)
        }

        is Undo -> applyStep(store, Undo)

        is Redo -> applyStep(store, Redo)

        else -> next(action)
    }
}

/**
 * Runs one [undoReducer] step over the present stacks + board and, if a snapshot was restored, commits
 * the next stacks ([SetUndoModel]) and applies the board ([BoardRestored]). Returns [action] (intercepted;
 * an empty stack is a no-op).
 */
private fun applyStep(store: Store<ModelState>, action: Action): Any {
    val undo = store.state.get<UndoModel>()
    val present = store.state.get<BoardModel>().board
    val result = undoReducer(undo, action, present)
    val restored = result.restored
    if (restored != null) {
        store.dispatch(SetUndoModel(result.model))
        store.dispatch(BoardRestored(restored))
    }
    return action
}
