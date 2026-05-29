package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the kctfork KSP2 harness runs the routing processor end to end. */
class SmokeTest {
    /** Empty source compiles cleanly through the no-op processor under KSP2. */
    @Test
    fun processor_runs_under_ksp2_and_compiles_empty() {
        val src = SourceFile.kotlin("Empty.kt", "package t\nfun noop() {}")
        val result = compileWithProcessor(moduleName = "T", src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
