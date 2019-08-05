package org.reduxkotlin


/**
 * see also https://github.com/reactjs/redux/blob/master/docs/Glossary.md#reducer
 */
typealias Reducer<State> = (state: State, action: Any) -> State

typealias GetState<State> = () -> State
typealias StoreSubscriber = () -> Unit
typealias StoreSubscription = () -> Unit
typealias Dispatcher = (Any) -> Any
//Enhancer is type Any? to avoid a circular dependency of types
typealias StoreCreator<State> = (reducer: Reducer<State>, initialState: State, enhancer: Any?) -> Store<State>

/**
 * Take a store creator and return a new enhanced one
 * see https://github.com/reactjs/redux/blob/master/docs/Glossary.md#store-enhancer
 */
typealias StoreEnhancer<State> = (StoreCreator<State>) -> StoreCreator<State>

/**
 *  https://github.com/reactjs/redux/blob/master/docs/Glossary.md#middleware
 */
typealias Middleware<State> = (store: Store<State>) -> (next: Dispatcher) -> (action: Any) -> Any


data class Store<State>(
    val getState: GetState<State>,
    var dispatch: Dispatcher,
    val subscribe: (StoreSubscriber) -> StoreSubscription,
    val replaceReducer: (Reducer<State>) -> Unit
) {
    val state: State
        get() = getState()
}

/**
 * Convenience function for creating a middleware
 * usage:
 *    val myMiddleware = middleware { store, next, action -> doStuff() }
 */
fun <State> middleware(dispatch: (Store<State>, next: Dispatcher, action: Any) -> Any): Middleware<State> =
    { store ->
        { next ->
            { action: Any ->
                dispatch(store, next, action)
            }
        }
    }
