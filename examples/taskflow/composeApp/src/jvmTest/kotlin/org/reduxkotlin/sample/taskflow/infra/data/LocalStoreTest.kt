package org.reduxkotlin.sample.taskflow.infra.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Attachment
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.feature.board.newBoardColumns
import org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.infra.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.infra.data.remote.InverseOpDto
import org.reduxkotlin.sample.taskflow.infra.data.remote.RemoteChange
import org.reduxkotlin.sample.taskflow.infra.data.remote.SyncOp
import org.reduxkotlin.sample.taskflow.infra.db.taskFlowDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LocalStoreTest {

    private fun newStore(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return SqlDelightLocalStore(taskFlowDb(driver))
    }

    private val annId = AccountId("ann")
    private val annBoardId = BoardId("ann-board")
    private val todo = ColumnId("ann-todo")
    private val doing = ColumnId("ann-doing")
    private val done = ColumnId("ann-done")

    @Test
    fun ensureSeededRoundTrips() = runTest {
        val store = newStore()
        store.ensureSeeded()

        val accounts = store.loadAccounts()
        assertEquals(3, accounts.size)
        assertTrue(accounts.any { it.id == annId })

        val boards = store.loadBoardList(annId)
        assertEquals(1, boards.size)
        val summary = boards.single()
        assertEquals(annBoardId, summary.id)
        assertEquals(6, summary.cardCount)
        assertEquals(2, summary.doneCount)
    }

    @Test
    fun ensureSeededIsIdempotent() = runTest {
        val store = newStore()
        store.ensureSeeded()
        store.ensureSeeded()
        assertEquals(3, store.loadAccounts().size)
    }

    @Test
    fun loadBoardReassemblesNormalizedGraph() = runTest {
        val store = newStore()
        store.ensureSeeded()

        val board = store.loadBoard(annBoardId)
        assertNotNull(board)
        // Columns ordered by sortIndex.
        assertEquals(listOf("To Do", "Doing", "Done"), board.columns.map { it.title })
        // cards map == union of column cardIds, no dupes.
        val union = board.columns.flatMap { it.cardIds }
        assertEquals(union.toSet(), board.cards.keys)
        assertEquals(union.size, union.toSet().size)
        // Doing column at-limit (wipLimit == count).
        val doingCol = board.columns.single { it.id == doing }
        assertEquals(doingCol.cardIds.size, doingCol.wipLimit)
        // Attachments + labels attached.
        assertTrue(board.cards.getValue(CardId("ann-1")).labels.isNotEmpty())
        val withImage = board.cards.values.first { c -> c.attachments.any { it is Attachment.Image } }
        assertTrue(withImage.attachments.any { it is Attachment.Image })
        val withLink = board.cards.values.first { c -> c.attachments.any { it is Attachment.Link } }
        assertTrue(withLink.attachments.any { it is Attachment.Link })
    }

    @Test
    fun loadCollaboratorsIncludesOwnerAndBot() = runTest {
        val store = newStore()
        store.ensureSeeded()
        val collabs = store.loadCollaborators(annId)
        assertTrue(collabs.containsKey(annId))
        assertTrue(collabs.containsKey(AccountId("bot")))
    }

    @Test
    fun moveCardPersists() = runTest {
        val store = newStore()
        store.ensureSeeded()
        store.moveCard(annBoardId, CardId("ann-1"), done, 0)

        val board = store.loadBoard(annBoardId)!!
        assertTrue(board.columns.single { it.id == done }.cardIds.contains(CardId("ann-1")))
        assertTrue(!board.columns.single { it.id == todo }.cardIds.contains(CardId("ann-1")))
        // No card appears in more than one column.
        val all = board.columns.flatMap { it.cardIds }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun addCardPersists() = runTest {
        val store = newStore()
        store.ensureSeeded()
        val now = Instant.fromEpochMilliseconds(1_716_500_000_000L)
        val card = Card(
            id = CardId("ann-new"),
            title = "Fresh card",
            description = "body",
            attachments = persistentListOf(Attachment.Image("https://img/x.png", "alt", 600, 400)),
            labels = persistentListOf(),
            assigneeId = annId,
            createdBy = annId,
            createdAt = now,
            updatedAt = now,
        )
        store.addCard(annBoardId, card, todo, 0)

        val board = store.loadBoard(annBoardId)!!
        assertEquals(CardId("ann-new"), board.columns.single { it.id == todo }.cardIds.first())
        assertEquals("Fresh card", board.cards.getValue(CardId("ann-new")).title)
        assertTrue(board.cards.getValue(CardId("ann-new")).attachments.any { it is Attachment.Image })
    }

    @Test
    fun createBoardPersistsBoardWithColumns() = runTest {
        val store = newStore()
        store.ensureSeeded()
        val newId = BoardId("ann-release")
        val now = Instant.fromEpochMilliseconds(1_716_600_000_000L)
        store.createBoard(
            accountId = annId,
            boardId = newId,
            name = "Release Plan",
            color = 0xFF4A3FB8L,
            updatedAt = now,
            columns = newBoardColumns(newId),
        )

        val board = store.loadBoard(newId)
        assertNotNull(board)
        assertEquals(listOf("To Do", "Doing", "Done"), board.columns.map { it.title })
        assertTrue(board.cards.isEmpty())
        // Surfaces in the owner's board list alongside the seeded board.
        val list = store.loadBoardList(annId)
        assertEquals(2, list.size)
        assertTrue(list.any { it.id == newId && it.name == "Release Plan" })
    }

    @Test
    fun createBoardThenAddCardLoadsCardInColumn() = runTest {
        val store = newStore()
        store.ensureSeeded()
        val newId = BoardId("ann-release")
        val now = Instant.fromEpochMilliseconds(1_716_600_000_000L)
        store.createBoard(annId, newId, "Release Plan", 0xFF4A3FB8L, now, newBoardColumns(newId))
        val todoCol = newBoardColumns(newId).first { it.title == "To Do" }
        val card = Card(
            id = CardId("rel-1"),
            title = "Cut the release",
            description = "",
            assigneeId = null,
            createdBy = annId,
            createdAt = now,
            updatedAt = now,
        )
        store.addCard(newId, card, todoCol.id, 0)

        val board = store.loadBoard(newId)!!
        val todo = board.columns.first { it.title == "To Do" }
        assertTrue(todo.cardIds.contains(CardId("rel-1")), "To Do column should contain the added card")
        assertEquals("Cut the release", board.cards.getValue(CardId("rel-1")).title)
    }

    @Test
    fun addColumnAppendsColumn() = runTest {
        val store = newStore()
        store.ensureSeeded()
        store.addColumn(annBoardId, Column(ColumnId("ann-backlog"), "Backlog", persistentListOf()), sortIndex = 3)

        val board = store.loadBoard(annBoardId)!!
        // Columns are ordered by sortIndex; the appended one lands last.
        assertEquals(ColumnId("ann-backlog"), board.columns.last().id)
        assertEquals("Backlog", board.columns.last().title)
    }

    @Test
    fun editCardPersists() = runTest {
        val store = newStore()
        store.ensureSeeded()
        val now = Instant.fromEpochMilliseconds(1_716_500_000_000L)
        store.editCard(CardId("ann-2"), "Renamed", "New body", now)

        val card = store.loadBoard(annBoardId)!!.cards.getValue(CardId("ann-2"))
        assertEquals("Renamed", card.title)
        assertEquals("New body", card.description)
        assertEquals(now, card.updatedAt)
    }

    @Test
    fun deleteCardPersists() = runTest {
        val store = newStore()
        store.ensureSeeded()
        store.deleteCard(CardId("ann-2"))

        val board = store.loadBoard(annBoardId)!!
        assertTrue(!board.cards.containsKey(CardId("ann-2")))
        assertTrue(board.columns.none { it.cardIds.contains(CardId("ann-2")) })
    }

    @Test
    fun pendingOpQueueLifecycle() = runTest {
        val store = newStore()
        store.ensureSeeded()

        val op1 = SyncOp.Move(
            opId = "op-1",
            cardId = "ann-1",
            from = "ann-todo",
            to = "ann-doing",
            toIndex = 0,
            inverse = InverseOpDto.MoveBack("ann-1", "ann-todo", 0),
        )
        val op2 = SyncOp.Delete(
            opId = "op-2",
            cardId = "ann-2",
            inverse = InverseOpDto.DeleteAdded("ann-2"),
        )
        store.enqueue(annId, op1)
        store.enqueue(annId, op2)

        // SyncOp survives a JSON round-trip through the DB; oldest first.
        val pending = store.pendingOps(annId)
        assertEquals(2, pending.size)
        assertEquals(op1, pending[0])
        assertEquals(op2, pending[1])

        store.incrementAttempts(OpId("op-1"))
        store.markSynced(OpId("op-1"))
        val after = store.pendingOps(annId)
        assertEquals(1, after.size)
        assertEquals(op2, after.single())
    }

    @Test
    fun applyRemoteMergesLastWriteWins() = runTest {
        val store = newStore()
        store.ensureSeeded()
        val remoteUpdate = store.loadBoard(annBoardId)!!.cards.getValue(CardId("ann-1"))
            .copy(title = "Remote wins")
        store.applyRemote(
            listOf(
                RemoteChange.CardUpserted(remoteUpdate, todo, 0),
                RemoteChange.CardDeleted(CardId("ann-2")),
            ),
        )

        val board = store.loadBoard(annBoardId)!!
        assertEquals("Remote wins", board.cards.getValue(CardId("ann-1")).title)
        assertTrue(!board.cards.containsKey(CardId("ann-2")))
    }

    @Test
    fun settingsActiveAccountAndSyncMetaRoundTrip() = runTest {
        val store = newStore()
        store.ensureSeeded()

        store.saveActiveAccountId(annId)
        assertEquals(annId, store.loadActiveAccountId())
        store.saveActiveAccountId(null)
        assertNull(store.loadActiveAccountId())

        val at = Instant.fromEpochMilliseconds(1_716_700_000_000L)
        store.setLastSyncedAt(annId, at)
        assertEquals(at, store.lastSyncedAt(annId))

        val settings = store.loadSettings().copy(language = "fr")
        store.saveSettings(settings)
        assertEquals("fr", store.loadSettings().language)
    }
}
