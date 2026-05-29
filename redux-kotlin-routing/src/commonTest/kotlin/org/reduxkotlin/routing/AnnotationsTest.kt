package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertTrue

class AnnotationsTest {
    @Reduce
    private fun sampleReduce(s: UserModel, a: LoggedIn): UserModel = s.copy(user = a.user)

    @ReduxInitial
    private fun sampleInitial(): UserModel = UserModel()

    @Test
    fun annotations_are_applicable_to_functions() {
        assertTrue(sampleInitial().user == null)
        assertTrue(sampleReduce(UserModel(), LoggedIn("x")).user == "x")
    }
}
