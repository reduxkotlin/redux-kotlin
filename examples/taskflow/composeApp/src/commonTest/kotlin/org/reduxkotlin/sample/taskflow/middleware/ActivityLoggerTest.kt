package org.reduxkotlin.sample.taskflow.middleware

import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.BotAddedCard
import org.reduxkotlin.sample.taskflow.action.BotMovedCard
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.RecordActivity
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AddCard
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.DeleteCard
import org.reduxkotlin.sample.taskflow.core.EditCard
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.model.SessionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ActivityLoggerTest {
    private val ann = AccountId("ann")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val todo = ColumnId("todo")
    private val doing = ColumnId("doing")

    /** Records dispatches and forwarded actions; state carries the self session id. */
    private class RecordingStore(initial: ModelState) : Store<ModelState> {
        val dispatched: MutableList<Any> = mutableListOf()
        private val current: ModelState = initial
        override var dispatch: (Any) -> Any = { action ->
            dispatched += action
            action
        }
        override val subscribe: (subscriber: () -> Unit) -> () -> Unit = { { } }
        override var replaceReducer: ((ModelState, Any) -> ModelState) -> Unit = { }
        override val getState: () -> ModelState = { current }
        override val store: Store<ModelState> get() = this
    }

    private fun store(): RecordingStore = RecordingStore(ModelState.of(SessionModel(ann)))

    private fun chainFor(store: RecordingStore, forwarded: MutableList<Any>): (Any) -> Any {
        val mw = activityLoggerMiddleware()
        return mw(store)({ action ->
            forwarded += action
            action
        })
    }

    private fun recorded(store: RecordingStore): RecordActivity =
        assertIs<RecordActivity>(store.dispatched.single { it is RecordActivity })

    @Test
    fun logsMoveWithSelfActorAndSummary() {
        val store = store()
        val forwarded = mutableListOf<Any>()
        chainFor(store, forwarded)(CardMoveRequested(CardId("c1"), todo, doing, 0, OpId("op1")))

        val entry = recorded(store).entry
        assertEquals(ann, entry.actorId)
        assertTrue(entry.summary.contains("moved", ignoreCase = true), "summary: ${entry.summary}")
        assertTrue(entry.id.isNotBlank(), "minted id")
        // The action is forwarded to the rest of the chain before logging.
        assertTrue(forwarded.single() is CardMoveRequested)
    }

    @Test
    fun logsAddWithSummary() {
        val store = store()
        chainFor(store, mutableListOf())(AddCard(todo, CardId("c2"), "T", "D", OpId("op2"), now))
        val entry = recorded(store).entry
        assertEquals(ann, entry.actorId)
        assertTrue(entry.summary.contains("added", ignoreCase = true), "summary: ${entry.summary}")
    }

    @Test
    fun logsEditWithSummary() {
        val store = store()
        chainFor(store, mutableListOf())(EditCard(CardId("c1"), "T2", "D2", OpId("op3"), now))
        val entry = recorded(store).entry
        assertEquals(ann, entry.actorId)
        assertTrue(entry.summary.contains("edited", ignoreCase = true), "summary: ${entry.summary}")
    }

    @Test
    fun logsDeleteWithSummary() {
        val store = store()
        chainFor(store, mutableListOf())(DeleteCard(CardId("c1"), OpId("op4")))
        val entry = recorded(store).entry
        assertEquals(ann, entry.actorId)
        assertTrue(entry.summary.contains("deleted", ignoreCase = true), "summary: ${entry.summary}")
    }

    @Test
    fun logsBotMoveWithBotActor() {
        val store = store()
        chainFor(store, mutableListOf())(BotMovedCard(CardId("c1"), doing, 0))
        val entry = recorded(store).entry
        assertEquals(BOT_ACCOUNT_ID, entry.actorId, "bot move attributed to the bot, not self")
        assertTrue(entry.summary.contains("moved", ignoreCase = true), "summary: ${entry.summary}")
    }

    @Test
    fun logsBotAddWithBotActor() {
        val store = store()
        val card = Card(
            id = CardId("bot-1"),
            title = "bot card",
            description = "",
            createdBy = BOT_ACCOUNT_ID,
            createdAt = now,
            updatedAt = now,
        )
        chainFor(store, mutableListOf())(BotAddedCard(todo, card))
        val entry = recorded(store).entry
        assertEquals(BOT_ACCOUNT_ID, entry.actorId)
        assertTrue(entry.summary.contains("added", ignoreCase = true), "summary: ${entry.summary}")
    }

    @Test
    fun doesNotLogUnrelatedAction() {
        val store = store()
        val forwarded = mutableListOf<Any>()
        chainFor(store, forwarded)(Navigate(Route.Profile))
        assertTrue(store.dispatched.none { it is RecordActivity }, "nav is not activity: ${store.dispatched}")
        assertTrue(forwarded.single() is Navigate)
    }
}
