package org.reduxkotlin.granular

import kotlin.reflect.KProperty1

/**
 * DSL receiver for [subscribeFields]. Each [on] registration adds one
 * entry to a single underlying `store.subscribe` listener, so a batch of
 * N fields incurs the cost of one underlying subscriber instead of N.
 *
 * The scope is sealed at the end of the [subscribeFields] block — there is
 * no public way to add or remove entries after activation. This is by
 * design: it lets us iterate the entry list without copying on the hot
 * dispatch path, and it bounds the surface of the API in v1. Callers
 * that need to swap inner subscriptions mid-flight should use separate
 * [subscribeFields] blocks or independent [subscribeTo] calls.
 */
public interface FieldSubscriptionScope<State> {

    /**
     * Registers a granular subscription against a derived value. The
     * listener fires when `selector(newState)` differs from the previously
     * observed value (referential `===` first, then structural `==`).
     *
     * If [triggerOnSubscribe] is `true` (the default), the listener fires
     * once immediately after the [subscribeFields] block completes, with
     * both `oldValue` and `newValue` set to the current selector result.
     */
    public fun <F> on(
        selector: (State) -> F,
        triggerOnSubscribe: Boolean = true,
        listener: (oldValue: F, newValue: F) -> Unit,
    )

    /**
     * Property-reference convenience overload. Kotlin call sites can write
     * `on(MyState::myField) { o, n -> ... }`; equivalent to passing
     * `MyState::myField::get` to the selector overload.
     *
     * Hidden from Swift via [@HiddenFromObjC] on the call site and not
     * `@JsExport`ed, because [KProperty1] is Kotlin-only — non-Kotlin
     * consumers should use the lambda selector overload above.
     */
    public fun <F> on(
        property: KProperty1<State, F>,
        triggerOnSubscribe: Boolean = true,
        listener: (oldValue: F, newValue: F) -> Unit,
    )
}
