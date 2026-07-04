package org.reduxkotlin.snapshot

import kotlinx.serialization.Serializable

/** The canonical batch report (also the contract the future HTML dashboard renders over). */
@Serializable
internal data class SnapshotReport(
    val schemaVersion: Int = 2,
    val runId: String,
    val outDir: String,
    val totals: Totals,
    val shots: List<ShotReport>,
)

/** Run roll-up counts. */
@Serializable
internal data class Totals(
    val total: Int,
    val ok: Int,
    val failed: Int,
    val mismatched: Int,
    val missingGolden: Int,
    val semanticsMismatched: Int = 0,
    val semanticsMissingGolden: Int = 0,
    val semanticsMatched: Int = 0,
    val renderMsTotal: Long = 0,
)

/** One shot's result. [status] is `ok` or `error`; [verify]/[verifySemantics] present only when verifying. */
@Serializable
internal data class ShotReport(
    val id: String,
    val scene: String,
    val input: String,
    val theme: String? = null,
    val sizePx: List<Int> = emptyList(),
    val out: String? = null,
    val bytes: Int? = null,
    val renderMs: Long? = null,
    val status: String,
    val error: String? = null,
    val verify: VerifyReport? = null,
    val verifySemantics: SemanticsVerifyReport? = null,
    val semanticsSidecar: String? = null,
    val semanticsBytes: Int? = null,
)

/** Golden comparison for one shot. [result] is `match` | `mismatch` | `missing-golden`. */
@Serializable
internal data class VerifyReport(
    val golden: String,
    val result: String,
    val diffPercent: Double,
    val diffImage: String? = null,
)

/** Semantics golden comparison for one shot. [result] is `match` | `mismatch` | `missing-golden`. */
@Serializable
internal data class SemanticsVerifyReport(val golden: String, val result: String, val delta: List<String>? = null)
