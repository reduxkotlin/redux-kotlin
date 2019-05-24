package org.reduxkotlin

/**
 * see also https://github.com/reactjs/redux/blob/master/docs/Glossary.md#reducer
 */
typealias Reducer = (state: Any, action: Any) -> Any
typealias GetState<S> = () -> S
typealias StoreSubscriber = ()-> Unit
typealias StoreSubscription = () -> Unit
typealias Dispatcher = (Any)->Any
typealias StoreCreator<S> = (reducer: Reducer<S>, initialState: S) -> Store<S>
/**
 * get a store creator and return a new enhanced one
 * see https://github.com/reactjs/redux/blob/master/docs/Glossary.md#store-enhancer
 */
typealias StoreEnhancer<S> = (next: StoreCreator<S>) -> StoreCreator<S>

/**
 * see also https://github.com/reactjs/redux/blob/master/docs/Glossary.md#middleware
 */
typealias Middleware<State> = (getState: GetState<State>, nextDispatcher: Dispatcher, action: Any) -> Any


data class Store<S>(val getState: GetState<S>,
                    var dispatch: Dispatcher,
                    val subscribe: (StoreSubscriber) -> StoreSubscription,
                    val replaceReducer: (Reducer<S>) -> Unit) {
    val state: S
        get() = getState()
}
