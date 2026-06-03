package org.reduxkotlin.sample.taskflow.app.nav

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.board.CancelCreateCard
import org.reduxkotlin.sample.taskflow.feature.board.StartCreateCard

/**
 * Pure per-account reducer for the [NavModel] slice (the navigation stack).
 *
 * Stack rules:
 *  - [Navigate] to a [Route.TopLevel] **resets** the stack to that destination's canonical base
 *    (`BoardList → [BoardList]`, `Board(id) → [BoardList, Board(id)]`,
 *    `Profile / Settings → [<self>]`). This is what gives the nav-rail its "tab" feel and what
 *    makes back from a board return to the board list.
 *  - [Navigate] to a modal route ([Route.CardDetail], [Route.ComposeCard]) **pushes** it on top
 *    of the current stack. Pushing a `CardDetail` with the same `cardId` as the top is idempotent
 *    (avoids a duplicate frame); a `Mode` carried by the action is ignored on push — entry mode
 *    is always [Route.CardDetail.Mode.View], and [EnterEditMode] flips it later.
 *  - [EnterEditMode] flips the top `CardDetail` to [Route.CardDetail.Mode.Edit] in place
 *    (no push). [Back] flips it back to `View` (no pop). That mode flip is the user's "edit"
 *    stack frame in the spec — they observe back as "return to view of the same card."
 *  - [Back] pops one frame if `stack.size > 1`. At the root it's a no-op so the host's system
 *    back exits/backgrounds the app. The Edit→View flip above takes precedence over the pop.
 *  - Domain aliases [OpenCard] / [CloseCard] / [StartCreateCard] / [CancelCreateCard] forward
 *    to [Navigate] / [Back] with their respective routes — kept so screens can dispatch domain
 *    actions instead of constructing routes by hand, and so existing tests keep their shape.
 *
 * Returns the same [model] instance unchanged when [action] is not handled (or when the
 * resulting stack is identical, e.g. an idempotent `Navigate`).
 *
 * @param model the current nav slice.
 * @param action the dispatched action.
 * @return the next nav slice, or [model] unchanged when [action] is not handled.
 */
public fun navReducer(model: NavModel, action: Action): NavModel = when (action) {
    is Navigate -> model.applyStack(stackForNavigate(model, action.route))
    is EnterEditMode -> model.flipCardDetailMode(Route.CardDetail.Mode.Edit, requireOther = Route.CardDetail.Mode.Edit)
    is Back -> backReducer(model)
    is OpenCard -> navReducer(model, Navigate(Route.CardDetail(action.cardId)))
    is CloseCard -> if (model.current is Route.CardDetail) navReducer(model, Back) else model
    is StartCreateCard -> navReducer(model, Navigate(Route.ComposeCard(action.columnId)))
    is CancelCreateCard -> if (model.current is Route.ComposeCard) navReducer(model, Back) else model
    else -> model
}

/** Computes the next stack for a [Navigate] action — see [navReducer]'s KDoc for the rules. */
private fun stackForNavigate(model: NavModel, target: Route): PersistentList<Route> = when (target) {
    is Route.BoardList -> persistentListOf(Route.BoardList)

    is Route.Board -> persistentListOf(Route.BoardList, target)

    is Route.Profile -> persistentListOf(Route.Profile)

    is Route.Settings -> persistentListOf(Route.Settings)

    is Route.CardDetail -> {
        val top = model.current
        if (top is Route.CardDetail && top.cardId == target.cardId) {
            model.stack
        } else {
            model.stack.add(Route.CardDetail(target.cardId))
        }
    }

    is Route.ComposeCard ->
        if (model.current == target) model.stack else model.stack.add(target)
}

/**
 * Edit→View special-case + standard pop. Edit-on-top transitions to view in place; otherwise pop one
 * frame when not at the root; otherwise no-op.
 */
private fun backReducer(model: NavModel): NavModel {
    val top = model.current
    return when {
        top is Route.CardDetail && top.mode == Route.CardDetail.Mode.Edit ->
            model.copy(stack = model.stack.set(model.stack.lastIndex, top.copy(mode = Route.CardDetail.Mode.View)))

        model.stack.size > 1 -> model.copy(stack = model.stack.removeAt(model.stack.lastIndex))

        else -> model
    }
}

/** Returns `this` unchanged if [next] equals the current stack; otherwise copies with the new stack. */
private fun NavModel.applyStack(next: PersistentList<Route>): NavModel = if (next == stack) this else copy(stack = next)

/**
 * Flips the top [Route.CardDetail] to [to] mode. No-op when the top isn't a CardDetail or is already
 * in mode [requireOther].
 */
private fun NavModel.flipCardDetailMode(to: Route.CardDetail.Mode, requireOther: Route.CardDetail.Mode): NavModel {
    val top = current
    return if (top is Route.CardDetail && top.mode != requireOther) {
        copy(stack = stack.set(stack.lastIndex, top.copy(mode = to)))
    } else {
        this
    }
}
