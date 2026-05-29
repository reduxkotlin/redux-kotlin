package org.reduxkotlin.sample.taskflow.data

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.model.Attachment
import org.reduxkotlin.sample.taskflow.model.Board
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.Card
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.Column
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.Label
import org.reduxkotlin.sample.taskflow.model.LabelId
import kotlin.time.Instant

/**
 * Deterministic seed content shared by [org.reduxkotlin.sample.taskflow.data.local.LocalStore]
 * and (later) the fake remote backend. All timestamps use a single fixed [SEED_INSTANT] so tests
 * are reproducible; only runtime-created cards use the platform clock.
 *
 * Layout: three login accounts (ann/raj/mia) plus a non-login [bot] collaborator. Each account
 * owns one board with three columns (To Do / Doing / Done); the **Doing** column's WIP limit
 * equals its seeded card count, reproducing the at-limit state that drives the Rejected-conflict
 * test later. Cards include a markdown body, a picsum image attachment, a link attachment, labels,
 * and assignees spread across the owner and the bot.
 */
public object SeedData {
    /** Fixed seed timestamp (deterministic for tests). */
    public val SEED_INSTANT: Instant = Instant.fromEpochMilliseconds(1_716_000_000_000L)

    /** The non-login bot collaborator referenced on seeded cards. */
    public val bot: AccountSummary = AccountSummary(
        id = AccountId("bot"),
        displayName = "TaskBot",
        email = "bot@taskflow.dev",
        avatarUrl = "https://api.dicebear.com/9.x/bottts/png?seed=taskflow",
    )

    /** The three login accounts. */
    public val accounts: PersistentList<AccountSummary> = persistentListOf(
        account("ann", "Ann Patterson"),
        account("raj", "Raj Mehta"),
        account("mia", "Mia Chen"),
    )

    /** The six semantic label names + colors (from the hi-fi spec's `semantic.labels`). */
    private val labelPalette: List<Pair<String, Long>> = listOf(
        "backend" to 0xFFDCE6FF,
        "frontend" to 0xFFD7ECDD,
        "p1" to 0xFFFFDBE3,
        "docs" to 0xFFFDE9CF,
        "infra" to 0xFFD6EEF0,
        "design" to 0xFFECE0FF,
    )

    /**
     * The six semantic labels with globally-keyed ids, for palette / UI reference. Seeded boards
     * use *board-scoped* label ids (see [labelsFor]) because the `label` table is keyed by id and
     * each board owns its own label rows.
     */
    public val labels: PersistentList<Label> = labelPalette
        .map { (name, color) -> Label(LabelId(name), name, color) }
        .let { persistentListOf(*it.toTypedArray()) }

    /** The six semantic labels scoped to one board (label id = `{boardKey}-{name}`). */
    private fun labelsFor(boardKey: String): PersistentList<Label> = labelPalette
        .map { (name, color) -> Label(LabelId("$boardKey-$name"), name, color) }
        .let { persistentListOf(*it.toTypedArray()) }

    private val boardColors: Map<String, Long> = mapOf(
        "ann" to 0xFF4A3FB8,
        "raj" to 0xFF7E5260,
        "mia" to 0xFF1E8A5B,
    )

    /** A seeded account paired with its single board and collaborators. */
    public data class SeededAccount(
        /** The account owner summary. */
        public val owner: AccountSummary,
        /** The account's single board. */
        public val board: Board,
        /** Collaborators referenceable on this account (owner + bot). */
        public val collaborators: PersistentList<AccountSummary>,
    )

    /** Returns the full deterministic content for every login account. */
    public fun seededAccounts(): PersistentList<SeededAccount> = accounts.map { owner ->
        SeededAccount(
            owner = owner,
            board = boardFor(owner),
            collaborators = persistentListOf(owner, bot),
        )
    }.toPersistentList()

    /** The board accent color for [accountId] (indigo family; falls back to the indigo seed). */
    public fun boardColor(accountId: AccountId): Long = boardColors[accountId.v] ?: 0xFF4A3FB8

    private fun account(id: String, name: String): AccountSummary = AccountSummary(
        id = AccountId(id),
        displayName = name,
        email = "$id@taskflow.dev",
        avatarUrl = "https://api.dicebear.com/9.x/avataaars/png?seed=$id",
    )

    private fun boardFor(owner: AccountSummary): Board {
        val a = owner.id.v
        val cardList = seedCards(owner)
        val ids = cardList.map { it.id }
        val cards = cardList.associateBy { it.id }.toPersistentMap()

        // Doing column's wipLimit == its seeded card count (2) -> at-limit, for the Rejected test.
        val columns = persistentListOf(
            Column(ColumnId("$a-todo"), "To Do", persistentListOf(ids[0], ids[1]), wipLimit = null),
            Column(ColumnId("$a-doing"), "Doing", persistentListOf(ids[2], ids[3]), wipLimit = 2),
            Column(ColumnId("$a-done"), "Done", persistentListOf(ids[4], ids[5]), wipLimit = null),
        )
        return Board(boardId = BoardId("$a-board"), columns = columns, cards = cards)
    }

    /** The six seeded cards for [owner], in column order (To Do, To Do, Doing, Doing, Done, Done). */
    private fun seedCards(owner: AccountSummary): List<Card> {
        val a = owner.id.v
        val l = labelsFor(a)
        val linkRfc = Attachment.Link(
            url = "https://example.com/rfc",
            title = "API RFC",
            description = "Proposed API contract",
        )
        val image = Attachment.Image(
            url = "https://picsum.photos/seed/$a-3/600/400",
            alt = "board mockup",
            width = 600,
            height = 400,
        )
        val markdown = "## Goals\n\n- Define endpoints\n- Document **auth** flow\n\n" +
            "See [the RFC](https://example.com/rfc) for context."
        return listOf(
            CardSpec(
                "1",
                "Design the API surface",
                markdown,
                owner.id,
                persistentListOf(l[0], l[3]),
                persistentListOf(linkRfc),
            ),
            CardSpec(
                "2",
                "Polish onboarding copy",
                "Tighten the welcome screen wording.",
                owner.id,
                persistentListOf(l[3]),
            ),
            CardSpec(
                "3",
                "Build the kanban board",
                "Wire columns + cards with granular subscriptions.",
                owner.id,
                persistentListOf(l[1], l[2]),
                persistentListOf(image),
            ),
            CardSpec(
                "4",
                "Investigate flaky sync",
                "Bot is reporting intermittent deferrals.",
                bot.id,
                persistentListOf(l[4]),
            ),
            CardSpec("5", "Set up CI", "GitHub Actions matrix is green.", owner.id, persistentListOf(l[4])),
            CardSpec("6", "Pick a color scheme", "Locked the indigo seed.", bot.id, persistentListOf(l[5])),
        ).map { it.toCard(a) }
    }

    private data class CardSpec(
        val n: String,
        val title: String,
        val description: String,
        val assignee: AccountId,
        val labels: PersistentList<Label>,
        val attachments: PersistentList<Attachment> = persistentListOf(),
    ) {
        fun toCard(accountKey: String): Card = Card(
            id = CardId("$accountKey-$n"),
            title = title,
            description = description,
            attachments = attachments,
            labels = labels,
            assigneeId = assignee,
            createdBy = AccountId(accountKey),
            createdAt = SEED_INSTANT,
            updatedAt = SEED_INSTANT,
        )
    }
}
