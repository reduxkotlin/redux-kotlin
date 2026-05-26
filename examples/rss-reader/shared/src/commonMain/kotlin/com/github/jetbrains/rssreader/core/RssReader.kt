package com.github.jetbrains.rssreader.core

import com.github.jetbrains.rssreader.Settings
import com.github.jetbrains.rssreader.datasource.network.FeedLoader
import com.github.jetbrains.rssreader.datasource.storage.FeedStorage
import com.github.jetbrains.rssreader.domain.RssFeed
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class RssReader(
    private val feedLoader: FeedLoader,
    private val feedStorage: FeedStorage,
    private val settings: Settings = Settings(setOf("https://blog.jetbrains.com/kotlin/feed/"))
) {

    @Throws(Exception::class)
    suspend fun getAllFeeds(
        forceUpdate: Boolean = false
    ): List<RssFeed> {
        var feeds = feedStorage.getAllFeeds()

        if (forceUpdate || feeds.isEmpty()) {
            val feedsUrls =
                if (feeds.isEmpty()) settings.defaultFeedUrls else feeds.map { it.sourceUrl }.filterNotNull()
            feeds = feedsUrls.mapAsync { url ->
                val new = feedLoader.getFeed(url, settings.isDefault(url))
                feedStorage.saveFeed(new)
                new
            }
        }

        return feeds
    }

    @Throws(Exception::class)
    suspend fun addFeed(url: String) {
        val feed = feedLoader.getFeed(url, settings.isDefault(url))
        feedStorage.saveFeed(feed)
    }

    @Throws(Exception::class)
    suspend fun deleteFeed(url: String) {
        feedStorage.deleteFeed(url)
    }

    private suspend fun <A, B> Iterable<A>.mapAsync(f: suspend (A) -> B): List<B> =
        coroutineScope { map { async { f(it) } }.awaitAll() }
}