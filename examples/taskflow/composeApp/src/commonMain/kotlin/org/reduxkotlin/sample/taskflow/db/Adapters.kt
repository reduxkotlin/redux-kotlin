package org.reduxkotlin.sample.taskflow.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.LabelId
import org.reduxkotlin.sample.taskflow.model.OpId
import kotlin.time.Instant

/** Maps an [AccountId] value-class to/from its `TEXT` storage form. */
public val accountIdAdapter: ColumnAdapter<AccountId, String> = object : ColumnAdapter<AccountId, String> {
    override fun decode(databaseValue: String): AccountId = AccountId(databaseValue)
    override fun encode(value: AccountId): String = value.v
}

/** Maps a [BoardId] value-class to/from its `TEXT` storage form. */
public val boardIdAdapter: ColumnAdapter<BoardId, String> = object : ColumnAdapter<BoardId, String> {
    override fun decode(databaseValue: String): BoardId = BoardId(databaseValue)
    override fun encode(value: BoardId): String = value.v
}

/** Maps a [ColumnId] value-class to/from its `TEXT` storage form. */
public val columnIdAdapter: ColumnAdapter<ColumnId, String> = object : ColumnAdapter<ColumnId, String> {
    override fun decode(databaseValue: String): ColumnId = ColumnId(databaseValue)
    override fun encode(value: ColumnId): String = value.v
}

/** Maps a [CardId] value-class to/from its `TEXT` storage form. */
public val cardIdAdapter: ColumnAdapter<CardId, String> = object : ColumnAdapter<CardId, String> {
    override fun decode(databaseValue: String): CardId = CardId(databaseValue)
    override fun encode(value: CardId): String = value.v
}

/** Maps a [LabelId] value-class to/from its `TEXT` storage form. */
public val labelIdAdapter: ColumnAdapter<LabelId, String> = object : ColumnAdapter<LabelId, String> {
    override fun decode(databaseValue: String): LabelId = LabelId(databaseValue)
    override fun encode(value: LabelId): String = value.v
}

/** Maps an [OpId] value-class to/from its `TEXT` storage form. */
public val opIdAdapter: ColumnAdapter<OpId, String> = object : ColumnAdapter<OpId, String> {
    override fun decode(databaseValue: String): OpId = OpId(databaseValue)
    override fun encode(value: OpId): String = value.v
}

/** Maps an [Instant] to/from its `INTEGER` storage form (epoch milliseconds). */
public val instantAdapter: ColumnAdapter<Instant, Long> = object : ColumnAdapter<Instant, Long> {
    override fun decode(databaseValue: Long): Instant = Instant.fromEpochMilliseconds(databaseValue)
    override fun encode(value: Instant): Long = value.toEpochMilliseconds()
}

/** Maps an [Int] to/from its `INTEGER` (`Long`) storage form. */
public val intAdapter: ColumnAdapter<Int, Long> = object : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int = databaseValue.toInt()
    override fun encode(value: Int): Long = value.toLong()
}

private fun accountNavAdapter() = Account_nav.Adapter(
    accountIdAdapter = accountIdAdapter,
    boardIdAdapter = boardIdAdapter,
    openCardIdAdapter = cardIdAdapter,
)

private fun activityTableAdapter() = Activity.Adapter(
    accountIdAdapter = accountIdAdapter,
    actorIdAdapter = accountIdAdapter,
    timestampAdapter = instantAdapter,
)

private fun appSettingsAdapter() = App_settings.Adapter(
    latencyMinMsAdapter = intAdapter,
    latencyMaxMsAdapter = intAdapter,
    botIntervalMsAdapter = intAdapter,
    syncIntervalMsAdapter = intAdapter,
    activeAccountIdAdapter = accountIdAdapter,
)

private fun attachmentTableAdapter() = Attachment.Adapter(
    cardIdAdapter = cardIdAdapter,
    widthAdapter = intAdapter,
    heightAdapter = intAdapter,
    sortIndexAdapter = intAdapter,
)

private fun boardTableAdapter() = Board.Adapter(
    idAdapter = boardIdAdapter,
    accountIdAdapter = accountIdAdapter,
    colorAdapter = intAdapter,
    updatedAtAdapter = instantAdapter,
)

private fun boardColumnAdapter() = Board_column.Adapter(
    idAdapter = columnIdAdapter,
    boardIdAdapter = boardIdAdapter,
    wipLimitAdapter = intAdapter,
    sortIndexAdapter = intAdapter,
)

private fun cardTableAdapter() = Card.Adapter(
    idAdapter = cardIdAdapter,
    boardIdAdapter = boardIdAdapter,
    columnIdAdapter = columnIdAdapter,
    sortIndexAdapter = intAdapter,
    assigneeIdAdapter = accountIdAdapter,
    createdByAdapter = accountIdAdapter,
    createdAtAdapter = instantAdapter,
    updatedAtAdapter = instantAdapter,
)

private fun labelTableAdapter() = Label.Adapter(
    idAdapter = labelIdAdapter,
    boardIdAdapter = boardIdAdapter,
    colorAdapter = intAdapter,
)

private fun pendingOpAdapter() = Pending_op.Adapter(
    opIdAdapter = opIdAdapter,
    accountIdAdapter = accountIdAdapter,
    createdAtAdapter = instantAdapter,
    attemptsAdapter = intAdapter,
)

/**
 * Builds a [TaskFlowDb] over [driver] with every column adapter wired in. Task 12's
 * `SqlDelightLocalStore` calls this from each platform's `DriverFactory`; this module only assembles
 * adapters and never opens a real driver itself.
 */
public fun taskFlowDb(driver: SqlDriver): TaskFlowDb = TaskFlowDb(
    driver = driver,
    accountAdapter = Account.Adapter(idAdapter = accountIdAdapter),
    account_navAdapter = accountNavAdapter(),
    activityAdapter = activityTableAdapter(),
    app_settingsAdapter = appSettingsAdapter(),
    attachmentAdapter = attachmentTableAdapter(),
    boardAdapter = boardTableAdapter(),
    board_columnAdapter = boardColumnAdapter(),
    cardAdapter = cardTableAdapter(),
    card_labelAdapter = Card_label.Adapter(cardIdAdapter = cardIdAdapter, labelIdAdapter = labelIdAdapter),
    collaboratorAdapter = Collaborator.Adapter(accountIdAdapter = accountIdAdapter, idAdapter = accountIdAdapter),
    labelAdapter = labelTableAdapter(),
    pending_opAdapter = pendingOpAdapter(),
    sync_metaAdapter = Sync_meta.Adapter(accountIdAdapter = accountIdAdapter, lastSyncedAtAdapter = instantAdapter),
)
