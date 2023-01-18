package org.reduxkotlin.threadsafe

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.reduxkotlin.*

/**
 * Threadsafe wrapper for ReduxKotlin store that synchronizes access to each function using
 * kotlinx.AtomicFu [https://github.com/Kotlin/kotlinx.atomicfu]
 * Allows all store functions to be accessed from any thread.
 * This does have a performance impact for JVM/Native.
 * TODO more info at [https://ReduxKotlin.org]
 */
public class SynchronizedStore<State, Action>(private val store: TypedStore<State, Action>) : TypedStore<State, Action>,
    SynchronizedObject() {

    override var dispatch: TypedDispatcher<Action> = { action ->
        synchronized(this) { store.dispatch(action) }
    }

    override val getState: GetState<State> = {
        synchronized(this) { store.getState() }
    }

    override val replaceReducer: (TypedReducer<State, Action>) -> Unit = { reducer ->
        synchronized(this) { store.replaceReducer(reducer) }
    }

    override val subscribe: (StoreSubscriber) -> StoreSubscription = { storeSubscriber ->
        synchronized(this) { store.subscribe(storeSubscriber) }
    }
}
