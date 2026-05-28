package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DevChecksTest {

    @Test
    fun devChecks_throws_on_structurally_equal_new_instance() {
        val s = createModelStore(devChecks = true) {
            model(UserModel()) {
                // Returns a brand-new instance that is structurally equal: a wasteful no-op write.
                on<LoggedIn> { u, _ -> u.copy() }
            }
        }
        assertFailsWith<IllegalStateException> {
            s.dispatch(LoggedIn("ann"))
        }
    }

    @Test
    fun devChecks_allows_real_change() {
        val s = createModelStore(devChecks = true) {
            model(UserModel()) {
                on<LoggedIn> { u, a -> u.copy(user = a.user) }
            }
        }
        s.dispatch(LoggedIn("ann")) // does not throw
    }

    @Test
    fun without_devChecks_no_op_copy_is_silently_committed() {
        val s = createModelStore(devChecks = false) {
            model(UserModel()) {
                on<LoggedIn> { u, _ -> u.copy() }
            }
        }
        s.dispatch(LoggedIn("ann")) // no throw; just a wasteful write
    }
}
