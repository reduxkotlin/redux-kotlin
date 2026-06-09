package org.reduxkotlin.compose.saveable

import kotlin.test.Test
import kotlin.test.assertEquals

class StateSaverTest {

    @Test
    fun encode_then_decode_roundtrips_snapshot() {
        val snapshot = testSaver.save(TestState(tab = 4, query = "hi"))
        val encoded = testSaver.json.encodeToString(testSaver.serializer, snapshot)
        val decoded = testSaver.json.decodeFromString(testSaver.serializer, encoded)
        assertEquals(snapshot, decoded)
    }

    @Test
    fun restore_builds_expected_action() {
        val action = testSaver.restore(UiSnapshot(tab = 4, query = "hi"))
        assertEquals(RehydrateUi(4, "hi"), action)
    }
}
