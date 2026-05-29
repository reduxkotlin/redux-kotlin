package org.reduxkotlin.routing

/** A user model for tests. */
internal data class UserModel(val user: String? = null, val lastOrder: Int = -1)

/** A cart model for tests. */
internal data class CartModel(val items: List<String> = emptyList(), val id: Int = 0, val closed: Boolean = false)

/** Actions used across routing tests. */
internal data class LoggedIn(val user: String)
internal object LoggedOut
internal data class AddItem(val item: String)
internal object Checkout
internal object ResetAll
internal object NeverHandled
