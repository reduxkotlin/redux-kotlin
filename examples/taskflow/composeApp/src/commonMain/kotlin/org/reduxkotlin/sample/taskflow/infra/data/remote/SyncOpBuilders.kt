package org.reduxkotlin.sample.taskflow.infra.data.remote

import org.reduxkotlin.sample.taskflow.core.AddCard
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.DeleteCard
import org.reduxkotlin.sample.taskflow.core.EditCard
import org.reduxkotlin.sample.taskflow.core.InverseOp

/**
 * Builds a serializable [SyncOp.Move] from a [CardMoveRequested] domain action and its inverse.
 *
 * The inverse is supplied explicitly because the optimistic mutation actions do not carry
 * their own inverse (it is reconstructed by the sync layer); this keeps the wire op
 * self-contained for undo on rejection.
 *
 * @param inverse the domain inverse to embed for undo.
 */
public fun CardMoveRequested.toSyncOp(inverse: InverseOp): SyncOp.Move = SyncOp.Move(
    opId = opId.v,
    cardId = cardId.v,
    from = from.v,
    to = to.v,
    toIndex = toIndex,
    inverse = inverse.toDto(),
)

/**
 * Builds a serializable [SyncOp.Add] from an [AddCard] domain action, the resulting [card]
 * and its inverse.
 *
 * @param card the fully-materialized card the action produced.
 * @param inverse the domain inverse to embed for undo.
 */
public fun AddCard.toSyncOp(card: Card, inverse: InverseOp): SyncOp.Add = SyncOp.Add(
    opId = opId.v,
    cardId = cardId.v,
    columnId = columnId.v,
    card = card.toDto(),
    inverse = inverse.toDto(),
)

/**
 * Builds a serializable [SyncOp.Edit] from an [EditCard] domain action and its inverse.
 *
 * @param inverse the domain inverse to embed for undo.
 */
public fun EditCard.toSyncOp(inverse: InverseOp): SyncOp.Edit = SyncOp.Edit(
    opId = opId.v,
    cardId = cardId.v,
    title = title,
    description = description,
    nowMillis = now.toEpochMilliseconds(),
    inverse = inverse.toDto(),
)

/**
 * Builds a serializable [SyncOp.Delete] from a [DeleteCard] domain action and its inverse.
 *
 * @param inverse the domain inverse to embed for undo.
 */
public fun DeleteCard.toSyncOp(inverse: InverseOp): SyncOp.Delete = SyncOp.Delete(
    opId = opId.v,
    cardId = cardId.v,
    inverse = inverse.toDto(),
)
