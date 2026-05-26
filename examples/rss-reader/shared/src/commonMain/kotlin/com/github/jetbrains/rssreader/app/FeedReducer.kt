package com.github.jetbrains.rssreader.app

import org.reduxkotlin.TypedReducer

/**
 * Pure reducer. All side effects (HTTP, storage, error broadcasting) are handled
 * by thunks in `FeedThunks.kt` and by the side-effect middleware — never here.
 *
 * Actions that are invalid for the current state (e.g. Refresh while progress=true,
 * or Data while progress=false) are treated as no-ops here. The thunk layer is
 * responsible for not dispatching such actions; the original NanoRedux store also
 * emitted a FeedSideEffect.Error("Unexpected action") in those cases — that behavior
 * will move to `SideEffectMiddleware.kt` in Phase 3.
 */
val feedReducer: TypedReducer<FeedState, FeedAction> = { state, action ->
    when (action) {
        is FeedAction.Refresh -> if (state.progress) state else state.copy(progress = true)
        is FeedAction.Add -> if (state.progress) state else state.copy(progress = true)
        is FeedAction.Delete -> if (state.progress) state else state.copy(progress = true)
        is FeedAction.SelectFeed -> when {
            action.feed == null -> state.copy(selectedFeed = null)
            state.feeds.contains(action.feed) -> state.copy(selectedFeed = action.feed)
            else -> state
        }
        is FeedAction.Data -> if (state.progress) {
            val preservedSelection = state.selectedFeed?.takeIf { it in action.feeds }
            state.copy(progress = false, feeds = action.feeds, selectedFeed = preservedSelection)
        } else state
        is FeedAction.Error -> if (state.progress) state.copy(progress = false) else state
    }
}
