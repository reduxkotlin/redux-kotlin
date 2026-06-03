package org.reduxkotlin.sample.taskflow.middleware

import org.reduxkotlin.Middleware
import org.reduxkotlin.Store
import org.reduxkotlin.middleware
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.BotAddedCard
import org.reduxkotlin.sample.taskflow.action.BotMovedCard
import org.reduxkotlin.sample.taskflow.action.RecordActivity
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.ActivityEntry
import org.reduxkotlin.sample.taskflow.core.AddCard
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.DeleteCard
import org.reduxkotlin.sample.taskflow.core.EditCard
import org.reduxkotlin.sample.taskflow.model.SessionModel
import org.reduxkotlin.sample.taskflow.platform.newUuid
import kotlin.time.Clock

/** The non-login bot collaborator's id; mirrors `SeedData.bot.id` so bot activity is attributed to it. */
public val BOT_ACCOUNT_ID: AccountId = AccountId("bot")

/**
 * The activity-logger middleware: humanizes card mutations into the per-account [ActivityModel] feed.
 *
 * It is a pass-through observer — it forwards the action first (so the feed reflects work that actually
 * happened), then, for the card mutations it recognizes, dispatches a [RecordActivity] carrying a freshly
 * minted [ActivityEntry]. The entry's `actorId` is the self account ([SessionModel.accountId]) for user
 * mutations and [BOT_ACCOUNT_ID] for the server-truth bot mutations. The entry's `id` and `timestamp` are
 * minted here (middleware may use the platform clock + [newUuid]; a reducer never may).
 *
 * @return the assembled [Middleware] over [ModelState].
 */
public fun activityLoggerMiddleware(): Middleware<ModelState> = middleware { store, next, action ->
    val result = next(action)
    val described = describe(action)
    if (described != null) {
        store.dispatch(
            RecordActivity(
                ActivityEntry(
                    id = newUuid(),
                    actorId = actorOf(store, action),
                    summary = described,
                    timestamp = Clock.System.now(),
                ),
            ),
        )
    }
    result
}

/** Bot mutations are attributed to the bot; everything else to the self session account. */
private fun actorOf(store: Store<ModelState>, action: Any): AccountId = when (action) {
    is BotMovedCard, is BotAddedCard -> BOT_ACCOUNT_ID
    else -> store.state.get<SessionModel>().accountId
}

/** Human summary for the actions that belong in the feed, or `null` to skip logging. */
private fun describe(action: Any): String? = when (action) {
    is CardMoveRequested -> "moved a card"
    is AddCard -> "added a card"
    is EditCard -> "edited a card"
    is DeleteCard -> "deleted a card"
    is BotMovedCard -> "moved a card"
    is BotAddedCard -> "added a card"
    else -> null
}
