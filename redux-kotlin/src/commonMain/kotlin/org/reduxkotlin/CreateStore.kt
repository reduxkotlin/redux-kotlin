package org.reduxkotlin

import org.reduxkotlin.utils.isPlainObject

/**
 * Creates a NON-THREADSAFE Redux store that holds the state tree.
 * If your application needs thread-safety access to store consider [createThreadSafeStore]
 * see:
 *  https://reduxkotlin.org/api/createThreadSafeStore
 *  https://www.reduxkotlin.org/introduction/threading
 *
 * The only way to change the data in the store is to call `dispatch()` on it.
 *
 * There should only be a single store in your app. To specify how different
 * parts of the state tree respond to actions, you may combine several reducers
 * into a single reducer function by using `combineReducers`.
 *
 * @param {Reducer} [reducer] A function that returns the next state tree, given
 * the current state tree and the action to handle.
 *
 * @param {Any} [preloadedState] The initial state. You may optionally specify
 * it to hydrate the state from the server in universal apps, or to restore a
 * previously serialized user session.
 *
 * @param {Enhancer} [enhancer] The store enhancer. You may optionally specify
 * it to enhance the store with third-party capabilities such as middleware,
 * time travel, persistence, etc. The only store enhancer that ships with Redux
 * is `applyMiddleware()`.
 *
 * @returns {Store} A Redux store that lets you read the state, dispatch actions
 * and subscribe to changes.
 */
public fun <State> createStore(
    reducer: Reducer<State>,
    preloadedState: State,
    enhancer: StoreEnhancer<State>? = null
): Store<State> {
    if (enhancer != null) {
        return enhancer { r, initialState, _ -> createStore(r, initialState) }(
            reducer,
            preloadedState,
            null
        )
    }

    var currentReducer = reducer
    var currentState = preloadedState
    var currentListeners = mutableListOf<() -> Unit>()
    var nextListeners = currentListeners
    var isDispatching = false

    /**
     * This makes a shallow copy of currentListeners so we can use
     * nextListeners as a temporary list while dispatching.
     *
     * This prevents any bugs around consumers calling
     * subscribe/unsubscribe in the middle of a dispatch.
     */
    fun ensureCanMutateNextListeners() {
        if (nextListeners === currentListeners) {
            nextListeners = currentListeners.toMutableList()
        }
    }

    /**
     * Reads the state tree managed by the store.
     *
     * @returns {S} The current state tree of your application.
     */
    fun getState(): State {
        check(!isDispatching) {
            """
        |You may not call store.getState() while the reducer is executing.
        |The reducer has already received the state as an argument.
        |Pass it down from the top reducer instead of reading it from the 
        |store.
        |You may be accessing getState while dispatching from another thread.
        |Try createThreadSafeStore().
        |https://reduxkotlin.org/introduction/threading
            """.trimMargin()
        }

        return currentState
    }

    /**
     * Adds a change listener. It will be called any time an action is
     * dispatched, and some part of the state tree may potentially have changed.
     * You may then call `getState()` to read the current state tree inside the
     * callback.
     *
     * You may call `dispatch()` from a change listener, with the following
     * caveats:
     *
     * 1. The subscriptions are snapshotted just before every `dispatch()` call.
     * If you subscribe or unsubscribe while the listeners are being invoked,
     * this will not have any effect on the `dispatch()` that is currently in
     * progress. However, the next `dispatch()` call, whether nested or not,
     * will use a more recent snapshot of the subscription list.
     *
     * 2. The listener should not expect to see all state changes, as the state
     * might have been updated multiple times during a nested `dispatch()`
     * before the listener is called. It is, however, guaranteed that all
     * subscribers registered before the `dispatch()` started will be called
     * with the latest state by the time it exits.
     *
     * @param {StoreSubscriber} [listener] A callback to be invoked on every
     * dispatch.
     * @returns {StoreSubscription} A fun  to remove this change listener.
     */
    fun subscribe(listener: StoreSubscriber): StoreSubscription {
        check(!isDispatching) {
            """|You may not call store.subscribe() while the reducer is executing.
             |If you would like to be notified after the store has been updated, 
             |subscribe from a component and invoke store.getState() in the 
             |callback to access the latest state. See 
             |https://www.reduxkotlin.org/api/store#subscribelistener-storesubscriber
             |for more details.
             |You may be seeing this due accessing the store from multiplethreads.
             |Try createThreadSafeStore()
             |https://reduxkotlin.org/introduction/threading
            """.trimMargin()
        }

        var isSubscribed = true

        ensureCanMutateNextListeners()
        nextListeners.add(listener)

        return {
            if (!isSubscribed) {
                Unit
            }

            check(!isDispatching) {
                """You may not unsubscribe from a store listener while the reducer
                 |is executing. See 
                 |https://www.reduxkotlin.org/api/store#subscribelistener-storesubscriber
                 |for more details.
                """.trimMargin()
            }

            isSubscribed = false

            ensureCanMutateNextListeners()
            val index = nextListeners.indexOf(listener)
            nextListeners.removeAt(index)
        }
    }

    /**
     * Dispatches an action. It is the only way to trigger a state change.
     *
     * The `reducer` function, used to create the store, will be called with the
     * current state tree and the given `action`. Its return value will
     * be considered the **next** state of the tree, and the change listeners
     * will be notified.
     *
     * The base implementation only supports plain object actions. If you want
     * to dispatch something else, such as a function or 'thunk' you need to
     * wrap your store creating function into the corresponding middleware. For
     * example, see the documentation for the `redux-thunk` package. Even the
     * middleware will eventually dispatch plain object actions using this
     * method.
     *
     * @param {Any} [action] A plain object representing “what changed”. It is
     * a good idea to keep actions serializable so you can record and replay
     * user sessions, or use the time travelling `redux-devtools`.
     *
     * @returns {Any} For convenience, the same action object you dispatched.
     *
     * Note that, if you use a custom middleware, it may wrap `dispatch()` to
     * return something else (for example, a Promise you can await).
     */
    fun dispatch(action: Any): Any {
        require(isPlainObject(action)) {
            """Actions must be plain objects. Use custom middleware for async 
            |actions.
            """.trimMargin()
        }

        /*
        check(!isDispatching) {
            """You may not dispatch while state is being reduced.
            |2 conditions can cause this error:
            |    1) Dispatching from a reducer
            |    2) Dispatching from multiple threads
            |If #2 switch to createThreadSafeStore().
            |https://reduxkotlin.org/introduction/threading""".trimMargin()
        }

         */

        try {
            isDispatching = true
            currentState = currentReducer(currentState, action)
        } finally {
            isDispatching = false
        }

        val listeners = nextListeners
        currentListeners = nextListeners
        listeners.forEach { it() }

        return action
    }

    /**
     * Replaces the reducer currently used by the store to calculate the state.
     *
     * You might need this if your app implements code splitting and you want to
     * load some of the reducers dynamically. You might also need this if you
     * implement a hot reloading mechanism for Redux.
     *
     * @param {function} nextReducer The reducer for the store to use instead.
     * @returns {void}
     */
    fun replaceReducer(nextReducer: Reducer<State>) {
        currentReducer = nextReducer

        // This action has a similar effect to ActionTypes.INIT.
        // Any reducers that existed in both the new and old rootReducer
        // will receive the previous state. This effectively populates
        // the new state tree with any relevant data from the old one.
        dispatch(ActionTypes.REPLACE)
    }

    /**
     * Interoperability point for observable/reactive libraries.
     * @returns {observable} A minimal observable of state changes.
     * For more information, see the observable proposal:
     * https://github.com/tc39/proposal-observable
     *//* TODO: consider kotlinx.coroutines.flow?

   */

    // When a store is created, an "INIT" action is dispatched so that every
    // reducer returns their initial state. This effectively populates
    // the initial state tree.
    dispatch(ActionTypes.INIT)

    return object : Store<State> {
        override val getState = ::getState
        override var dispatch: Dispatcher = ::dispatch
        override val subscribe = ::subscribe
        override val replaceReducer = ::replaceReducer
    }
}

/**
 * Creates a [TypedStore]. For further details see the matching [createStore].
 */
public inline fun <State, reified Action : Any> createTypedStore(
    crossinline reducer: TypedReducer<State, Action>,
    preloadedState: State,
    noinline enhancer: StoreEnhancer<State>? = null
): TypedStore<State, Action> {
    val store = createStore(
        reducer = typedReducer(reducer),
        preloadedState,
        enhancer,
    )
    return object : TypedStore<State, Action> {
        override val getState: GetState<State> = store.getState
        override var dispatch: TypedDispatcher<Action> = store.dispatch
        override val subscribe: (StoreSubscriber) -> StoreSubscription = store.subscribe
        override val replaceReducer: (TypedReducer<State, Action>) -> Unit = {
            store.replaceReducer(typedReducer(it))
        }
    }
}
