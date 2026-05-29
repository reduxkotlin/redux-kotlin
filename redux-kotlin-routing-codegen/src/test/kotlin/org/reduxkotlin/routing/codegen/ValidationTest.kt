package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun errorResult(body: String, moduleName: String? = "T") =
    compileWithProcessor(moduleName, SourceFile.kotlin("Src.kt", "package t\nimport org.reduxkotlin.routing.*\n$body"))

/** Validation diagnostics for @Reduce/@ReduxInitial. */
class ValidationTest {
    /** Missing routing.moduleName arg is a hard error. */
    @Test fun missing_module_name_errors() {
        val r = errorResult(
            "data class M(val n: Int=0)\ndata class A(val x: Int)\n@ReduxInitial fun mi():M=M()\n@Reduce fun h(s:M,a:A):M=s",
            moduleName = null,
        )
        assertTrue(r.messages.contains("routing.moduleName"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }

    /** A @Reduce with wrong arity is rejected. */
    @Test fun wrong_arity_errors() {
        val r = errorResult("data class M(val n:Int=0)\n@ReduxInitial fun mi():M=M()\n@Reduce fun h(s:M):M=s")
        assertTrue(r.messages.contains("exactly"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }

    /** A @Reduce whose return type is not the model is rejected. */
    @Test fun return_not_model_errors() {
        val r = errorResult(
            "data class M(val n:Int=0)\ndata class A(val x:Int)\n@ReduxInitial fun mi():M=M()\n@Reduce fun h(s:M,a:A):A=a",
        )
        assertTrue(r.messages.contains("return"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }

    /** A generic action type is rejected. */
    @Test fun generic_action_errors() {
        val r = errorResult(
            "data class M(val n:Int=0)\nclass A<T>\n@ReduxInitial fun mi():M=M()\n@Reduce fun h(s:M,a:A<String>):M=s",
        )
        assertTrue(r.messages.contains("generic"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }

    /** A model with handlers but no @ReduxInitial is rejected. */
    @Test fun missing_initial_errors() {
        val r = errorResult("data class M(val n:Int=0)\ndata class A(val x:Int)\n@Reduce fun h(s:M,a:A):M=s")
        assertTrue(r.messages.contains("ReduxInitial"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }

    /** A non-top-level @Reduce is rejected. */
    @Test fun non_top_level_reduce_errors() {
        val r = errorResult(
            "data class M(val n:Int=0)\ndata class A(val x:Int)\n@ReduxInitial fun mi():M=M()\nobject O { @Reduce fun h(s:M,a:A):M=s }",
        )
        assertTrue(r.messages.contains("top-level"), r.messages)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
    }

    /** If ONE model lacks its @ReduxInitial, the whole module is fail-closed: nothing is generated. */
    @Test fun one_missing_initial_suppresses_generation_for_all_models() {
        val r = compileWithProcessor(
            "Multi",
            SourceFile.kotlin(
                "Src.kt",
                """
                package t
                import org.reduxkotlin.routing.*
                data class M1(val n: Int = 0)
                data class M2(val n: Int = 0)
                data class A1(val x: Int)
                data class A2(val x: Int)
                @ReduxInitial fun m1i(): M1 = M1()
                @Reduce fun h1(s: M1, a: A1): M1 = s
                @Reduce fun h2(s: M2, a: A2): M2 = s
                """.trimIndent(),
            ),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assertTrue(r.generatedRegistrar("Multi") == null, "no registrar should be generated when any model is invalid")
    }
}
