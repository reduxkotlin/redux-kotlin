package org.reduxkotlin.sample.taskflow.render

import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.Serializable
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.Label
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.infra.SeedData

/*
 * Agent-facing JSON state contracts. These are deliberately NOT the internal `ModelState` models —
 * they are small, stable DTOs an agent authors directly, then mapped to real domain objects and
 * dispatched. This keeps the wire format simple (strings/ints/lists) and decoupled from internal
 * value classes / persistent collections.
 */

/** Settings screen state. Every field is optional; omitted fields keep the model default. */
@Serializable
internal data class SettingsSpec(
    val theme: String? = null,
    val online: Boolean? = null,
    val botEnabled: Boolean? = null,
    val failureRate: Float? = null,
    val latencyMinMs: Int? = null,
    val latencyMaxMs: Int? = null,
)

/** Board screen state: ordered columns, each with ordered cards. */
@Serializable
internal data class BoardSpec(val columns: List<ColumnSpec> = emptyList())

/** A board column. */
@Serializable
internal data class ColumnSpec(val name: String, val cards: List<CardSpec> = emptyList(), val wipLimit: Int? = null)

/** A card; [labels] reference label names (e.g. "backend"), [assignee] an account id (e.g. "ann"). */
@Serializable
internal data class CardSpec(
    val title: String,
    val description: String = "",
    val labels: List<String> = emptyList(),
    val assignee: String? = null,
)

/** Maps a [BoardSpec] to a real [Board], minting deterministic ids and resolving label names. */
internal fun buildBoardFromSpec(spec: BoardSpec): Board {
    val owner = SeedData.seededAccounts().first().owner.id
    val cards = mutableMapOf<CardId, Card>()
    val columns = spec.columns.mapIndexed { ci, col ->
        val cardIds = col.cards.mapIndexed { idx, c ->
            val id = CardId("c$ci-$idx")
            cards[id] = Card(
                id = id,
                title = c.title,
                description = c.description,
                labels = c.labels.map(::resolveLabel).toPersistentList(),
                assigneeId = c.assignee?.let { AccountId(it) },
                createdBy = owner,
                createdAt = SeedData.SEED_INSTANT,
                updatedAt = SeedData.SEED_INSTANT,
            )
            id
        }.toPersistentList()
        Column(id = ColumnId("col-$ci"), title = col.name, cardIds = cardIds, wipLimit = col.wipLimit)
    }.toPersistentList()
    return Board(boardId = BoardId("json-board"), columns = columns, cards = cards.toPersistentMap())
}

/** Resolves a label name to a seeded [Label], or synthesizes a neutral one if unknown. */
private fun resolveLabel(name: String): Label =
    SeedData.labels.firstOrNull { it.name == name } ?: Label(LabelId(name), name, DEFAULT_LABEL_COLOR)

private const val DEFAULT_LABEL_COLOR = 0xFFDDDDDDL
