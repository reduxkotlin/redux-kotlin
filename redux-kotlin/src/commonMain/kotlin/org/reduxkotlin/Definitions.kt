package org.reduxkotlin

/**
 * See also https://github.com/reactjs/redux/blob/master/docs/Glossary.md#reducer
 */
public typealias Reducer<State> = TypedReducer<State, in Any>

/**
 * Reducer for a particular subclass of actions.  Useful for Sealed classes &
 * exhaustive when statements.  See [typedReducer].
 */
public typealias TypedReducer<State, Action> = (state: State, action: Action) -> State

@Deprecated(
    message = "Renamed to TypedReducer",
    replaceWith = ReplaceWith(
        expression = "TypedReducer",
        imports = arrayOf("org.reduxkotlin.TypedReducer"),
    )
)
public typealias ReducerForActionType<TState, TAction> = (state: TState, action: TAction) -> TState

public typealias GetState<State> = () -> State
public typealias StoreSubscriber = () -> Unit
public typealias StoreSubscription = () -> Unit
public typealias Dispatcher = TypedDispatcher<Any>
public typealias TypedDispatcher<Action> = (Action) -> Any

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

/**
 * Main redux storage container for a given [State]
 */
public typealias Store<State> = TypedStore<State, Any>

/**
 * Converts this [Store]<[State]> to [TypedStore]<[State], [Action]> that delegates all actions to the original store.
 */
public inline fun <State, reified Action : Any> Store<State>.asTyped(): TypedStore<State, Action> =
    object : TypedStore<State, Action> {
        override val getState: GetState<State> = this@asTyped.getState
        override var dispatch: TypedDispatcher<Action> = this@asTyped.dispatch
        override val subscribe: (StoreSubscriber) -> StoreSubscription = this@asTyped.subscribe
        override val replaceReducer: (TypedReducer<State, Action>) -> Unit = {
            this@asTyped.replaceReducer(typedReducer(it))
        }
    }

/**
 * Main redux storage container for a given [State] and typesafe actions
 */
public interface TypedStore<State, Action> {
    /**
     * Current store state getter
     */
    public val getState: GetState<State>

    /**
     * Dispatcher that can be used to update the store state
     */
    public var dispatch: TypedDispatcher<Action>

    /**
     * Subscribes to state's updates.
     * Subscription returns [StoreSubscription] that can be invoked to unsubscribe from further updates.
     */
    public val subscribe: (StoreSubscriber) -> StoreSubscription

    /**
     * Replace store's reducer with a new implementation
     */
    public val replaceReducer: (TypedReducer<State, Action>) -> Unit

    /**
     * Current store state
     */
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
 * Convenience function for creating a [TypedReducer]
 * usage:
 *   sealed class LoginScreenAction
 *   data class LoginComplete(val user: User): LoginScreenAction()
 *
 *   val loginReducer = typedReducer<AppState, LoginAction> { state, action ->
 *       when(action) {
 *           is LoginComplete -> state.copy(user = action.user)
 *       }
 *   }
 *
 *   sealed class FeedScreenAction
 *   data class FeedLoaded(val items: FeedItems): FeedScreenAction
 *   data class FeedLoadError(val msg: String): FeedScreenAction
 *
 *   val feedReducer = typedReducer<AppState, FeedScreeAction> { state, action ->
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
public inline fun <State, reified Action> typedReducer(
    crossinline reducer: TypedReducer<State, Action>
): Reducer<State> = { state, action ->
    when (action) {
        is Action -> reducer(state, action)
        else -> state
    }
}

@Deprecated(
    message = "Replaced with typedReducer",
    replaceWith = ReplaceWith(
        expression = "typedReducer",
        imports = arrayOf("org.reduxkotlin.typedReducer"),
    )
)
public inline fun <TState, reified TAction> reducerForActionType(
    crossinline reducer: TypedReducer<TState, TAction>,
): Reducer<TState> = typedReducer(reducer)
