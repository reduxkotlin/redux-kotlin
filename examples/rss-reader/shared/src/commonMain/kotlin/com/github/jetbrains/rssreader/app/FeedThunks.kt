package com.github.jetbrains.rssreader.app

import com.github.jetbrains.rssreader.core.RssReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.reduxkotlin.Dispatcher
import org.reduxkotlin.GetState

/**
 * A [Thunk] is a function dispatched in place of an action. The thunk middleware
 * (Phase 4: [thunkMiddleware]) recognizes it, invokes it with the store's
 * `dispatch` + `getState`, and short-circuits the reducer.
 */
typealias Thunk = (dispatch: Dispatcher, getState: GetState<FeedState>) -> Any

/**
 * Coroutine scope used by every thunk. In a richer app we'd inject this (and the
 * coroutine context) for testability; for the sample, a top-level Main-dispatched
 * scope mirrors the original [FeedStore].
 */
private val thunkScope = CoroutineScope(Dispatchers.Main)

fun refresh(rssReader: RssReader, forceLoad: Boolean): Thunk = { dispatch, getState ->
    if (getState().progress) {
        dispatch(FeedSideEffect.Error(IllegalStateException("In progress")))
    } else {
        dispatch(FeedAction.Refresh(forceLoad))
        thunkScope.launch {
            try {
                val feeds = rssReader.getAllFeeds(forceLoad)
                dispatch(FeedAction.Data(feeds))
            } catch (e: Exception) {
                dispatch(FeedAction.Error(e))
                dispatch(FeedSideEffect.Error(e))
            }
        }
    }
}

fun addFeed(rssReader: RssReader, url: String): Thunk = { dispatch, getState ->
    if (getState().progress) {
        dispatch(FeedSideEffect.Error(IllegalStateException("In progress")))
    } else {
        dispatch(FeedAction.Add(url))
        thunkScope.launch {
            try {
                rssReader.addFeed(url)
                val feeds = rssReader.getAllFeeds(forceUpdate = false)
                dispatch(FeedAction.Data(feeds))
            } catch (e: Exception) {
                dispatch(FeedAction.Error(e))
                dispatch(FeedSideEffect.Error(e))
            }
        }
    }
}

fun deleteFeed(rssReader: RssReader, url: String): Thunk = { dispatch, getState ->
    if (getState().progress) {
        dispatch(FeedSideEffect.Error(IllegalStateException("In progress")))
    } else {
        dispatch(FeedAction.Delete(url))
        thunkScope.launch {
            try {
                rssReader.deleteFeed(url)
                val feeds = rssReader.getAllFeeds(forceUpdate = false)
                dispatch(FeedAction.Data(feeds))
            } catch (e: Exception) {
                dispatch(FeedAction.Error(e))
                dispatch(FeedSideEffect.Error(e))
            }
        }
    }
}
