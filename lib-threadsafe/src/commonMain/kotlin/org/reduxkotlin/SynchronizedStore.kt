package org.reduxkotlin

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Threadsafe wrapper for ReduxKotlin store that synchronizes access to each function using
 * kotlinx.AtomicFu [https://github.com/Kotlin/kotlinx.atomicfu]
 * Allows all store functions to be accessed from any thread.
 * This does have a performance impact for JVM/Native.
 * TODO more info at [https://ReduxKotlin.org]
 */
class SynchronizedStore<TState>(private val store: Store<TState>) : Store<TState>, SynchronizedObject() {

    override var dispatch: Dispatcher = { action ->
        synchronized(this) { store.dispatch(action) }
    }

    override val getState: GetState<TState> = {
        synchronized(this) { store.getState() }
    }

    override val replaceReducer: (Reducer<TState>) -> Unit = { reducer ->
        synchronized(this) { store.replaceReducer(reducer) }
    }

    override val subscribe: (StoreSubscriber) -> StoreSubscription = { storeSubscriber ->
        synchronized(this) { store.subscribe(storeSubscriber) }
    }
}
