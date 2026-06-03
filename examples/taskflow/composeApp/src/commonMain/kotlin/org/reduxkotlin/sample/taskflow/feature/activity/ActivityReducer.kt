package org.reduxkotlin.sample.taskflow.feature.activity

import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.core.Action

/** Maximum activity entries retained per account (oldest dropped). */
public const val ACTIVITY_CAP: Int = 50

/**
 * Pure per-account reducer for the [ActivityModel] slice (humanized activity feed, capped).
 *
 * [RecordActivity] appends the entry and trims to the most recent [ACTIVITY_CAP]. Not reset by
 * BoardClosed. Returns the same [model] instance unchanged for actions it does not handle.
 *
 * @param model the current activity slice.
 * @param action the dispatched action.
 * @return the next activity slice, or [model] unchanged when [action] is not handled.
 */
public fun activityReducer(model: ActivityModel, action: Action): ActivityModel = when (action) {
    is RecordActivity -> {
        val appended = model.entries.add(action.entry)
        val trimmed = if (appended.size > ACTIVITY_CAP) {
            appended.subList(appended.size - ACTIVITY_CAP, appended.size).toPersistentList()
        } else {
            appended
        }
        model.copy(entries = trimmed)
    }

    else -> model
}
