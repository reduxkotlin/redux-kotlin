package org.reduxkotlin.sample.taskflow.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Instant

// Always-present slot. board == null is the NotLoaded sentinel (reset by BoardClosed).
data class BoardModel(val board: Board? = null)

data class Board(val boardId: BoardId, val columns: PersistentList<Column>, val cards: PersistentMap<CardId, Card>)

data class Column(val id: ColumnId, val title: String, val cardIds: PersistentList<CardId>, val wipLimit: Int? = null)

data class Card(
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

sealed interface Attachment {
    data class Image(val url: String, val alt: String, val width: Int? = null, val height: Int? = null) : Attachment
    data class Link(
        val url: String,
        val title: String? = null,
        val description: String? = null,
        val imageUrl: String? = null,
    ) : Attachment
}

data class Label(val id: LabelId, val name: String, val color: Long)

// Pure helper used by the Board screen (Rule C) to bind per-column lists by ColumnId.
fun Board.columnById(id: ColumnId): Column? = columns.firstOrNull { it.id == id }
