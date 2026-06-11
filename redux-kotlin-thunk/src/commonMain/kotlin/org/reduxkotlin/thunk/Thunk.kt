package org.reduxkotlin.thunk

import org.reduxkotlin.Dispatcher
import org.reduxkotlin.GetState
import org.reduxkotlin.Middleware

/**
 * Specialised async action type that can be dispatched to a store with a registered [ThunkMiddleware].
 *
 * A thunk is a function receiving the store's `dispatch` and `getState` plus the optional
 * `extraArg` configured on [createThunkMiddleware]; its return value is returned from `dispatch`.
 */
public typealias Thunk<State> = (dispatch: Dispatcher, getState: GetState<State>, extraArg: Any?) -> Any

/**
 * Middleware that intercepts dispatched [Thunk] functions and executes them.
 *
 * @see createThunkMiddleware
 */
public typealias ThunkMiddleware<State> = Middleware<State>

/**
 * Creates a thunk middleware for async action dispatches.
 *
 * Usage:
 * ```
 * val thunk = createThunkMiddleware<AppState>()
 * val store = createStore(myReducer, initialState, applyMiddleware(thunk, myMiddleware))
 *
 * fun myNetworkThunk(query: String): Thunk<AppState> = { dispatch, getState, extraArgument ->
 *     launch {
 *         dispatch(LoadingAction())
 *         // do async stuff
 *         val result = api.fetch(query)
 *         dispatch(CompleteAction(result))
 *     }
 * }
 *
 * store.dispatch(myNetworkThunk("query"))
 * ```
 *
 * @param State the store's state type.
 * @param extraArgument optional dependency (e.g. an api client) passed to every thunk as `extraArg`.
 */
public fun <State> createThunkMiddleware(extraArgument: Any? = null): ThunkMiddleware<State> = { store ->
    { next: Dispatcher ->
        { action: Any ->
            if (action is Function<*>) {
                @Suppress("UNCHECKED_CAST")
                val thunk = try {
                    (action as Thunk<*>)
                } catch (e: ClassCastException) {
                    throw IllegalArgumentException(
                        "Dispatching functions must use type Thunk<State>",
                        e,
                    )
                }
                thunk(store.dispatch, store.getState, extraArgument)
            } else {
                next(action)
            }
        }
    }
}
