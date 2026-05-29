package org.reduxkotlin.sample.taskflow.data.remote

import kotlinx.serialization.Serializable

/**
 * A serializable card-mutation operation transferred to/from the remote sync backend.
 *
 * Each variant mirrors a domain card mutation but uses only primitive / [List] / [Long]
 * fields so it can be (de)serialized with kotlinx-serialization without relying on
 * serializers for `kotlin.time.Instant` or the immutable-collection types used by the
 * in-memory domain model.
 */
@Serializable
public sealed interface SyncOp {
    /**
     * Stable, client-generated id for this operation (used for dedup / idempotency).
     */
    public val opId: String

    /**
     * Id of the card this operation affects.
     */
    public val cardId: String

    /**
     * Serializable inverse used to revert this op when the backend rejects it.
     */
    public val inverse: InverseOpDto

    /**
     * Serializable form of a card move between columns.
     *
     * @property opId stable operation id.
     * @property cardId card being moved.
     * @property from source column id.
     * @property to destination column id.
     * @property toIndex destination index within [to].
     * @property inverse inverse operation for undo.
     */
    @Serializable
    public data class Move(
        public override val opId: String,
        public override val cardId: String,
        public val from: String,
        public val to: String,
        public val toIndex: Int,
        public override val inverse: InverseOpDto,
    ) : SyncOp

    /**
     * Serializable form of a card insertion into a column.
     *
     * @property opId stable operation id.
     * @property cardId id of the new card.
     * @property columnId destination column id.
     * @property card the card payload to insert.
     * @property inverse inverse operation for undo.
     */
    @Serializable
    public data class Add(
        public override val opId: String,
        public override val cardId: String,
        public val columnId: String,
        public val card: CardDto,
        public override val inverse: InverseOpDto,
    ) : SyncOp

    /**
     * Serializable form of a card title / description edit.
     *
     * @property opId stable operation id.
     * @property cardId card being edited.
     * @property title new title.
     * @property description new description.
     * @property nowMillis edit timestamp as epoch milliseconds.
     * @property inverse inverse operation for undo.
     */
    @Serializable
    public data class Edit(
        public override val opId: String,
        public override val cardId: String,
        public val title: String,
        public val description: String,
        public val nowMillis: Long,
        public override val inverse: InverseOpDto,
    ) : SyncOp

    /**
     * Serializable form of a card deletion.
     *
     * @property opId stable operation id.
     * @property cardId card being deleted.
     * @property inverse inverse operation for undo.
     */
    @Serializable
    public data class Delete(
        public override val opId: String,
        public override val cardId: String,
        public override val inverse: InverseOpDto,
    ) : SyncOp
}

/**
 * Serializable form of a [org.reduxkotlin.sample.taskflow.model.Card].
 *
 * @property id card id.
 * @property title card title.
 * @property description card body text (Markdown).
 * @property attachments serializable attachments.
 * @property labels serializable labels.
 * @property assigneeId optional assigned account id.
 * @property createdBy id of the account that created the card.
 * @property createdAtMillis creation timestamp as epoch milliseconds.
 * @property updatedAtMillis last-update timestamp as epoch milliseconds.
 */
@Serializable
public data class CardDto(
    public val id: String,
    public val title: String,
    public val description: String,
    public val attachments: List<AttachmentDto>,
    public val labels: List<LabelDto>,
    public val assigneeId: String?,
    public val createdBy: String,
    public val createdAtMillis: Long,
    public val updatedAtMillis: Long,
)

/**
 * Serializable form of a [org.reduxkotlin.sample.taskflow.model.Attachment].
 */
@Serializable
public sealed interface AttachmentDto {
    /**
     * Serializable image attachment.
     *
     * @property url location of the image.
     * @property alt alternate text describing the image.
     * @property width optional intrinsic width in pixels.
     * @property height optional intrinsic height in pixels.
     */
    @Serializable
    public data class Image(
        public val url: String,
        public val alt: String,
        public val width: Int?,
        public val height: Int?,
    ) : AttachmentDto

    /**
     * Serializable link attachment with optional preview metadata.
     *
     * @property url target URL.
     * @property title optional link title.
     * @property description optional link description.
     * @property imageUrl optional preview image URL.
     */
    @Serializable
    public data class Link(
        public val url: String,
        public val title: String?,
        public val description: String?,
        public val imageUrl: String?,
    ) : AttachmentDto
}

/**
 * Serializable form of a [org.reduxkotlin.sample.taskflow.model.Label].
 *
 * @property id label id.
 * @property name label text.
 * @property color ARGB color value.
 */
@Serializable
public data class LabelDto(public val id: String, public val name: String, public val color: Long)

/**
 * Serializable form of a domain [org.reduxkotlin.sample.taskflow.action.InverseOp].
 *
 * Captures how to undo the [SyncOp] it is attached to.
 */
@Serializable
public sealed interface InverseOpDto {
    /**
     * Moves a card back to a column at an index (undo of [SyncOp.Move]).
     *
     * @property cardId card to move back.
     * @property to column to move back into.
     * @property index index to restore at.
     */
    @Serializable
    public data class MoveBack(public val cardId: String, public val to: String, public val index: Int) : InverseOpDto

    /**
     * Deletes a card that was added (undo of [SyncOp.Add]).
     *
     * @property cardId card to delete.
     */
    @Serializable
    public data class DeleteAdded(public val cardId: String) : InverseOpDto

    /**
     * Restores a card to a previous state (undo of [SyncOp.Edit]).
     *
     * @property prev previous card state.
     */
    @Serializable
    public data class RestoreEdited(public val prev: CardDto) : InverseOpDto

    /**
     * Re-adds a deleted card (undo of [SyncOp.Delete]).
     *
     * @property card card to re-add.
     * @property column column to re-add into.
     * @property index index to re-add at.
     */
    @Serializable
    public data class ReAddDeleted(public val card: CardDto, public val column: String, public val index: Int) :
        InverseOpDto
}
