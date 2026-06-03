package org.reduxkotlin.sample.taskflow.data.local

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.ActivityEntry
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel
import org.reduxkotlin.sample.taskflow.core.Attachment
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.FakeServiceConfig
import org.reduxkotlin.sample.taskflow.core.Label
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.core.Theme
import org.reduxkotlin.sample.taskflow.data.SeedData
import org.reduxkotlin.sample.taskflow.data.remote.RemoteChange
import org.reduxkotlin.sample.taskflow.data.remote.SyncOp
import org.reduxkotlin.sample.taskflow.data.remote.decodeSyncOp
import org.reduxkotlin.sample.taskflow.data.remote.encodeToPayload
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import kotlin.time.Instant

private const val ATTACHMENT_KIND_IMAGE = "image"
private const val ATTACHMENT_KIND_LINK = "link"
private const val ACTIVITY_LIMIT = 50L
private const val ARGB_MASK = 0xFFFFFFFFL

private const val ROUTE_BOARD_LIST = "BoardList"
private const val ROUTE_BOARD = "Board"
private const val ROUTE_PROFILE = "Profile"
private const val ROUTE_SETTINGS = "Settings"

/**
 * The durable [LocalStore], backed by the generated SQLDelight [TaskFlowDb]. Instant reads/writes
 * with no artificial latency. Multi-row writes run inside `db.transaction { }` for atomicity; reads
 * use the `generateAsync` `awaitAs*` suspend extensions, which complete synchronously over a
 * synchronous driver.
 */
@Suppress("TooManyFunctions") // implements the full LocalStore contract + private mapping helpers
public class SqlDelightLocalStore(private val db: TaskFlowDb) : LocalStore {

    private val q get() = db.taskFlowDbQueries

    // ---- reads ----

    override suspend fun loadAccounts(): PersistentList<AccountSummary> = q.selectAccounts().awaitAsList()
        .map { AccountSummary(it.id, it.displayName, it.email, it.avatarUrl) }
        .toPersistentList()

    override suspend fun loadSettings(): AppSettingsModel {
        val row = q.selectSettings().awaitAsOneOrNull() ?: return AppSettingsModel()
        return AppSettingsModel(
            theme = Theme.valueOf(row.theme),
            language = row.language,
            fakeService = FakeServiceConfig(
                latencyMinMs = row.latencyMinMs,
                latencyMaxMs = row.latencyMaxMs,
                failureRate = row.failureRate.toFloat(),
                botEnabled = row.botEnabled,
                botIntervalMs = row.botIntervalMs,
                online = row.online,
                syncIntervalMs = row.syncIntervalMs,
            ),
        )
    }

    override suspend fun loadActiveAccountId(): AccountId? = q.selectSettings().awaitAsOneOrNull()?.activeAccountId

    override suspend fun loadBoardList(accountId: AccountId): PersistentList<BoardSummary> =
        q.selectBoardList(accountId).awaitAsList()
            .map {
                BoardSummary(
                    id = it.id,
                    name = it.name,
                    color = it.color.toLong() and ARGB_MASK,
                    cardCount = it.cardCount.toInt(),
                    doneCount = it.doneCount.toInt(),
                    updatedAt = it.updatedAt,
                )
            }
            .toPersistentList()

    override suspend fun loadBoard(boardId: BoardId): Board? {
        val boardRow = q.selectBoard(boardId).awaitAsOneOrNull() ?: return null

        val columnRows = q.selectColumnsByBoard(boardId).awaitAsList() // ordered by sortIndex
        val cardRows = q.selectCardsByBoard(boardId).awaitAsList()
        val labelRows = q.selectLabelsByBoard(boardId).awaitAsList()
        val cardLabelRows = q.selectCardLabelsByBoard(boardId).awaitAsList()

        val labelsById: Map<LabelId, Label> =
            labelRows.associate { it.id to Label(it.id, it.name, it.color.toLong() and ARGB_MASK) }
        val labelsByCard: Map<CardId, List<Label>> =
            cardLabelRows.groupBy({ it.cardId }, { it.labelId })
                .mapValues { (_, ids) -> ids.mapNotNull(labelsById::get) }

        val cards = cardRows.associate { row ->
            val attachments = q.selectAttachmentsByCard(row.id).awaitAsList().map(::toAttachment)
            row.id to row.toCard(
                attachments = attachments.toPersistentList(),
                labels = (labelsByCard[row.id] ?: emptyList()).toPersistentList(),
            )
        }.toPersistentMap()

        // Order each column's cardIds by the card's sortIndex.
        val cardsByColumn: Map<ColumnId, List<org.reduxkotlin.sample.taskflow.db.Card>> =
            cardRows.sortedBy { it.sortIndex }.groupBy { it.columnId }
        val columns = columnRows.map { col ->
            Column(
                id = col.id,
                title = col.title,
                cardIds = (cardsByColumn[col.id] ?: emptyList()).map { it.id }.toPersistentList(),
                wipLimit = col.wipLimit,
            )
        }.toPersistentList()

        return Board(boardId = boardRow.id, columns = columns, cards = cards)
    }

    override suspend fun loadNav(accountId: AccountId): NavModel {
        val row = q.selectNav(accountId).awaitAsOneOrNull() ?: return NavModel()
        // Reconstruct the stack from the persisted (route_tag, boardId, openCardId) triple.
        // Board(id) sits on top of BoardList so back from a board returns to the list (matches the
        // in-memory navReducer's Navigate(Board) rule). An open card pushes a CardDetail(View) frame.
        // We do not persist edit mode (always restore to view) or the transient ComposeCard state.
        val base: PersistentList<Route> = when (row.route) {
            ROUTE_BOARD ->
                row.boardId
                    ?.let { persistentListOf<Route>(Route.BoardList, Route.Board(it)) }
                    ?: persistentListOf<Route>(Route.BoardList)

            ROUTE_PROFILE -> persistentListOf<Route>(Route.Profile)

            ROUTE_SETTINGS -> persistentListOf<Route>(Route.Settings)

            else -> persistentListOf<Route>(Route.BoardList)
        }
        val stack = row.openCardId
            ?.takeIf { row.route == ROUTE_BOARD && row.boardId != null }
            ?.let { base.add(Route.CardDetail(it)) }
            ?: base
        return NavModel(stack)
    }

    override suspend fun loadCollaborators(accountId: AccountId): PersistentMap<AccountId, AccountSummary> =
        q.selectCollaborators(accountId).awaitAsList()
            .associate { it.id to AccountSummary(it.id, it.displayName, it.email, it.avatarUrl) }
            .toPersistentMap()

    override suspend fun loadActivity(accountId: AccountId): PersistentList<ActivityEntry> =
        q.selectRecentActivity(accountId, ACTIVITY_LIMIT).awaitAsList()
            .map { ActivityEntry(it.id, it.actorId, it.summary, it.timestamp) }
            .toPersistentList()

    // ---- writes ----

    override suspend fun saveSettings(settings: AppSettingsModel) {
        val active = q.selectSettings().awaitAsOneOrNull()?.activeAccountId
        val f = settings.fakeService
        q.upsertSettings(
            theme = settings.theme.name,
            language = settings.language,
            latencyMinMs = f.latencyMinMs,
            latencyMaxMs = f.latencyMaxMs,
            failureRate = f.failureRate.toDouble(),
            botEnabled = f.botEnabled,
            botIntervalMs = f.botIntervalMs,
            online = f.online,
            syncIntervalMs = f.syncIntervalMs,
            activeAccountId = active,
        )
    }

    override suspend fun saveActiveAccountId(accountId: AccountId?) {
        if (q.selectSettings().awaitAsOneOrNull() == null) saveSettings(AppSettingsModel())
        q.setActiveAccountId(accountId)
    }

    override suspend fun saveNav(accountId: AccountId, nav: NavModel) {
        // Persist the lowest-level destination (the most-recent TopLevel) + the active board id +
        // any open card. Card-detail edit mode and the transient ComposeCard are intentionally
        // dropped — those are session state, not durable nav.
        val baseTopLevel = nav.stack.last { it is Route.TopLevel } as Route.TopLevel
        val tag: String
        val boardId: BoardId?
        when (baseTopLevel) {
            is Route.Board -> {
                tag = ROUTE_BOARD
                boardId = baseTopLevel.boardId
            }

            Route.BoardList -> {
                tag = ROUTE_BOARD_LIST
                boardId = null
            }

            Route.Profile -> {
                tag = ROUTE_PROFILE
                boardId = null
            }

            Route.Settings -> {
                tag = ROUTE_SETTINGS
                boardId = null
            }
        }
        q.upsertNav(accountId, tag, boardId, nav.openCardId)
    }

    override suspend fun moveCard(boardId: BoardId, cardId: CardId, toColumn: ColumnId, toIndex: Int) {
        db.transaction {
            val now = q.selectCard(cardId).awaitAsOne().updatedAt
            q.moveCard(columnId = toColumn, sortIndex = toIndex, updatedAt = now, id = cardId)
            normalizeColumn(boardId, toColumn, pin = cardId, pinIndex = toIndex)
        }
    }

    override suspend fun addCard(boardId: BoardId, card: Card, columnId: ColumnId, index: Int) {
        db.transaction {
            q.insertCard(
                id = card.id,
                boardId = boardId,
                columnId = columnId,
                sortIndex = index,
                title = card.title,
                description = card.description,
                assigneeId = card.assigneeId,
                createdBy = card.createdBy,
                createdAt = card.createdAt,
                updatedAt = card.updatedAt,
            )
            writeAttachments(card)
            writeLabels(card)
            normalizeColumn(boardId, columnId, pin = card.id, pinIndex = index)
        }
    }

    override suspend fun createBoard(
        accountId: AccountId,
        boardId: BoardId,
        name: String,
        color: Long,
        updatedAt: Instant,
        columns: List<Column>,
    ) {
        db.transaction {
            q.upsertBoard(
                id = boardId,
                accountId = accountId,
                name = name,
                color = (color and ARGB_MASK).toInt(),
                updatedAt = updatedAt,
            )
            columns.forEachIndexed { i, col -> q.upsertColumn(col.id, boardId, col.title, col.wipLimit, i) }
        }
    }

    override suspend fun addColumn(boardId: BoardId, column: Column, sortIndex: Int) {
        q.upsertColumn(column.id, boardId, column.title, column.wipLimit, sortIndex)
    }

    override suspend fun editCard(cardId: CardId, title: String, description: String, updatedAt: Instant) {
        val existing = q.selectCard(cardId).awaitAsOneOrNull() ?: return
        q.updateCard(
            title = title,
            description = description,
            assigneeId = existing.assigneeId,
            updatedAt = updatedAt,
            id = cardId,
        )
    }

    override suspend fun deleteCard(cardId: CardId) {
        db.transaction {
            q.deleteAttachmentsByCard(cardId)
            q.deleteCardLabelsByCard(cardId)
            q.deleteCard(cardId)
        }
    }

    override suspend fun recordActivity(accountId: AccountId, entry: ActivityEntry) {
        q.insertActivity(entry.id, accountId, entry.actorId, entry.summary, entry.timestamp)
    }

    // ---- outbound sync queue ----

    override suspend fun enqueue(accountId: AccountId, op: SyncOp) {
        q.enqueueOp(
            opId = OpId(op.opId),
            accountId = accountId,
            kind = op::class.simpleName ?: "SyncOp",
            payload = op.encodeToPayload(),
            createdAt = SeedData.SEED_INSTANT,
            attempts = 0,
        )
    }

    override suspend fun pendingOps(accountId: AccountId): List<SyncOp> =
        q.selectPendingOps(accountId).awaitAsList().map { decodeSyncOp(it.payload) }

    override suspend fun markSynced(opId: OpId) {
        q.deleteOp(opId)
    }

    override suspend fun incrementAttempts(opId: OpId) {
        val current = q.selectOp(opId).awaitAsOneOrNull()?.attempts ?: return
        q.updateOpAttempts(current + 1, opId)
    }

    // ---- remote merge (last-write-wins) ----

    override suspend fun applyRemote(changes: List<RemoteChange>) {
        if (changes.isEmpty()) return
        db.transaction {
            changes.forEach { change -> applyChange(change) }
        }
    }

    private suspend fun applyChange(change: RemoteChange) {
        when (change) {
            is RemoteChange.CardUpserted -> {
                val c = change.card
                val board = boardIdOfCard(c.id) ?: return
                q.upsertCard(
                    id = c.id,
                    boardId = board,
                    columnId = change.columnId,
                    sortIndex = change.sortIndex,
                    title = c.title,
                    description = c.description,
                    assigneeId = c.assigneeId,
                    createdBy = c.createdBy,
                    createdAt = c.createdAt,
                    updatedAt = c.updatedAt,
                )
                writeAttachments(c)
                writeLabels(c)
            }

            is RemoteChange.CardDeleted -> {
                q.deleteAttachmentsByCard(change.cardId)
                q.deleteCardLabelsByCard(change.cardId)
                q.deleteCard(change.cardId)
            }

            is RemoteChange.ColumnUpserted -> {
                val col = change.column
                q.upsertColumn(col.id, change.boardId, col.title, col.wipLimit, 0)
            }

            is RemoteChange.BoardUpserted -> {
                val s = change.summary
                // accountId is unknown from a summary-only change; preserve the existing owner.
                val owner = q.selectBoard(s.id).awaitAsOneOrNull()?.accountId ?: return
                q.upsertBoard(
                    id = s.id,
                    accountId = owner,
                    name = s.name,
                    color = (s.color and ARGB_MASK).toInt(),
                    updatedAt = s.updatedAt,
                )
            }
        }
    }

    override suspend fun lastSyncedAt(accountId: AccountId): Instant? =
        q.selectSyncMeta(accountId).awaitAsOneOrNull()?.lastSyncedAt

    override suspend fun setLastSyncedAt(accountId: AccountId, at: Instant?) {
        q.upsertSyncMeta(accountId, at)
    }

    // ---- seed ----

    override suspend fun ensureSeeded() {
        if (q.countAccounts().awaitAsOne() > 0L) return
        db.transaction {
            saveSettings(AppSettingsModel())
            SeedData.seededAccounts().forEach { seeded -> seedAccount(seeded) }
        }
    }

    private suspend fun seedAccount(seeded: SeedData.SeededAccount) {
        val owner = seeded.owner
        q.upsertAccount(owner.id, owner.displayName, owner.email, owner.avatarUrl)
        seeded.collaborators.forEach { c ->
            q.upsertCollaborator(owner.id, c.id, c.displayName, c.email, c.avatarUrl)
        }
        val board = seeded.board
        q.upsertBoard(
            id = board.boardId,
            accountId = owner.id,
            name = "${owner.displayName.substringBefore(' ')}'s Board",
            color = (SeedData.boardColor(owner.id) and ARGB_MASK).toInt(),
            updatedAt = SeedData.SEED_INSTANT,
        )
        // Labels are board-scoped; seed each board's own catalog from the labels its cards use.
        board.cards.values
            .flatMap { it.labels }
            .distinctBy { it.id }
            .forEach { l -> q.upsertLabel(l.id, board.boardId, l.name, (l.color and ARGB_MASK).toInt()) }
        board.columns.forEachIndexed { ci, col ->
            q.upsertColumn(col.id, board.boardId, col.title, col.wipLimit, ci)
            col.cardIds.forEachIndexed { idx, cardId ->
                val card = board.cards.getValue(cardId)
                q.insertCard(
                    id = card.id,
                    boardId = board.boardId,
                    columnId = col.id,
                    sortIndex = idx,
                    title = card.title,
                    description = card.description,
                    assigneeId = card.assigneeId,
                    createdBy = card.createdBy,
                    createdAt = card.createdAt,
                    updatedAt = card.updatedAt,
                )
                writeAttachments(card)
                writeLabels(card)
            }
        }
    }

    // ---- helpers ----

    /** Re-sequences a column's `sortIndex` to 0..n-1, placing [pin] at [pinIndex] (clamped). */
    private suspend fun normalizeColumn(boardId: BoardId, columnId: ColumnId, pin: CardId, pinIndex: Int) {
        val inColumn = q.selectCardsByBoard(boardId).awaitAsList()
            .filter { it.columnId == columnId }
            .sortedBy { it.sortIndex }
        val others = inColumn.filter { it.id != pin }.map { it.id }
        val clamped = pinIndex.coerceIn(0, others.size)
        val ordered = others.toMutableList().apply { add(clamped, pin) }
        ordered.forEachIndexed { i, id ->
            val row = inColumn.first { it.id == id }
            q.moveCard(columnId = columnId, sortIndex = i, updatedAt = row.updatedAt, id = id)
        }
    }

    private suspend fun writeAttachments(card: Card) {
        q.deleteAttachmentsByCard(card.id)
        card.attachments.forEachIndexed { i, a ->
            when (a) {
                is Attachment.Image -> q.insertAttachment(
                    cardId = card.id,
                    kind = ATTACHMENT_KIND_IMAGE,
                    url = a.url,
                    alt = a.alt,
                    title = null,
                    description = null,
                    imageUrl = null,
                    width = a.width,
                    height = a.height,
                    sortIndex = i,
                )

                is Attachment.Link -> q.insertAttachment(
                    cardId = card.id,
                    kind = ATTACHMENT_KIND_LINK,
                    url = a.url,
                    alt = null,
                    title = a.title,
                    description = a.description,
                    imageUrl = a.imageUrl,
                    width = null,
                    height = null,
                    sortIndex = i,
                )
            }
        }
    }

    private suspend fun writeLabels(card: Card) {
        q.deleteCardLabelsByCard(card.id)
        card.labels.forEach { q.insertCardLabel(card.id, it.id) }
    }

    private suspend fun boardIdOfCard(cardId: CardId): BoardId? = q.selectCard(cardId).awaitAsOneOrNull()?.boardId
}

private fun org.reduxkotlin.sample.taskflow.db.Card.toCard(
    attachments: PersistentList<Attachment>,
    labels: PersistentList<Label>,
): Card = Card(
    id = id,
    title = title,
    description = description,
    attachments = attachments,
    labels = labels,
    assigneeId = assigneeId,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun toAttachment(row: org.reduxkotlin.sample.taskflow.db.Attachment): Attachment = when (row.kind) {
    ATTACHMENT_KIND_IMAGE -> Attachment.Image(
        url = row.url,
        alt = row.alt.orEmpty(),
        width = row.width,
        height = row.height,
    )

    else -> Attachment.Link(
        url = row.url,
        title = row.title,
        description = row.description,
        imageUrl = row.imageUrl,
    )
}
