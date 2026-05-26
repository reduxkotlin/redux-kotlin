package com.github.jetbrains.rssreader.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.reduxkotlin.Store
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore

/**
 * Holds the redux-kotlin [Store], plus the side-effect [SharedFlow] and a derived
 * [StateFlow] that Compose can `collectAsState()` against.
 *
 * One instance per app via Koin (Phase 6).
 */
class FeedStoreHolder(
    private val sideEffectBus: SideEffectBus = SideEffectBus(),
) {
    val store: Store<FeedState> = createStore(
        reducer = { state, action ->
            // FeedReducer is a TypedReducer<FeedState, FeedAction>; fit it into the
            // untyped Reducer<FeedState> = (FeedState, Any) -> FeedState shape by
            // filtering on the action type and letting unknown actions fall through.
            when (action) {
                is FeedAction -> feedReducer(state, action)
                else -> state
            }
        },
        preloadedState = FeedState(progress = false, feeds = emptyList()),
        enhancer = applyMiddleware(
            sideEffectBus.middleware(), // intercept FeedSideEffect first
            thunkMiddleware(),          // then handle Thunks
        ),
    )

    val state: StateFlow<FeedState> = store.toStateFlow()

    val sideEffects: SharedFlow<FeedSideEffect> = sideEffectBus.effects
}

/**
 * Bridges a redux-kotlin [Store] to a Kotlin [StateFlow] so Compose can subscribe with
 * `collectAsState()`. The subscription lives for the lifetime of the store (process).
 */
private fun <S> Store<S>.toStateFlow(): StateFlow<S> {
    val flow = MutableStateFlow(state)
    subscribe { flow.value = state }
    return flow.asStateFlow()
}
