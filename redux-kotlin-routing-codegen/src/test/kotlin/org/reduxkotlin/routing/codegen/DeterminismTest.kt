package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The generated registrar text must be byte-identical across runs (load-bearing for dispatch order + stable ABI). */
class DeterminismTest {
    private fun src() = SourceFile.kotlin(
        "D.kt",
        """
        package d
        import org.reduxkotlin.routing.*
        data class M1(val n: Int = 0)
        data class M2(val n: Int = 0)
        data class AX(val x: Int); data class AY(val x: Int)
        @ReduxInitial fun m1i(): M1 = M1()
        @ReduxInitial fun m2i(): M2 = M2()
        @Reduce fun onY(s: M2, a: AY): M2 = s
        @Reduce fun onX(s: M1, a: AX): M1 = s
        """.trimIndent(),
    )

    /** Same input → identical generated output. */
    @Test
    fun generated_output_is_byte_identical_across_runs() {
        val first = compileWithProcessor("Det", src()).generatedRegistrar("Det")
        val second = compileWithProcessor("Det", src()).generatedRegistrar("Det")
        assertNotNull(first)
        assertEquals(first, second)
    }
}
