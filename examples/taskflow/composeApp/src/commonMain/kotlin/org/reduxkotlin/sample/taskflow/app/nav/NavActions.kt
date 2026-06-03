package org.reduxkotlin.sample.taskflow.app.nav

import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.Route

// --- nav ---

/**
 * Push or reset the navigation stack to land on [route].
 *
 * Pushed onto the current top for non-top-level routes ([Route.CardDetail],
 * [Route.ComposeCard]); a [Route.TopLevel] **resets** the stack to that destination's
 * canonical base (BoardList → `[BoardList]`, Board(id) → `[BoardList, Board(id)]`,
 * Profile/Settings → `[<self>]`).
 */
data class Navigate(val route: Route) : Action

/**
 * Pop the top frame of the navigation stack — the system back button, in action form.
 *
 * Special-case for [Route.CardDetail.Mode]: if the top is a card-detail in [Mode.Edit]
 * the reducer transitions to [Mode.View] (a mode flip on the same frame, not a pop).
 * No-op if the stack is at its root (the host's system-back exits/backgrounds the app).
 */
data object Back : Action

/** Flip the current [Route.CardDetail] to [Route.CardDetail.Mode.Edit]; no-op otherwise. */
data object EnterEditMode : Action

/** Convenience for `Navigate(Route.CardDetail(cardId))` — preserved for selector clarity. */
data class OpenCard(val cardId: CardId) : Action

/** Convenience for [Back] when the top of the stack is a [Route.CardDetail]. */
data object CloseCard : Action
