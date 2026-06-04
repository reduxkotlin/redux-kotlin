package org.reduxkotlin.devtools.cli.capture

import kotlin.test.Test
import kotlin.test.assertEquals

internal class CaptureStoreTest {
    @Test
    fun safeKey_sanitizes_separator_and_unsafe_chars() {
        assertEquals("taskflow__TaskFlow-root", safeKey("taskflow::TaskFlow-root"))
        assertEquals("a_b__c_d", safeKey("a/b::c d"))
    }
}
