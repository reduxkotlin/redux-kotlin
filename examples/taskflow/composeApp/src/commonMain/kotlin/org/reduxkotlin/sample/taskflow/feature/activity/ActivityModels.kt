package org.reduxkotlin.sample.taskflow.feature.activity

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.reduxkotlin.sample.taskflow.core.ActivityEntry

/** Per-account model for the humanized activity feed. */
public data class ActivityModel(val entries: PersistentList<ActivityEntry> = persistentListOf())
