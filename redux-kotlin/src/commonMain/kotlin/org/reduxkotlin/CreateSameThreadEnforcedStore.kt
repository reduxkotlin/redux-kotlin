package org.reduxkotlin

import org.reduxkotlin.utils.getThreadName
import org.reduxkotlin.utils.stripCoroutineName

/**
 * Creates a Redux store that can only be accessed from the same thread.
 * Any call to the store's functions called from a thread other than thread
 * from which it was created will throw an IllegalStateException.
 *
 * Most use cases will want to use [createThreadSafeStore] or [createStore]
 * More details:   TODO add documentation link
 *
 * see [createStore] for details on store params/behavior
 */
public fun <State> createSameThreadEnforcedStore(
    reducer: Reducer<State>,
    preloadedState: State,
    enhancer: StoreEnhancer<State>? = null
): Store<State> {
    val store = createStore(reducer, preloadedState, enhancer)
    val storeThreadName = stripCoroutineName(getThreadName())
    fun isSameThread() = stripCoroutineName(getThreadName()) == storeThreadName
    fun checkSameThread() = check(isSameThread()) {
        """
      |You may not call the store from a thread other than the thread on which it was created.
      |This includes: getState(), dispatch(), subscribe(), and replaceReducer()
      |This store was created on: '$storeThreadName' and current
      |thread is '${getThreadName()}'
        """.trimMargin()
    }

    return object : Store<State> {
        override val getState = {
            checkSameThread()
            store.getState()
        }

        override var dispatch: Dispatcher = { action ->
            checkSameThread()
            store.dispatch(action)
        }

        override val subscribe = { storeSubscriber: StoreSubscriber ->
            checkSameThread()
            store.subscribe(storeSubscriber)
        }

        override val replaceReducer = { reducer: Reducer<State> ->
            checkSameThread()
            store.replaceReducer(reducer)
        }
    }
}
