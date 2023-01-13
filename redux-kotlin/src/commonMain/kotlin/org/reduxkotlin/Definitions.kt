package org.reduxkotlin

/**
 * See also https://github.com/reactjs/redux/blob/master/docs/Glossary.md#reducer
 */
public typealias Reducer<State> = (state: State, action: Any) -> State

/**
 * Reducer for a particular subclass of actions.  Useful for Sealed classes &
 * exhaustive when statements.  See [reducerForActionType].
 */
public typealias ReducerForActionType<TState, TAction> = (state: TState, action: TAction) -> TState

public typealias GetState<State> = () -> State
public typealias StoreSubscriber = () -> Unit
public typealias StoreSubscription = () -> Unit
public typealias Dispatcher = (Any) -> Any

// Enhancer is type Any? to avoid a circular dependency of types.
public typealias StoreCreator<State> = (
  reducer: Reducer<State>,
  initialState: State,
  enhancer: Any?
) -> Store<State>

/**
 * Take a store creator and return a new enhanced one
 * see https://github.com/reactjs/redux/blob/master/docs/Glossary.md#store-enhancer
 */
public typealias StoreEnhancer<State> = (StoreCreator<State>) -> StoreCreator<State>

/**
 *  https://github.com/reactjs/redux/blob/master/docs/Glossary.md#middleware
 */
public typealias Middleware<State> = (store: Store<State>) -> (next: Dispatcher) -> (action: Any) -> Any

public interface Store<State> {
  public val getState: GetState<State>
  public var dispatch: Dispatcher
  public val subscribe: (StoreSubscriber) -> StoreSubscription
  public val replaceReducer: (Reducer<State>) -> Unit
  public val state: State get() = getState()
}

/**
 * Convenience function for creating a [Middleware]
 * usage:
 *    val myMiddleware = middleware { store, next, action -> doStuff() }
 */
public fun <State> middleware(dispatch: (Store<State>, next: Dispatcher, action: Any) -> Any): Middleware<State> =
  { store ->
    { next ->
      { action: Any ->
        dispatch(store, next, action)
      }
    }
  }

/**
 * Convenience function for creating a [ReducerForActionType]
 * usage:
 *   sealed class LoginScreenAction
 *   data class LoginComplete(val user: User): LoginScreenAction()
 *
 *   val loginReducer = reducerForActionType<AppState, LoginAction> { state, action ->
 *       when(action) {
 *           is LoginComplete -> state.copy(user = action.user)
 *       }
 *   }
 *
 *   sealed class FeedScreenAction
 *   data class FeedLoaded(val items: FeedItems): FeedScreenAction
 *   data class FeedLoadError(val msg: String): FeedScreenAction
 *
 *   val feedReducer = reducerForActionType<AppState, FeedScreeAction> { state, action ->
 *       when(action) {
 *          is FeedLoaded -> state.copy(feedItems = action.items)
 *          is FeedLoadError -> state.copy(errorMsg = action.msg)
 *       }
 *   }
 *
 *   val rootReducer = combineReducers(loginReducer, feedReducer)
 *   val store = createStore(rootReducer, AppState())
 *      **or**
 *   val store = createThreadSafeStore(rootReducer, AppState())
 */
public inline fun <TState, reified TAction> reducerForActionType(
  crossinline reducer: ReducerForActionType<TState, TAction>
): Reducer<TState> = { state, action ->
  when (action) {
    is TAction -> reducer(state, action)
    else -> state
  }
}
