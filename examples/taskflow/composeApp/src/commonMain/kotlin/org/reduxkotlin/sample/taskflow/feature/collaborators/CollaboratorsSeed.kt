package org.reduxkotlin.sample.taskflow.feature.collaborators

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.core.AccountDetail
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.infra.SeedData

/**
 * Builds the initial [CollaboratorsModel] directory for [detail]: self + bot + every assignee
 * referenced on the account's seeded board.
 *
 * Drawing the assignees from [SeedData] (rather than only [AccountDetail.collaborators]) guarantees
 * the board screen can resolve every seeded card's avatar without reaching into the root store.
 *
 * @param detail the account whose collaborator directory is being seeded.
 * @return the self + bot + board-assignee summaries keyed by [AccountId].
 */
public fun seedCollaborators(detail: AccountDetail): PersistentMap<AccountId, AccountSummary> {
    val builder = persistentMapOf<AccountId, AccountSummary>().builder()
    builder[detail.self.id] = detail.self
    builder[SeedData.bot.id] = SeedData.bot
    detail.collaborators.forEach { (id, summary) -> builder[id] = summary }
    val seeded = SeedData.seededAccounts().firstOrNull { it.owner.id == detail.accountId }
    if (seeded != null) {
        // The board's collaborators (self + bot) cover every seeded card's assignee.
        seeded.collaborators.forEach { builder[it.id] = it }
    }
    return builder.build()
}
