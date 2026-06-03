package org.reduxkotlin.sample.taskflow.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary

// --- Per-account models (ALL declared up front; board slices start empty/sentinel) ---
// Identity (name/email/avatar) is NOT duplicated here — it lives once in CollaboratorsModel
// (which includes self). SessionModel moved to …feature.account.AccountModels.
// CollaboratorsModel moved to …feature.collaborators.CollaboratorsModels.

/** Holds the cached board tiles and their display order for one account. */
public data class BoardListModel(
    val boards: PersistentMap<BoardId, BoardSummary> = persistentMapOf(),
    val order: PersistentList<BoardId> = persistentListOf(),
)
