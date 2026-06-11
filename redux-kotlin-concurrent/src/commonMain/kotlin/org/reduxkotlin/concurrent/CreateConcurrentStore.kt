package org.reduxkotlin.concurrent

import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.TypedReducer
import org.reduxkotlin.TypedStore
import org.reduxkotlin.asTyped
import org.reduxkotlin.createStore
import org.reduxkotlin.typedReducer

/**
 * Creates a [ConcurrentStore]: a thread-safe Redux store with lock-free
 * (non-blocking) `getState`/`subscribe` and writes serialized through a
 * reentrant writer lock (the `CallerSerialized` strategy).
 *
 * Install middleware via [enhancer] (never pre-applied to a foreign store) so
 * middleware re-dispatch is routed through the sequencer.
 *
 * @param State the application state type.
 * @param reducer the root reducer.
 * @param preloadedState the initial state. Must be deeply immutable.
 * @param notificationContext where listener callbacks and [onError] run; the
 *  default [NotificationContext.Inline] runs every subscriber while the writer
 *  lock is held — a slow subscriber delays concurrent dispatchers (and
 *  `replaceReducer`), never readers. UI consumers should pass a main-thread
 *  context (e.g. [coalescingNotificationContext]); supply a posting/coalescing
 *  context if subscribers can be slow.
 * @param onError isolates listener throwables (default logs and continues). Must
 *  not throw; a throw from the handler itself is printed and swallowed — dispatch
 *  and delivery to remaining subscribers always proceed.
 * @param enhancer optional store enhancer (e.g. `applyMiddleware(...)`).
 */
public fun <State> createConcurrentStore(
    reducer: Reducer<State>,
    preloadedState: State,
    notificationContext: NotificationContext = NotificationContext.Inline,
    onError: (Throwable) -> Unit = LogAndContinue,
    enhancer: StoreEnhancer<State>? = null,
): ConcurrentStore<State> = CallerSerializedStore(
    inner = createStore(reducer, preloadedState, enhancer),
    notificationContext = notificationContext,
    onError = onError,
)

/**
 * Creates a typed [ConcurrentStore]. See [createConcurrentStore].
 */
public inline fun <State, reified Action : Any> createTypedConcurrentStore(
    crossinline reducer: TypedReducer<State, Action>,
    preloadedState: State,
    notificationContext: NotificationContext = NotificationContext.Inline,
    noinline onError: (Throwable) -> Unit = LogAndContinue,
    noinline enhancer: StoreEnhancer<State>? = null,
): TypedStore<State, Action> = createConcurrentStore(
    reducer = typedReducer(reducer),
    preloadedState = preloadedState,
    notificationContext = notificationContext,
    onError = onError,
    enhancer = enhancer,
).asTyped()

/**
 * Adopts an existing [Store] as a [ConcurrentStore] by re-pointing its `dispatch`
 * to route through the sequencer. Safe for bare and middleware-enhanced stores.
 *
 * Caveat (same as core Redux): middleware that captured the dispatch *function by
 * value* at construction, or captured a foreign store object, is not intercepted.
 * Prefer the [enhancer] argument of [createConcurrentStore].
 *
 * [notificationContext] and [onError] carry the same contracts as on
 * [createConcurrentStore].
 */
public fun <State> Store<State>.asConcurrent(
    notificationContext: NotificationContext = NotificationContext.Inline,
    onError: (Throwable) -> Unit = LogAndContinue,
): ConcurrentStore<State> = CallerSerializedStore(
    inner = this.store,
    notificationContext = notificationContext,
    onError = onError,
)
