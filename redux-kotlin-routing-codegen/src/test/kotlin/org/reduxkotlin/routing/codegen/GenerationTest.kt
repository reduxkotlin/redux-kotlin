package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** End-to-end generation tests: the generated registrar must COMPILE against the routing runtime. */
class GenerationTest {
    private val feature = SourceFile.kotlin(
        "Feature.kt",
        """
        package feat
        import org.reduxkotlin.routing.Reduce
        import org.reduxkotlin.routing.ReduxInitial
        data class UserModel(val user: String? = null)
        data class LoggedIn(val user: String)
        object LoggedOut
        @ReduxInitial fun userInitial(): UserModel = UserModel()
        @Reduce fun onLoggedIn(s: UserModel, a: LoggedIn): UserModel = s.copy(user = a.user)
        @Reduce fun onLoggedOut(s: UserModel, a: LoggedOut): UserModel = s.copy(user = null)
        """.trimIndent(),
    )

    /** A valid feature generates a registrar that compiles. */
    @Test
    fun generates_a_compiling_redux_module() {
        val result = compileWithProcessor(moduleName = "UserFeature", feature)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val text = result.generatedRegistrar("UserFeature")
        assertTrue(text != null, "registrar not generated")
        assertTrue(text!!.contains("object UserFeature"))
        assertTrue(text.contains("ReduxModule"))
    }

    /** Two handlers with the same simple name in different packages must still compile (aliased imports). */
    @Test
    fun two_handlers_same_simple_name_different_packages_compile() {
        val a = SourceFile.kotlin(
            "A.kt",
            """
            package a
            import org.reduxkotlin.routing.*
            data class MA(val n: Int = 0)
            data class Act(val x: Int)
            @ReduxInitial fun mi(): MA = MA()
            @Reduce fun onAct(s: MA, a: Act): MA = s.copy(n = a.x)
            """.trimIndent(),
        )
        val b = SourceFile.kotlin(
            "B.kt",
            """
            package b
            import org.reduxkotlin.routing.*
            data class MB(val n: Int = 0)
            data class Act(val x: Int)
            @ReduxInitial fun mi(): MB = MB()
            @Reduce fun onAct(s: MB, a: Act): MB = s.copy(n = a.x)
            """.trimIndent(),
        )
        val result = compileWithProcessor(moduleName = "Multi", a, b)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    /** Two @Reduce for the same (model, action) both render as on<A> (DSL last-write-wins applies). */
    @Test
    fun duplicate_handlers_for_same_action_both_render() {
        val src = SourceFile.kotlin(
            "Dup.kt",
            """
            package dup
            import org.reduxkotlin.routing.*
            data class M(val n: Int = 0)
            data class A(val x: Int)
            @ReduxInitial fun mi(): M = M()
            @Reduce fun first(s: M, a: A): M = s.copy(n = a.x)
            @Reduce fun second(s: M, a: A): M = s.copy(n = a.x + 1)
            """.trimIndent(),
        )
        val result = compileWithProcessor(moduleName = "Dup", src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val text = result.generatedRegistrar("Dup")!!
        // both handler references present (aliased or not)
        assertTrue(text.contains("first") && text.contains("second"), text)
    }
}
