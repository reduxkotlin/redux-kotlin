package org.reduxkotlin.sample.taskflow.core

import kotlin.time.Instant

/** Request to move a card from one column to another; undoable and persisted. */
public data class CardMoveRequested(
    val cardId: CardId,
    val from: ColumnId,
    val to: ColumnId,
    val toIndex: Int,
    val opId: OpId,
) : Action,
    Undoable

/** Request to add a new card to a column; undoable and persisted. */
public data class AddCard(
    val columnId: ColumnId,
    val cardId: CardId,
    val title: String,
    val description: String,
    val opId: OpId,
    val now: Instant,
) : Action,
    Undoable

/** Request to edit an existing card's content; undoable and persisted. */
public data class EditCard(
    val cardId: CardId,
    val title: String,
    val description: String,
    val opId: OpId,
    val now: Instant,
) : Action,
    Undoable

/** Request to delete a card; undoable and persisted. */
public data class DeleteCard(val cardId: CardId, val opId: OpId) :
    Action,
    Undoable

/** Signals that a card async op completed successfully. */
public data class CardOpSucceeded(val opId: OpId, val cardId: CardId) : Action

/** Signals that a card async op failed; carries the inverse op for rollback. */
public data class CardOpFailed(val opId: OpId, val cardId: CardId, val error: String, val inverse: InverseOp) : Action

/**
 * Per-op inverse — rides the queued SyncOp; reconstructed on Rejected (no middleware-side map).
 */
public sealed interface InverseOp {
    /** Inverse of a [CardMoveRequested]: move the card back to [to] column at [index]. */
    public data class MoveBack(val cardId: CardId, val to: ColumnId, val index: Int) : InverseOp

    /** Inverse of an [AddCard]: delete the card that was added. */
    public data class DeleteAdded(val cardId: CardId) : InverseOp

    /** Inverse of an [EditCard]: restore the previous card state. */
    public data class RestoreEdited(val prev: Card) : InverseOp

    /** Inverse of a [DeleteCard]: re-add the deleted card at its original position. */
    public data class ReAddDeleted(val card: Card, val column: ColumnId, val index: Int) : InverseOp
}
