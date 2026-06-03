package org.reduxkotlin.sample.taskflow.infra.data.remote

import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId

/**
 * A domain-typed change emitted by the remote API for the local store to apply.
 *
 * This is a runtime transfer type between the remote API and the local store; it is NOT
 * serialized over the wire (the wire format is [SyncOp]). It carries fully-decoded domain
 * objects so the store can apply changes directly.
 */
public sealed interface RemoteChange {
    /**
     * A card was created or updated remotely.
     *
     * @property card the current card state.
     * @property columnId column the card belongs to.
     * @property sortIndex position of the card within its column.
     */
    public data class CardUpserted(public val card: Card, public val columnId: ColumnId, public val sortIndex: Int) :
        RemoteChange

    /**
     * A card was deleted remotely.
     *
     * @property cardId id of the deleted card.
     */
    public data class CardDeleted(public val cardId: CardId) : RemoteChange

    /**
     * A column was created or updated remotely.
     *
     * @property boardId board the column belongs to.
     * @property column the current column state.
     */
    public data class ColumnUpserted(public val boardId: BoardId, public val column: Column) : RemoteChange

    /**
     * A board summary was created or updated remotely.
     *
     * @property summary the current board summary.
     */
    public data class BoardUpserted(public val summary: BoardSummary) : RemoteChange
}
