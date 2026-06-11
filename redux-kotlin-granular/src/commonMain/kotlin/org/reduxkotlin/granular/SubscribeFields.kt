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
 * Registration is race-safe: after the underlying `store.subscribe` is
 * installed, every selector is re-evaluated and a change that landed
 * during registration fires the real `(old, new)` diff at that point
 * (subsuming the `triggerOnSubscribe` callback — no double-fire). On a
 * store that posts notifications, a callback for the same change may
 * already be queued; the worst case is one redundant same-value
 * callback.
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
            // written serially because notification delivery is required to
            // be serial: plain stores notify inline under dispatch, and the
            // concurrent store's NotificationContext contract requires
            // one-at-a-time, post-ordered delivery (a multi-threaded
            // executor context is unsupported and would race this field).
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

        // Post-install re-diff, for EVERY entry: a change landing during
        // registration — after on() sampled `entry.last`, before the
        // store.subscribe above was installed — has no notification of its
        // own, so re-evaluate each selector now and fire the real diff if the
        // value moved. This also subsumes the triggerOnSubscribe firing: a
        // moved value fires (prev, next) once (no double-fire); an unchanged
        // value fires the documented (current, current) trigger. On a posting
        // context a callback for the same change may already be queued — the
        // worst case is one redundant same-value callback, which is harmless.
        reDiffAfterInstall()

        return { storeSub() }
    }

    private fun reDiffAfterInstall() {
        val state = store.state
        for (i in entries.indices) {
            @Suppress("UNCHECKED_CAST")
            val entry = entries[i] as Entry<State, Any?>
            val next: Any? = try {
                entry.selector(state)
            } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
                onSelectorError?.invoke(cause)
                continue
            }
            val prev = entry.last
            if (next !== prev && next != prev) {
                entry.last = next
                notifyGuarded(entry, prev, next)
            } else if (entry.triggerOnSubscribe) {
                notifyGuarded(entry, prev, prev)
            }
        }
    }

    private fun notifyGuarded(entry: Entry<State, Any?>, prev: Any?, next: Any?) {
        try {
            entry.listener(prev, next)
        } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
            onSelectorError?.invoke(cause)
        }
    }

    private companion object {
        private const val INITIAL_CAPACITY = 4
    }
}
