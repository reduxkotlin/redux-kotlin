package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import coil3.compose.setSingletonImageLoaderFactory
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import org.junit.Test
import org.reduxkotlin.Store
import org.reduxkotlin.bundle.createConcurrentModelStore
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.LoadBoardSucceeded
import org.reduxkotlin.sample.taskflow.action.SetFilterQuery
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.model.BoardModel
import org.reduxkotlin.sample.taskflow.model.FilterModel
import org.reduxkotlin.sample.taskflow.model.SyncModel
import org.reduxkotlin.sample.taskflow.model.columnById
import org.reduxkotlin.sample.taskflow.reducer.boardReducer
import org.reduxkotlin.sample.taskflow.reducer.filterReducer
import org.reduxkotlin.sample.taskflow.reducer.syncReducer
import org.reduxkotlin.sample.taskflow.ui.image.fakeNoNetworkImageLoader
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The render-isolation proof (design §10): the binding discipline that lets a single card move
 * recompose only the two affected columns while every other column stays frozen — the property
 * TaskFlow's whole `BoardScreen` is built to demonstrate (Rule C).
 *
 * Each column composable is wrapped in `key(colId)` and its innermost "card list" subscribes to
 * ONLY that column's stable `cardIds` slice
 * (`fieldStateOf(BoardModel::class){ it.board?.columnById(colId)?.cardIds ?: persistentListOf() }`),
 * never to the whole board / cards / columns. Because the `boardReducer` reuses unchanged `Column`
 * instances (structural sharing), a move A→B leaves column C's `cardIds` value-and-referentially
 * identical, so C's binding never fires and C does not recompose. A `LocalRecompositionCounter`
 * (a remembered `mutableStateMapOf` the test holds a reference to) records every (re)composition of
 * each card list. The counter is a PLAIN (non-snapshot) [MutableMap] held across recompositions by
 * `remember`: a snapshot-backed `mutableStateMapOf` written from a composable body would self-invalidate
 * the composition every frame (infinite recomposition → `waitForIdle()` never settles), so the tally
 * must live outside the snapshot system — mirroring [FieldStateTest]'s plain `var derivedReads`.
 *
 * If C ever recomposes on the move or on an unrelated dispatch, that is a genuine render-isolation
 * bug (a too-wide slice selection), not a test artifact — the assertions are not weakened to pass.
 */
@OptIn(ExperimentalTestApi::class)
class RenderIsolationTest {

    private val colA = ColumnId("col-A")
    private val colB = ColumnId("col-B")
    private val colC = ColumnId("col-C")

    private val owner = AccountId("ann")
    private val fixedInstant = Instant.fromEpochMilliseconds(1_716_000_000_000L)

    /** A 3-column board (A/B/C), each holding two cards, preloaded into the store via [LoadBoardSucceeded]. */
    private fun seedBoard(): Board {
        val ids = (1..6).map { CardId("card-$it") }
        val cards = ids.associateWith { id ->
            Card(
                id = id,
                title = "Card ${id.v}",
                description = "",
                createdBy = owner,
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            )
        }.toPersistentMap()
        val columns = persistentListOf(
            Column(colA, "A", persistentListOf(ids[0], ids[1])),
            Column(colB, "B", persistentListOf(ids[2], ids[3])),
            Column(colC, "C", persistentListOf(ids[4], ids[5])),
        )
        return Board(boardId = BoardId("test-board"), columns = columns, cards = cards)
    }

    /**
     * A minimal account-shaped store: the three board-side model slots wired to the REAL reducers
     * ([boardReducer] / [filterReducer] / [syncReducer]), with `NotificationContext.Inline` for a
     * deterministic, no-marshalling dispatch. Preloaded with [seedBoard].
     */
    private fun seededStore(): Store<ModelState> {
        val store = createConcurrentModelStore(notificationContext = NotificationContext.Inline) {
            model(BoardModel()) {
                on<LoadBoardSucceeded> { s, a -> boardReducer(s, a, owner) }
                on<CardMoveRequested> { s, a -> boardReducer(s, a, owner) }
            }
            model(FilterModel()) {
                on<SetFilterQuery> { s, a -> filterReducer(s, a) }
            }
            model(SyncModel()) {
                on<CardMoveRequested> { s, a -> syncReducer(s, a) }
            }
        }
        store.dispatch(LoadBoardSucceeded(seedBoard()))
        return store
    }

    @Test
    fun movingACardRecomposesOnlyTheTwoAffectedColumns() = runComposeUiTest {
        val store = seededStore()
        // Test-held reference to the per-column recomposition tally; each card list bumps its own count.
        lateinit var counts: MutableMap<ColumnId, Int>

        setContent {
            // No-network ImageLoader so any AsyncImage surfaces its fallback instead of hitting the net.
            setSingletonImageLoaderFactory { ctx -> fakeNoNetworkImageLoader(ctx) }
            // Plain (non-snapshot) map: writing it during composition must NOT invalidate the frame,
            // or waitForIdle() would loop forever. remember keeps the SAME instance across recompositions.
            val recompositions = remember {
                mutableMapOf<ColumnId, Int>().also { counts = it }
            }
            val s = rememberStableStore(store).value
            Row {
                listOf(colA, colB, colC).forEach { colId ->
                    key(colId) {
                        ColumnCardList(store = s, colId = colId, recompositions = recompositions)
                    }
                }
            }
        }
        waitForIdle()

        val baseline = counts.toMap()
        // Every column composed exactly once on the first frame.
        assertEquals(1, baseline[colA], "A composed once at baseline")
        assertEquals(1, baseline[colB], "B composed once at baseline")
        assertEquals(1, baseline[colC], "C composed once at baseline")

        // Move card-1 from A to B at index 0: only A and B's cardIds change identity.
        store.dispatch(CardMoveRequested(CardId("card-1"), colA, colB, 0, opId = OpId("op-1")))
        waitForIdle()

        assertTrue(counts[colA]!! > baseline[colA]!!, "A must recompose: its cardIds slice changed")
        assertTrue(counts[colB]!! > baseline[colB]!!, "B must recompose: its cardIds slice changed")
        assertEquals(
            baseline[colC]!!,
            counts[colC]!!,
            "C must NOT recompose: its cardIds slice is unchanged (render isolation)",
        )

        // CONTROL: an unrelated dispatch (a filter query that matches nothing in C) leaves C's
        // cardIds slice untouched, so the per-column card list stays flat.
        val mid = counts.toMap()
        store.dispatch(SetFilterQuery("zzz-no-match"))
        waitForIdle()
        assertEquals(
            mid[colC]!!,
            counts[colC]!!,
            "C must stay flat on an unrelated dispatch: SetFilterQuery does not touch the cardIds slice",
        )
    }
}

/**
 * The innermost per-column "card list" composable under test. It subscribes to ONLY this column's
 * stable [PersistentList] of [CardId] (Rule C) and increments [recompositions] on every (re)composition,
 * so the test can assert which columns recomposed. Wrapped in `key(colId)` by the caller.
 *
 * @param store the stable account store holding the board models.
 * @param colId the column whose card-id slice this list binds (the only slice it reads).
 * @param recompositions the shared per-column composition tally the test inspects.
 */
@Composable
private fun ColumnCardList(store: Store<ModelState>, colId: ColumnId, recompositions: MutableMap<ColumnId, Int>) {
    val cardIds: PersistentList<CardId> by store.fieldStateOf(BoardModel::class) {
        it.board?.columnById(colId)?.cardIds ?: persistentListOf()
    }
    // Side effect of (re)composition: bump this column's tally.
    recompositions[colId] = (recompositions[colId] ?: 0) + 1
    Text(text = "${colId.v}:${cardIds.joinToString(",") { it.v }}")
}
