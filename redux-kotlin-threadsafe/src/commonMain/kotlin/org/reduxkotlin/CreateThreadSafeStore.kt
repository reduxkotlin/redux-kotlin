package org.reduxkotlin

/**
 * Creates a SYNCHRONIZED, THREADSAFE Redux store that holds the state tree.
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
fun <State> createThreadSafeStore(
  reducer: Reducer<State>,
  preloadedState: State,
  enhancer: StoreEnhancer<State>? = null
): Store<State> = SynchronizedStore(createStore(reducer, preloadedState, enhancer))
