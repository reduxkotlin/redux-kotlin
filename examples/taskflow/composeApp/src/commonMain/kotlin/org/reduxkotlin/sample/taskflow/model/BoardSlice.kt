package org.reduxkotlin.sample.taskflow.model

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.LabelId
import kotlin.time.Instant

data class FilterModel(
    val query: String = "",
    val assignee: AccountId? = null,
    val labelIds: PersistentSet<LabelId> = persistentSetOf(),
)

// UndoModel moved to …feature.undo.UndoModel

data class SyncModel(
    // cards with an in-flight op (drives per-card optimistic alpha)
    val inFlight: PersistentSet<CardId> = persistentSetOf(),
    val pendingCount: Int = 0, // queued outbound ops not yet synced
    val online: Boolean = true, // one-way projection of AppSettings.fakeService.online (via SyncStatusChanged only)
    val lastSyncedAt: Instant? = null,
    val lastError: String? = null,
)
