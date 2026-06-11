package org.reduxkotlin.concurrent

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.atomicfu.update
import org.reduxkotlin.Dispatcher
import org.reduxkotlin.GetState
import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscriber
import org.reduxkotlin.StoreSubscription

/**
 * A [Store] whose `getState` and `subscribe` are lock-free (never block, even
 * during an in-flight dispatch) while writes are serialized through a reentrant
 * writer lock.
 *
 * Reads off the processing context return a value from an atomic state mirror;
 * reads on the processing context (from a listener/middleware on the dispatching
 * thread) return the inner store's in-progress state, matching core Redux.
 *
 * Per-dispatch ordering: the reducer commits → the mirror is published →
 * listeners are signaled through the [NotificationContext] → the writer lock
 * releases. A callback therefore always observes state at least as new as the
 * dispatch that triggered it — no lost wakeups for diff-based consumers.
 * Multiple dispatches may coalesce into what one callback observes, so
 * callbacks must pull current state via `getState`; a notification is a
 * signal, never a payload. There is no "listeners finished" barrier:
 * off-context readers may observe the new state while listeners are still
 * running. Reads are otherwise eventually consistent and strictly weaker than
 * a fully synchronized store (the intentional trade for non-blocking reads).
 *
 * Subscription contract: after `unsubscribe()` returns, no new callback
 * invocation begins; a callback already executing on another thread may run to
 * completion. With an inline context this means a peer unsubscribed by an
 * earlier listener in the same fan-out is skipped (a deliberate divergence
 * from core Redux's snapshot delivery).
 */
public interface ConcurrentStore<State> : Store<State>

/**
 * The v1 `CallerSerialized` strategy: the calling thread runs the full pipeline
 * under a reentrant lock; readers go lock-free through the mirror.
 *
 * Do not reassign [dispatch] after construction — doing so bypasses the writer
 * lock and the state mirror.
 *
 * @param State the application state type held by the store.
 * @param inner a freshly-created, non-thread-safe store (its INIT dispatch must
 *  have already run). Middleware must be installed on [inner] via the enhancer,
 *  not pre-applied to a foreign store.
 * @param notificationContext where listener callbacks (and [onError]) run.
 * @param onError isolates listener throwables so one failing subscriber never
 *  aborts delivery to the others nor desyncs the mirror.
 */
public class CallerSerializedStore<State>(
    private val inner: Store<State>,
    private val notificationContext: NotificationContext,
    private val onError: (Throwable) -> Unit,
) : ConcurrentStore<State> {

    private val lock = SynchronizedObject()
    private val context = DispatchContext()
    private val mirror = atomic(inner.getState())
    private val listeners = atomic<List<Registration>>(emptyList())

    /**
     * Per-subscription handle: the posted callback checks [active] at execution
     * time, so after `unsubscribe()` returns no new callback invocation begins
     * (an already-executing callback may run to completion).
     */
    private class Registration(val subscriber: StoreSubscriber) {
        val active = atomic(true)
    }

    init {
        // This fan-out hook must remain the FIRST listener registered on the
        // inner store (tests pin the ordering): inner listeners run only after
        // the reducer has committed, so the mirror published here is the
        // post-reducer state, and the atomic write happens-before every post()
        // below. A posted callback therefore never observes a mirror older than
        // the dispatch that triggered it — no lost wakeups for diff-based
        // consumers. sequenced()'s finally re-publish stays as an idempotent
        // backstop for reducer-throw and middleware-swallowed-action paths.
        inner.subscribe {
            mirror.value = inner.getState()
            val snapshot = listeners.value
            snapshot.forEach { registration ->
                notificationContext.post {
                    if (registration.active.value) {
                        notifyGuarded(registration.subscriber)
                    }
                }
            }
        }
        val pipeline = inner.dispatch
        inner.dispatch = { action -> sequenced(pipeline, action) }
    }

    private fun notifyGuarded(subscriber: StoreSubscriber) {
        try {
            subscriber()
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            try {
                onError(t)
            } catch (@Suppress("TooGenericExceptionCaught") suppressed: Throwable) {
                println(
                    "redux-kotlin-concurrent: onError handler threw and was suppressed: " +
                        "$suppressed (original listener error: $t)",
                )
            }
        }
    }

    private fun sequenced(pipeline: Dispatcher, action: Any): Any = synchronized(lock) {
        try {
            context.enter()
            pipeline(action)
        } finally {
            mirror.value = inner.getState()
            context.exit()
        }
    }

    override val store: Store<State> get() = inner

    override var dispatch: Dispatcher = { action -> inner.dispatch(action) }

    override val getState: GetState<State> = {
        if (context.isActive) inner.getState() else mirror.value
    }

    override val subscribe: (StoreSubscriber) -> StoreSubscription = { subscriber ->
        val registration = Registration(subscriber)
        listeners.update { it + registration }
        val unsub: StoreSubscription = {
            if (registration.active.compareAndSet(expect = true, update = false)) {
                listeners.update { it - registration }
            }
        }
        unsub
    }

    override val replaceReducer: (Reducer<State>) -> Unit = { nextReducer ->
        synchronized(lock) {
            try {
                context.enter()
                inner.replaceReducer(nextReducer)
            } finally {
                mirror.value = inner.getState()
                context.exit()
            }
        }
    }
}
