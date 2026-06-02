package org.reduxkotlin.devtools

/**
 * Returns `true` if [action] should be recorded given the [denylist]/[allowlist] regexes. An action
 * is identified by its class `simpleName` (falling back to `toString()`); denied matches win, and a
 * non-empty allowlist must match.
 */
internal fun shouldSend(action: Any, denylist: List<Regex>, allowlist: List<Regex>): Boolean {
    val key = action::class.simpleName ?: action.toString()
    val denied = denylist.any { it.containsMatchIn(key) }
    val allowed = allowlist.isEmpty() || allowlist.any { it.containsMatchIn(key) }
    return !denied && allowed
}
