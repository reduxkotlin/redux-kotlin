package com.github.jetbrains.rssreader.app

import com.github.jetbrains.rssreader.domain.Channel
import com.github.jetbrains.rssreader.domain.RssFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedReducerTest {

    private val initial = FeedState(progress = false, feeds = emptyList())
    private val feedA = RssFeed(
        version = null,
        sourceUrl = "https://a/feed",
        isDefault = false,
        channel = Channel(title = null, description = null, link = null, item = emptyList()),
    )
    private val feedB = RssFeed(
        version = null,
        sourceUrl = "https://b/feed",
        isDefault = false,
        channel = Channel(title = null, description = null, link = null, item = emptyList()),
    )

    @Test
    fun refreshWhileIdleFlipsProgressTrueAndKeepsFeeds() {
        val next = feedReducer(initial.copy(feeds = listOf(feedA)), FeedAction.Refresh(forceLoad = false))
        assertTrue(next.progress)
        assertEquals(listOf(feedA), next.feeds)
    }

    @Test
    fun refreshWhileInProgressIsNoop() {
        val state = FeedState(progress = true, feeds = listOf(feedA))
        val next = feedReducer(state, FeedAction.Refresh(forceLoad = false))
        assertEquals(state, next)
    }

    @Test
    fun dataClearsProgressAndReplacesFeedsPreservingSelectionIfStillPresent() {
        val state = FeedState(progress = true, feeds = listOf(feedA), selectedFeed = feedA)
        val next = feedReducer(state, FeedAction.Data(listOf(feedA, feedB)))
        assertEquals(false, next.progress)
        assertEquals(listOf(feedA, feedB), next.feeds)
        assertEquals(feedA, next.selectedFeed)
    }

    @Test
    fun dataDropsSelectionIfPreviouslySelectedFeedDisappeared() {
        val state = FeedState(progress = true, feeds = listOf(feedA), selectedFeed = feedA)
        val next = feedReducer(state, FeedAction.Data(listOf(feedB)))
        assertNull(next.selectedFeed)
    }

    @Test
    fun errorWhileInProgressClearsProgressWhileIdleIsNoop() {
        val inProgress = FeedState(progress = true, feeds = listOf(feedA))
        val nextInProgress = feedReducer(inProgress, FeedAction.Error(RuntimeException("x")))
        assertEquals(false, nextInProgress.progress)
        assertEquals(listOf(feedA), nextInProgress.feeds)

        val idle = FeedState(progress = false, feeds = listOf(feedA))
        val nextIdle = feedReducer(idle, FeedAction.Error(RuntimeException("x")))
        assertEquals(idle, nextIdle)
    }

    @Test
    fun selectFeedUpdatesSelectedFeedWhenFeedExists() {
        val state = FeedState(progress = false, feeds = listOf(feedA, feedB))
        val next = feedReducer(state, FeedAction.SelectFeed(feedB))
        assertEquals(feedB, next.selectedFeed)
    }

    @Test
    fun selectFeedNullClearsSelection() {
        val state = FeedState(progress = false, feeds = listOf(feedA), selectedFeed = feedA)
        val next = feedReducer(state, FeedAction.SelectFeed(null))
        assertNull(next.selectedFeed)
    }
}
