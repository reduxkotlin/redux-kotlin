package org.reduxkotlin.sample.taskflow.feature.collaborators

import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.feature.account.EditProfile

/**
 * Pure per-account reducer for the [CollaboratorsModel] slice (account directory including self).
 *
 * [EditProfile] carries no account id, so [selfId] (captured by the per-account store closure)
 * identifies the self collaborator to update or insert. Returns the same [model] instance unchanged
 * for actions it does not handle.
 *
 * @param model the current collaborators slice.
 * @param action the dispatched action.
 * @param selfId the id of the self account this per-account store belongs to.
 * @return the next collaborators slice, or [model] unchanged when [action] is not handled.
 */
public fun collaboratorsReducer(model: CollaboratorsModel, action: Action, selfId: AccountId): CollaboratorsModel =
    when (action) {
        is EditProfile -> model.copy(
            byId = model.byId.put(
                selfId,
                model.byId[selfId]?.copy(
                    displayName = action.displayName,
                    email = action.email,
                    avatarUrl = action.avatarUrl,
                ) ?: AccountSummary(selfId, action.displayName, action.email, action.avatarUrl),
            ),
        )

        else -> model
    }
