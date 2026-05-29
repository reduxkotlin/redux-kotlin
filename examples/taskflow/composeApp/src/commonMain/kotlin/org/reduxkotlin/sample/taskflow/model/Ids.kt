package org.reduxkotlin.sample.taskflow.model

import kotlin.jvm.JvmInline

@JvmInline
value class AccountId(val v: String)

@JvmInline
value class BoardId(val v: String)

@JvmInline
value class ColumnId(val v: String)

@JvmInline
value class CardId(val v: String)

@JvmInline
value class LabelId(val v: String)

// unique per async op (minted at dispatch)
@JvmInline
value class OpId(val v: String)
