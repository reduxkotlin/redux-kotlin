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
 * thread) return the inner store's in-progress state, matching core Redux. The
 * mirror is published after listeners run, so off-context readers never observe
 * a mid-listener tear; reads are otherwise eventually consistent and strictly
 * weaker than a fully synchronized store (the intentional trade for non-blocking
 * reads). See the design spec for the full consistency model.
 */
public interface ConcurrentStore<State> : Store<State>

/**
 * The v1 `CallerSerialized` strategy: the calling thread runs the full pipeline
 * under a reentrant lock; readers go lock-free through the mirror.
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
    private val listeners = atomic<List<StoreSubscriber>>(emptyList())

    init {
        inner.subscribe {
            val snapshot = listeners.value
            snapshot.forEach { subscriber ->
                notificationContext.post {
                    try {
                        subscriber()
                    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                        onError(t)
                    }
                }
            }
        }
        val pipeline = inner.dispatch
        inner.dispatch = { action -> sequenced(pipeline, action) }
    }

    private fun sequenced(pipeline: Dispatcher, action: Any): Any = synchronized(lock) {
        context.enter()
        try {
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
        listeners.update { it + subscriber }
        val subscribed = atomic(true)
        val unsub: StoreSubscription = {
            if (subscribed.compareAndSet(expect = true, update = false)) {
                listeners.update { it - subscriber }
            }
        }
        unsub
    }

    override val replaceReducer: (Reducer<State>) -> Unit = { nextReducer ->
        inner.replaceReducer(nextReducer)
    }
}
