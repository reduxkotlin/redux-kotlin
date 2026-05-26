package com.github.jetbrains.rssreader.app

import com.github.jetbrains.rssreader.domain.RssFeed

sealed class FeedAction : Action {
    data class Refresh(val forceLoad: Boolean) : FeedAction()
    data class Add(val url: String) : FeedAction()
    data class Delete(val url: String) : FeedAction()
    data class SelectFeed(val feed: RssFeed?) : FeedAction()
    data class Data(val feeds: List<RssFeed>) : FeedAction()
    data class Error(val error: Exception) : FeedAction()
}
