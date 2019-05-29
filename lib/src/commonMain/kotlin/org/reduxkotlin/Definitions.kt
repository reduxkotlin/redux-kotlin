package org.reduxkotlin

import kotlin.reflect.KClass

/**
 * see also https://github.com/reactjs/redux/blob/master/docs/Glossary.md#reducer
 */
typealias Reducer = (state: Any, action: Any) -> Any

typealias GetState = () -> Any
typealias StoreSubscriber = () -> Unit
typealias StoreSubscription = () -> Unit
typealias Dispatcher = (Any) -> Any
typealias StoreCreator = (reducer: Reducer, initialState: Any, s: StoreEnhancerWrapper?) -> Store
/**
 * get a store creator and return a new enhanced one
 * see https://github.com/reactjs/redux/blob/master/docs/Glossary.md#store-enhancer
 */
typealias StoreEnhancer = (next: StoreCreator) -> StoreCreator

/**
 * wrapper class is needed here to avoid a recursive type declaration.
 */
class StoreEnhancerWrapper(val storeEnhancer2: StoreEnhancer) : StoreEnhancer {
    override fun invoke(p1: StoreCreator): StoreCreator {
        return storeEnhancer2(p1)
    }
}

/**
 * see also https://github.com/reactjs/redux/blob/master/docs/Glossary.md#middleware
 */
typealias Middleware = (store: Store) -> (next: Dispatcher) -> (action: Any) -> Any


data class Store(
    val getState: GetState,
    var dispatch: Dispatcher,
    val subscribe: (StoreSubscriber) -> StoreSubscription,
    val replaceReducer: (Reducer) -> Unit
) {
    val state: Any
        get() = getState()
}

/**
 * Convenience function for creating a middleware
 * usage:
 *    val myMiddleware = middleware { store, dispatch, action -> doStuff() }
 */
fun middleware(dispatch: (Store, dispatch: Dispatcher, action: Any) -> Any): Middleware =
    { store ->
        { next ->
            { action: Any ->
                {
                    dispatch(store, next, action)
                }
            }
        }
    }

/**
 * Creates a function that returns reducers with state casted to given state.
 * This is to assist in readability of creating reducers and remove the need to cast.
 * usage:
 *   * create reducers with castingReducer:
 *      val reducer = castingReducer { state: MyState, action ->
 *              when (action) {
 *                  is Todo -> state.copy(...)
 *              }
 *          }
 */
inline fun <reified T> castingReducer(crossinline reducer: ((T, Any) -> Any)): Reducer = { state: Any, action: Any ->
    if (T::class.isInstance(state)) {
        reducer(state as T, action)
    } else {
        { state: Any, action: Any -> state }
    }
}
