package com.github.jetbrains.rssreader.app

import com.github.jetbrains.rssreader.domain.RssFeed

data class FeedState(
    val progress: Boolean,
    val feeds: List<RssFeed>,
    val selectedFeed: RssFeed? = null // null means selected all
) : State

fun FeedState.mainFeedPosts() =
    (selectedFeed?.channel?.item ?: feeds.flatMap { it.channel?.item ?: emptyList() })
        .sortedByDescending { it.pubDate }
