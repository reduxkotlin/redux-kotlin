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
}
