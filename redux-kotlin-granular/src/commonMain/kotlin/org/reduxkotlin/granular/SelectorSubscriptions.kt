package org.reduxkotlin.granular

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscription

/**
 * A dynamic group of granular subscriptions backed by one store subscriber.
 *
 * Add bindings with [subscribeTo] and call [close] when the owning screen or
 * controller is disposed. Removing the final binding also removes the shared
 * store subscriber, so an idle scope does not keep receiving notifications.
 */
public interface SelectorSubscriptions<State> {

    /**
     * Adds a granular subscription to this shared group. The listener follows
     * the same trigger and equality semantics as [Store.subscribeTo].
     */
    public fun <F> subscribeTo(
        selector: (State) -> F,
        triggerOnSubscribe: Boolean = true,
        listener: (oldValue: F, newValue: F) -> Unit,
    ): StoreSubscription

    /**
     * Removes every binding and the underlying store subscription. A closed
     * scope cannot accept new bindings.
     */
    public fun close(): Unit
}

/**
 * Creates a dynamic shared subscription group for this store. The scope
 * subscribes to this final [Store] instance, so it composes correctly with
 * concurrent, routing, and bundle store factories without enhancer ordering.
 *
 * [onSelectorError] has the same defence-in-depth role as
 * [Store.subscribeFields]: a failed selector or listener does not prevent
 * other entries from receiving later updates.
 */
public fun <State> Store<State>.selectorSubscriptions(
    onSelectorError: ((cause: Throwable) -> Unit)? = null,
): SelectorSubscriptions<State> = SelectorSubscriptionsRegistry(this, onSelectorError)

private class SelectorSubscriptionsRegistry<State>(
    private val store: Store<State>,
    private val onSelectorError: ((Throwable) -> Unit)?,
) : SelectorSubscriptions<State> {

    private class Entry<State, F>(val selector: (State) -> F, var last: F, val listener: (F, F) -> Unit) {
        var active: Boolean = true
    }

    private class Notification<F>(val listener: (F, F) -> Unit, val old: F, val new: F)

    private val lock = SynchronizedObject()
    private val entries = ArrayList<Entry<State, *>>()
    private var storeSubscription: StoreSubscription? = null
    private var closed: Boolean = false

    override fun <F> subscribeTo(
        selector: (State) -> F,
        triggerOnSubscribe: Boolean,
        listener: (oldValue: F, newValue: F) -> Unit,
    ): StoreSubscription {
        synchronized(lock) { check(!closed) { "SelectorSubscriptions is closed." } }
        val initial = try {
            selector(store.state)
        } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
            onSelectorError?.invoke(cause)
            return {}
        }
        val entry = Entry(selector, initial, listener)
        synchronized(lock) {
            check(!closed) { "SelectorSubscriptions is closed." }
            entries += entry
            if (storeSubscription == null) {
                storeSubscription = store.subscribe(::onStoreChanged)
            }
        }
        // The subscription is installed before this re-sample, closing the
        // registration window without invoking user code under the registry lock.
        reDiff(entry, triggerOnSubscribe)
        return { remove(entry) }
    }

    override fun close() {
        val subscription = synchronized(lock) {
            if (closed) return
            closed = true
            entries.forEach { it.active = false }
            entries.clear()
            storeSubscription.also { storeSubscription = null }
        }
        subscription?.invoke()
    }

    private fun remove(entry: Entry<State, *>) {
        val subscription = synchronized(lock) {
            if (!entry.active) return
            entry.active = false
            entries.remove(entry)
            if (entries.isEmpty()) storeSubscription.also { storeSubscription = null } else null
        }
        subscription?.invoke()
    }

    private fun onStoreChanged() {
        val snapshot = synchronized(lock) {
            if (closed) emptyList() else entries.toList()
        }
        val state = store.state
        snapshot.forEach { rawEntry ->
            @Suppress("UNCHECKED_CAST")
            deliver(rawEntry as Entry<State, Any?>, state, triggerOnUnchanged = false)
        }
    }

    private fun <F> reDiff(entry: Entry<State, F>, triggerOnUnchanged: Boolean) {
        deliver(entry, store.state, triggerOnUnchanged)
    }

    private fun <F> deliver(entry: Entry<State, F>, state: State, triggerOnUnchanged: Boolean) {
        val next = try {
            entry.selector(state)
        } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
            onSelectorError?.invoke(cause)
            return
        }
        val notification = synchronized(lock) {
            if (!entry.active) {
                null
            } else {
                val previous = entry.last
                when {
                    !valuesMatch(next, previous) -> {
                        entry.last = next
                        Notification(entry.listener, previous, next)
                    }

                    triggerOnUnchanged -> Notification(entry.listener, previous, previous)

                    else -> null
                }
            }
        }
        notification?.let { guardedNotify(it) }
    }

    private fun <F> guardedNotify(notification: Notification<F>) {
        try {
            notification.listener(notification.old, notification.new)
        } catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
            onSelectorError?.invoke(cause)
        }
    }
}
