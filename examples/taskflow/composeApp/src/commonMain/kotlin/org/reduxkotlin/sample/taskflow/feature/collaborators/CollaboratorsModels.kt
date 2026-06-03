package org.reduxkotlin.sample.taskflow.feature.collaborators

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary

// Resolves assignee/creator/bot avatars within the account store (no root reach-in).
/** Holds the collaborator directory for one account, keyed by [AccountId]. Includes self + bot. */
public data class CollaboratorsModel(val byId: PersistentMap<AccountId, AccountSummary> = persistentMapOf())
