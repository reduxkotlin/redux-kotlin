package org.reduxkotlin.sample.taskflow.core

import kotlin.time.Instant

/** A single entry in the activity log for a board. */
public data class ActivityEntry(val id: String, val actorId: AccountId, val summary: String, val timestamp: Instant)
