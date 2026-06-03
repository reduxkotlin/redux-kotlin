package org.reduxkotlin.sample.taskflow.infra.util

import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.infra.platform.newUuid

/**
 * Source of fresh, unique ids minted at dispatch sites (Rule G — never in a reducer).
 *
 * Screens read this via `LocalIdGenerator.current` and pass new ids straight into actions, so id
 * creation stays a side effect at the call site. Tests swap in [FakeIdGenerator] for determinism.
 */
interface IdGenerator {
    /** A unique [OpId] tagging one optimistic async op (so its ack/rollback can be correlated). */
    fun newOpId(): OpId

    /** A unique [CardId] for a newly created card. */
    fun newCardId(): CardId

    /** A unique [BoardId] for a newly created board. */
    fun newBoardId(): BoardId

    /** A unique [ColumnId] for a newly created column. */
    fun newColumnId(): ColumnId

    /** A unique activity-entry id (a plain `String`, matching `ActivityEntry.id`). */
    fun newActivityId(): String
}

/**
 * Production [IdGenerator] backing every id with a fresh platform UUID.
 *
 * @property uuid the UUID source; defaults to the platform [newUuid] and is overridable for testing.
 */
class DefaultIdGenerator(private val uuid: () -> String = ::newUuid) : IdGenerator {
    override fun newOpId(): OpId = OpId(uuid())

    override fun newCardId(): CardId = CardId(uuid())

    override fun newBoardId(): BoardId = BoardId(uuid())

    override fun newColumnId(): ColumnId = ColumnId(uuid())

    override fun newActivityId(): String = uuid()
}

/**
 * Deterministic [IdGenerator] for tests: each kind emits a sequential, prefixed id
 * (`op-1`, `card-1`, `board-1`, `column-1`, `activity-1`, ...) so assertions stay stable.
 *
 * @param start the first sequence number (defaults to `0`; the first emitted id is `start + 1`).
 */
class FakeIdGenerator(start: Int = 0) : IdGenerator {
    private var counter = start

    private fun next(prefix: String): String = "$prefix-${++counter}"

    override fun newOpId(): OpId = OpId(next("op"))

    override fun newCardId(): CardId = CardId(next("card"))

    override fun newBoardId(): BoardId = BoardId(next("board"))

    override fun newColumnId(): ColumnId = ColumnId(next("column"))

    override fun newActivityId(): String = next("activity")
}
