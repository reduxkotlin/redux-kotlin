package org.reduxkotlin.sample.taskflow.core

import kotlin.jvm.JvmInline

/** Identifies an account (user). */
@JvmInline
public value class AccountId(public val v: String)

/** Identifies a board. */
@JvmInline
public value class BoardId(public val v: String)

/** Identifies a column within a board. */
@JvmInline
public value class ColumnId(public val v: String)

/** Identifies a card within a board. */
@JvmInline
public value class CardId(public val v: String)

/** Identifies a label. */
@JvmInline
public value class LabelId(public val v: String)

/** Unique per async op (minted at dispatch). */
@JvmInline
public value class OpId(public val v: String)
