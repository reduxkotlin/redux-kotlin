package org.reduxkotlin.sample.taskflow.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.Instant

data class FilterModel(
    val query: String = "",
    val assignee: AccountId? = null,
    val labelIds: PersistentSet<LabelId> = persistentSetOf(),
)

data class UndoModel(
    val past: PersistentList<Board> = persistentListOf(),
    val future: PersistentList<Board> = persistentListOf(),
    val cap: Int = 15,
)

data class SyncModel(
    // cards with an in-flight op (drives per-card optimistic alpha)
    val inFlight: PersistentSet<CardId> = persistentSetOf(),
    val pendingCount: Int = 0, // queued outbound ops not yet synced
    val online: Boolean = true, // one-way projection of AppSettings.fakeService.online (via SyncStatusChanged only)
    val lastSyncedAt: Instant? = null,
    val lastError: String? = null,
)

data class ActivityModel(val entries: PersistentList<ActivityEntry> = persistentListOf())

data class ActivityEntry(val id: String, val actorId: AccountId, val summary: String, val timestamp: Instant)
