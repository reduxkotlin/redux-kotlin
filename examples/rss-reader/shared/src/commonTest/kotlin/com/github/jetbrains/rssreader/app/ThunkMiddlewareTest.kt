package com.github.jetbrains.rssreader.app

import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThunkMiddlewareTest {

    @Test
    fun thunkIsInvokedWithDispatchAndGetStateAndIsNotPassedToReducer() {
        var reducerCallCount = 0
        val store = createStore<FeedState>(
            reducer = { state, _ ->
                reducerCallCount++
                state
            },
            preloadedState = FeedState(progress = false, feeds = emptyList()),
            enhancer = applyMiddleware(thunkMiddleware()),
        )
        // After createStore, the reducer has been called exactly once with the INIT action.
        assertEquals(1, reducerCallCount)

        var thunkRan = false
        val noopThunk: Thunk = { _, getState ->
            thunkRan = true
            assertEquals(false, getState().progress)
            Unit
        }

        store.dispatch(noopThunk)

        assertTrue(thunkRan)
        // The reducer count must NOT have advanced — the thunk short-circuited dispatch.
        assertEquals(1, reducerCallCount)
    }

    @Test
    fun nonThunkActionsPassThroughToReducer() {
        var lastSeenByReducer: Any? = null
        val store = createStore<FeedState>(
            reducer = { state, action ->
                lastSeenByReducer = action
                state
            },
            preloadedState = FeedState(progress = false, feeds = emptyList()),
            enhancer = applyMiddleware(thunkMiddleware()),
        )
        // INIT has already populated lastSeenByReducer — zero it so the assertion below
        // proves the SelectFeed dispatch reached the reducer, not just that INIT did.
        lastSeenByReducer = null
        store.dispatch(FeedAction.SelectFeed(null))
        assertEquals(FeedAction.SelectFeed(null), lastSeenByReducer)
    }
}
