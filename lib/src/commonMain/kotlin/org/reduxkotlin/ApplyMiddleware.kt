package org.reduxkotlin

/**
 * Creates a store enhancer that applies middleware to the dispatch method
 * of the Redux store. This is handy for a variety of tasks, such as expressing
 * asynchronous actions in a concise manner, or logging every action payload.
 *
 * See `redux-thunk` package as an example of the Redux middleware.
 *
 * Because middleware is potentially asynchronous, this should be the first
 * store enhancer in the composition chain.
 *
 * Note that each middleware will be given the `dispatch` and `getState` functions
 * as named arguments.
 *
 * @param {vararg Middleware} [middleware] The middleware chain to be applied.
 * @returns {StoreEnhancer} A store enhancer applying the middleware.
 */
fun <S : Any> applyMiddleware(vararg middlewares: Middleware<S>): StoreEnhancer<S> {
    return { storeCreator ->
        { reducer, initialState ->
            val store = storeCreator(reducer, initialState)
            //TODO determine if handling dispatching while constructing middleware is needed.
            //reduxjs throws an exception if action is dispatched before applymiddleware is complete
            /*
            var dispatch: Dispatcher = { action: Any ->
                throw Exception(
                        """Dispatching while constructing your middleware is not allowed.
                    Other middleware would not be applied to this dispatch.""")
            }
             */

            val combinedDispatch = middlewares.foldRight(store.dispatch) { middleware, next -> {action -> middleware(store.getState, next, action)}}
            Store(getState = store.getState,
                    dispatch = combinedDispatch,
                    subscribe = store.subscribe,
                    replaceReducer = store.replaceReducer)
        }
    }
}
