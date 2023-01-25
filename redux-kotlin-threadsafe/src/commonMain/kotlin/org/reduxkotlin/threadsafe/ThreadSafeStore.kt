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
    Store<State>,
    SynchronizedObject() {
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
        synchronized(this) { store.subscribe(storeSubscriber) }
    }
}

@Deprecated(
    "Renamed to ThreadSafeStore",
    replaceWith = ReplaceWith(
        expression = "ThreadSafeStore",
        "org.reduxkotlin.threadsafe.ThreadSafeStore"
    )
)
public typealias SynchronizedStore<State> = ThreadSafeStore<State>
