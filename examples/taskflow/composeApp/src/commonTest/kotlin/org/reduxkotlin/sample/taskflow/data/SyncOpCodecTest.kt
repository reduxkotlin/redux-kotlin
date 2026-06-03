package org.reduxkotlin.sample.taskflow.data

import kotlinx.collections.immutable.persistentListOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AddCard
import org.reduxkotlin.sample.taskflow.core.Attachment
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.DeleteCard
import org.reduxkotlin.sample.taskflow.core.EditCard
import org.reduxkotlin.sample.taskflow.core.InverseOp
import org.reduxkotlin.sample.taskflow.core.Label
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.data.remote.SyncOp
import org.reduxkotlin.sample.taskflow.data.remote.decodeSyncOp
import org.reduxkotlin.sample.taskflow.data.remote.encodeToPayload
import org.reduxkotlin.sample.taskflow.data.remote.toSyncOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SyncOpCodecTest {

    private fun sampleCard(id: String): Card = Card(
        id = CardId(id),
        title = "Title $id",
        description = "Description $id",
        attachments = persistentListOf(
            Attachment.Image(url = "https://img/$id.png", alt = "alt", width = 320, height = 240),
            Attachment.Link(
                url = "https://link/$id",
                title = "link title",
                description = "link desc",
                imageUrl = "https://link/$id/preview.png",
            ),
        ),
        labels = persistentListOf(
            Label(id = LabelId("lbl-1"), name = "bug", color = 0xFFFF0000L),
            Label(id = LabelId("lbl-2"), name = "feature", color = 0xFF00FF00L),
        ),
        assigneeId = AccountId("user-2"),
        createdBy = AccountId("user-1"),
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_100_000L),
    )

    private fun assertRoundTrips(op: SyncOp) {
        val decoded = decodeSyncOp(op.encodeToPayload())
        assertEquals(op, decoded)
    }

    @Test
    fun moveRoundTrips() {
        val op = CardMoveRequested(
            cardId = CardId("card-1"),
            from = ColumnId("col-a"),
            to = ColumnId("col-b"),
            toIndex = 3,
            opId = OpId("op-move-1"),
        ).toSyncOp(
            inverse = InverseOp.MoveBack(cardId = CardId("card-1"), to = ColumnId("col-a"), index = 1),
        )

        assertRoundTrips(op)
    }

    @Test
    fun addRoundTrips() {
        val card = sampleCard("card-2")
        val op = AddCard(
            columnId = ColumnId("col-b"),
            cardId = card.id,
            title = card.title,
            description = card.description,
            opId = OpId("op-add-1"),
            now = card.createdAt,
        ).toSyncOp(
            card = card,
            inverse = InverseOp.DeleteAdded(cardId = card.id),
        )

        assertRoundTrips(op)
    }

    @Test
    fun editRoundTrips() {
        val prev = sampleCard("card-3")
        val op = EditCard(
            cardId = prev.id,
            title = "New title",
            description = "New description",
            opId = OpId("op-edit-1"),
            now = Instant.fromEpochMilliseconds(1_700_000_200_000L),
        ).toSyncOp(
            inverse = InverseOp.RestoreEdited(prev = prev),
        )

        assertRoundTrips(op)
    }

    @Test
    fun deleteRoundTrips() {
        val card = sampleCard("card-4")
        val op = DeleteCard(
            cardId = card.id,
            opId = OpId("op-delete-1"),
        ).toSyncOp(
            inverse = InverseOp.ReAddDeleted(card = card, column = ColumnId("col-c"), index = 2),
        )

        assertRoundTrips(op)
    }
}
