package org.reduxkotlin.sample.taskflow.feature.activity

import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.ActivityEntry

// --- profile / activity (per-account) ---

/** Records a single [ActivityEntry] into the per-account activity feed. */
public data class RecordActivity(val entry: ActivityEntry) : Action
