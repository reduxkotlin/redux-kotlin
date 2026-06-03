package org.reduxkotlin.sample.taskflow.core

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

/** A board containing ordered columns and a map of all cards. */
public data class Board(
    val boardId: BoardId,
    val columns: PersistentList<Column>,
    val cards: PersistentMap<CardId, Card>,
)

/** A column within a board, containing an ordered list of card IDs. */
public data class Column(
    val id: ColumnId,
    val title: String,
    val cardIds: PersistentList<CardId>,
    val wipLimit: Int? = null,
)

/** A card within a board column. */
public data class Card(
    val id: CardId,
    val title: String,
    val description: String, // Markdown
    val attachments: PersistentList<Attachment> = persistentListOf(),
    val labels: PersistentList<Label> = persistentListOf(),
    val assigneeId: AccountId? = null,
    val createdBy: AccountId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** An attachment on a card — either an image or a link preview. */
public sealed interface Attachment {
    /** An image attachment with optional dimensions. */
    public data class Image(val url: String, val alt: String, val width: Int? = null, val height: Int? = null) :
        Attachment

    /** A link attachment with optional OG-style metadata. */
    public data class Link(
        val url: String,
        val title: String? = null,
        val description: String? = null,
        val imageUrl: String? = null,
    ) : Attachment
}

/** A label that can be applied to a card. */
public data class Label(val id: LabelId, val name: String, val color: Long)
