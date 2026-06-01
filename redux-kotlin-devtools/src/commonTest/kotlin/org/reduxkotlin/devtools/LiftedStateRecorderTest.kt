package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiftedStateRecorderTest {
    private fun recorder(maxAge: Int = 50) =
        LiftedStateRecorder(maxAge = maxAge, clock = { 1000L })

    private fun stagedIds(r: LiftedStateRecorder): List<Int> =
        r.liftedState()["stagedActionIds"]!!.jsonArray.map { it.jsonPrimitive.int }

    @Test
    fun initSeedsActionZeroAndComputedState() {
        val r = recorder()
        r.init(JsonPrimitive(0))
        val lifted = r.liftedState()
        assertEquals(1, lifted["nextActionId"]!!.jsonPrimitive.int)
        assertEquals(listOf(0), stagedIds(r))
        assertEquals(JsonPrimitive(0), lifted["computedStates"]!!.jsonArray[0].jsonObject["state"])
    }

    @Test
    fun recordAssignsIncrementingIdsAndReturnsEntry() {
        val r = recorder()
        r.init(JsonPrimitive(0))
        val first = r.record(action = JsonPrimitive("INC"), state = JsonPrimitive(1))
        assertEquals(1, first.actionId)
        assertFalse(first.isExcess)
        val second = r.record(action = JsonPrimitive("INC"), state = JsonPrimitive(2))
        assertEquals(2, second.actionId)
    }

    @Test
    fun isExcessFlipsOnceStagedExceedsMaxAge() {
        val r = recorder(maxAge = 3)
        r.init(JsonPrimitive(0)) // staged = [0]
        val e1 = r.record(JsonPrimitive("a"), JsonPrimitive(1)) // staged [0,1]
        val e2 = r.record(JsonPrimitive("b"), JsonPrimitive(2)) // staged [0,1,2] size == maxAge
        val e3 = r.record(JsonPrimitive("c"), JsonPrimitive(3)) // size 4 > maxAge → excess
        assertFalse(e1.isExcess)
        assertFalse(e2.isExcess)
        assertTrue(e3.isExcess)
    }

    @Test
    fun ringBufferDropsOldestActionsBeyondMaxAge() {
        val r = recorder(maxAge = 2)
        r.init(JsonPrimitive(0))
        r.record(JsonPrimitive("a"), JsonPrimitive(1))
        r.record(JsonPrimitive("b"), JsonPrimitive(2))
        r.record(JsonPrimitive("c"), JsonPrimitive(3))
        assertEquals(2, stagedIds(r).size)
    }
}
