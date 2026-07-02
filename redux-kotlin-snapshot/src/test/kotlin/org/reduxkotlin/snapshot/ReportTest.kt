package org.reduxkotlin.snapshot

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ReportTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun schema_version_is_2() {
        val r = SnapshotReport(runId = "r", outDir = "o", totals = Totals(0, 0, 0, 0, 0), shots = emptyList())
        assertEquals(2, r.schemaVersion)
    }

    @Test fun new_shot_fields_default_to_null() {
        val s = ShotReport(id = "a", scene = "counter", input = "preset=n0", status = "ok")
        assertEquals(null, s.verifySemantics)
        assertEquals(null, s.semanticsSidecar)
        assertEquals(null, s.semanticsBytes)
    }

    @Test fun new_totals_fields_default_to_zero() {
        val t = Totals(0, 0, 0, 0, 0)
        assertEquals(0, t.semanticsMismatched)
        assertEquals(0, t.semanticsMatched)
        assertEquals(0L, t.renderMsTotal)
    }

    @Test fun v1_report_json_parses_under_v2_model() {
        // A v1 file lacks the new fields; defaults fill them in.
        val v1 = """{"schemaVersion":1,"runId":"r","outDir":"o",
            "totals":{"total":1,"ok":1,"failed":0,"mismatched":0,"missingGolden":0},
            "shots":[{"id":"a","scene":"counter","input":"preset=n0","status":"ok"}]}"""
        val r = json.decodeFromString(SnapshotReport.serializer(), v1)
        assertEquals(1, r.shots.size)
        assertEquals(0, r.totals.semanticsMismatched)
    }

    @Test fun semantics_verify_report_round_trips() {
        val sv = SemanticsVerifyReport(golden = "g.semantics.json", result = "mismatch", delta = listOf("- a", "+ b"))
        val encoded = Json.encodeToString(SemanticsVerifyReport.serializer(), sv)
        val decoded = json.decodeFromString(SemanticsVerifyReport.serializer(), encoded)
        assertEquals("mismatch", decoded.result)
        assertEquals(listOf("- a", "+ b"), decoded.delta)
    }
}
