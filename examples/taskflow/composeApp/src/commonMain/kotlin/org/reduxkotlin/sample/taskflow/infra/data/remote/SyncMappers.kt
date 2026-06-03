package org.reduxkotlin.sample.taskflow.infra.data.remote

import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Attachment
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.InverseOp
import org.reduxkotlin.sample.taskflow.core.Label
import org.reduxkotlin.sample.taskflow.core.LabelId
import kotlin.time.Instant

/**
 * Converts this domain [Label] into its serializable [LabelDto].
 */
public fun Label.toDto(): LabelDto = LabelDto(id = id.v, name = name, color = color)

/**
 * Converts this [LabelDto] back into a domain [Label].
 */
public fun LabelDto.toDomain(): Label = Label(id = LabelId(id), name = name, color = color)

/**
 * Converts this domain [Attachment] into its serializable [AttachmentDto].
 */
public fun Attachment.toDto(): AttachmentDto = when (this) {
    is Attachment.Image -> AttachmentDto.Image(url = url, alt = alt, width = width, height = height)

    is Attachment.Link -> AttachmentDto.Link(
        url = url,
        title = title,
        description = description,
        imageUrl = imageUrl,
    )
}

/**
 * Converts this [AttachmentDto] back into a domain [Attachment].
 */
public fun AttachmentDto.toDomain(): Attachment = when (this) {
    is AttachmentDto.Image -> Attachment.Image(url = url, alt = alt, width = width, height = height)

    is AttachmentDto.Link -> Attachment.Link(
        url = url,
        title = title,
        description = description,
        imageUrl = imageUrl,
    )
}

/**
 * Converts this domain [Card] into its serializable [CardDto].
 *
 * Timestamps are mapped to epoch milliseconds and immutable collections to plain lists.
 */
public fun Card.toDto(): CardDto = CardDto(
    id = id.v,
    title = title,
    description = description,
    attachments = attachments.map { it.toDto() },
    labels = labels.map { it.toDto() },
    assigneeId = assigneeId?.v,
    createdBy = createdBy.v,
    createdAtMillis = createdAt.toEpochMilliseconds(),
    updatedAtMillis = updatedAt.toEpochMilliseconds(),
)

/**
 * Converts this [CardDto] back into a domain [Card].
 *
 * Epoch milliseconds are mapped back to [Instant] and lists to immutable collections.
 */
public fun CardDto.toDomain(): Card = Card(
    id = CardId(id),
    title = title,
    description = description,
    attachments = attachments.map { it.toDomain() }.toPersistentList(),
    labels = labels.map { it.toDomain() }.toPersistentList(),
    assigneeId = assigneeId?.let(::AccountId),
    createdBy = AccountId(createdBy),
    createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
    updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
)

/**
 * Converts this domain [InverseOp] into its serializable [InverseOpDto].
 */
public fun InverseOp.toDto(): InverseOpDto = when (this) {
    is InverseOp.MoveBack -> InverseOpDto.MoveBack(
        cardId = cardId.v,
        to = to.v,
        index = index,
    )

    is InverseOp.DeleteAdded -> InverseOpDto.DeleteAdded(cardId = cardId.v)

    is InverseOp.RestoreEdited -> InverseOpDto.RestoreEdited(prev = prev.toDto())

    is InverseOp.ReAddDeleted -> InverseOpDto.ReAddDeleted(
        card = card.toDto(),
        column = column.v,
        index = index,
    )
}

/**
 * Converts this [InverseOpDto] back into a domain [InverseOp].
 */
public fun InverseOpDto.toDomain(): InverseOp = when (this) {
    is InverseOpDto.MoveBack -> InverseOp.MoveBack(
        cardId = CardId(cardId),
        to = ColumnId(to),
        index = index,
    )

    is InverseOpDto.DeleteAdded -> InverseOp.DeleteAdded(cardId = CardId(cardId))

    is InverseOpDto.RestoreEdited -> InverseOp.RestoreEdited(prev = prev.toDomain())

    is InverseOpDto.ReAddDeleted -> InverseOp.ReAddDeleted(
        card = card.toDomain(),
        column = ColumnId(column),
        index = index,
    )
}
