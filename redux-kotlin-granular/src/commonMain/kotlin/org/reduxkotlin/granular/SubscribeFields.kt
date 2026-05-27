package org.reduxkotlin.granular

import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscription
import kotlin.concurrent.Volatile

/**
 * Subscribes to a single derived value of the store's state. The
 * [listener] fires only when `selector(newState)` differs from the
 * previously observed value (referential `===` first, then structural).
 *
 * If [triggerOnSubscribe] is `true` (the default), the listener fires
 * once immediately after subscription with both `oldValue` and
 * `newValue` set to the current selector result. This matches the
 * convention of `StateFlow`, `LiveData`, and RxJava's `BehaviorSubject`,
 * and removes the typical "render once + subscribe to changes" two-step
 * in UI binding code.
 *
 * Returns a [StoreSubscription] (`() -> Unit`) that tears down the
 * subscription when invoked.
 */
public fun <State, F> Store<State>.subscribeTo(
    selector: (State) -> F,
    triggerOnSubscribe: Boolean = true,
    listener: (oldValue: F, newValue: F) -> Unit,
): StoreSubscription = subscribeFields { on(selector, triggerOnSubscribe, listener) }

/**
 * Registers multiple granular subscriptions backed by a *single*
 * underlying `store.subscribe` listener. Best for components that watch
 * several fields.
 *
 * Returns one combined [StoreSubscription]; invoking it tears down every
 * inner subscription and the single underlying listener.
 *
 * The optional `onSelectorError` parameter is a handler invoked when a
 * selector evaluation throws inside the dispatch hot path (or at
 * registration time). The default is `null` — exceptions are silently
 * swallowed and the offending entry is skipped, so an uncaught
 * exception from one selector never breaks other subscribers. Pass a
 * custom handler to capture errors to a crash reporter or fail-fast in
 * tests.
 */
public fun <State> Store<State>.subscribeFields(
    onSelectorError: ((cause: Throwable) -> Unit)? = null,
    block: (FieldSubscriptionScope<State>) -> Unit,
): StoreSubscription {
    val registry = FieldSubscriptionRegistry(this, onSelectorError)
    block(registry)
    return registry.activate()
}

/**
 * Kotlin-only overload accepting a lambda-with-receiver block. Compiles
 * away to the lambda-form overload above; not `@JsExport`ed because
 * Kotlin/JS lambda-with-receiver export semantics are awkward in raw JS.
 *
 * Non-Kotlin consumers (Swift, JS, TS) use the [block] / [onSelectorError]
 * pair above directly.
 */
public inline fun <State> Store<State>.subscribeFields(
    crossinline block: FieldSubscriptionScope<State>.() -> Unit,
): StoreSubscription = subscribeFields(onSelectorError = null) { scope -> scope.block() }

internal class FieldSubscriptionRegistry<State>(
    private val store: Store<State>,
    private val onSelectorError: ((Throwable) -> Unit)?,
) : FieldSubscriptionScope<State> {

    internal class Entry<State, F>(
        val selector: (State) -> F,
        @Volatile var last: F,
        val triggerOnSubscribe: Boolean,
        val listener: (F, F) -> Unit,
    )

    private val entries = ArrayList<Entry<State, *>>(INITIAL_CAPACITY)

    @Volatile
    private var sealed: Boolean = false

    override fun <F> on(selector: (State) -> F, triggerOnSubscribe: Boolean, listener: (F, F) -> Unit) {
        check(!sealed) {
            "FieldSubscriptionScope.on() called after subscribeFields block completed. " +
                "Register all subscriptions inside the block."
        }
        // If the selector throws during initial evaluation (before activate),
        // forward the error and skip this entry rather than aborting the
        // whole block — same defence-in-depth promise as the dispatch loop.
        val initial: F = try {
            selector(store.state)
        } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
            onSelectorError?.invoke(cause)
            return
        }
        entries += Entry(selector, initial, triggerOnSubscribe, listener)
    }

    override fun <F> on(
        property: kotlin.reflect.KProperty1<State, F>,
        triggerOnSubscribe: Boolean,
        listener: (F, F) -> Unit,
    ) {
        on(selector = property::get, triggerOnSubscribe = triggerOnSubscribe, listener = listener)
    }

    fun activate(): StoreSubscription {
        sealed = true

        val storeSub = store.subscribe {
            val state = store.state
            // `entries` is sealed (no further mutation). The only mutable
            // field per entry is `entry.last`, which is @Volatile and
            // written serially because the store contract guarantees
            // subscribers are invoked serially within a dispatch.
            for (i in entries.indices) {
                @Suppress("UNCHECKED_CAST")
                val entry = entries[i] as Entry<State, Any?>
                val next: Any?
                try {
                    next = entry.selector(state)
                } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
                    onSelectorError?.invoke(cause)
                    continue
                }
                val prev = entry.last
                if (next !== prev && next != prev) {
                    entry.last = next
                    try {
                        entry.listener(prev, next)
                    } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
                        onSelectorError?.invoke(cause)
                    }
                }
            }
        }

        // Fire triggerOnSubscribe=true entries AFTER the underlying
        // subscriber is installed, so a racing dispatch can't be silently
        // dropped (the worst case is the listener fires twice — once from
        // the dispatch with a real diff, once from the trigger with
        // (current, current) — which is harmless).
        for (i in entries.indices) {
            @Suppress("UNCHECKED_CAST")
            val entry = entries[i] as Entry<State, Any?>
            if (entry.triggerOnSubscribe) {
                val current = entry.last
                try {
                    entry.listener(current, current)
                } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
                    onSelectorError?.invoke(cause)
                }
            }
        }

        return { storeSub() }
    }

    private companion object {
        private const val INITIAL_CAPACITY = 4
    }
}
