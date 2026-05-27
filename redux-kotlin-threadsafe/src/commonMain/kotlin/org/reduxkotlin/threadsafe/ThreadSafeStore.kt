package org.reduxkotlin.threadsafe

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.reduxkotlin.Dispatcher
import org.reduxkotlin.GetState
import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscriber
import org.reduxkotlin.StoreSubscription

/**
 * Threadsafe wrapper for ReduxKotlin store that synchronizes access to each function using
 * kotlinx.AtomicFu [https://github.com/Kotlin/kotlinx.atomicfu]
 * Allows all store functions to be accessed from any thread.
 * This does have a performance impact for JVM/Native.
 * TODO more info at [https://ReduxKotlin.org]
 */
public class ThreadSafeStore<State>(override val store: Store<State>) :
    SynchronizedObject(),
    Store<State> {
    override var dispatch: Dispatcher = { action ->
        synchronized(this) { store.dispatch(action) }
    }

    override val getState: GetState<State> = {
        synchronized(this) { store.getState() }
    }

    override val replaceReducer: (Reducer<State>) -> Unit = { reducer ->
        synchronized(this) { store.replaceReducer(reducer) }
    }

    override val subscribe: (StoreSubscriber) -> StoreSubscription = { storeSubscriber ->
        val inner = synchronized(this) { store.subscribe(storeSubscriber) }
        // The returned StoreSubscription mutates the underlying store's
        // listener list when invoked; without re-acquiring the lock, a
        // teardown racing against a concurrent dispatch can corrupt the
        // list or throw ConcurrentModificationException from inside the
        // upstream store's iteration loop. Wrap it so unsubscribe takes
        // the same lock as subscribe and dispatch.
        val unsubscribe: StoreSubscription = { synchronized(this) { inner() } }
        unsubscribe
    }
}

@Deprecated(
    "Renamed to ThreadSafeStore",
    replaceWith = ReplaceWith(
        expression = "ThreadSafeStore",
        "org.reduxkotlin.threadsafe.ThreadSafeStore",
    ),
)
public typealias SynchronizedStore<State> = ThreadSafeStore<State>
