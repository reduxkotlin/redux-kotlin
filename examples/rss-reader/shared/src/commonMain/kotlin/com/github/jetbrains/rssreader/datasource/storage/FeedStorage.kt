package com.github.jetbrains.rssreader.datasource.storage

import com.github.jetbrains.rssreader.domain.RssFeed
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class FeedStorage(
    private val settings: Settings,
    private val json: Json
) {
    private companion object {
        private const val KEY_FEED_CACHE = "key_feed_cache"
    }

    private var diskCache: Map<String, RssFeed>
        get() {
            return settings.getStringOrNull(KEY_FEED_CACHE)?.let { str ->
                json.decodeFromString(ListSerializer(RssFeed.serializer()), str)
                    .associate { it.sourceUrl to it }
            } ?: mutableMapOf()
        }
        set(value) {
            val list = value.map { it.value }
            settings[KEY_FEED_CACHE] =
                json.encodeToString(ListSerializer(RssFeed.serializer()), list)
        }

    private val memCache: MutableMap<String, RssFeed> by lazy { diskCache.toMutableMap() }

    suspend fun getFeed(url: String): RssFeed? = memCache[url]

    suspend fun saveFeed(feed: RssFeed) {
        memCache[feed.sourceUrl] = feed
        diskCache = memCache
    }

    suspend fun deleteFeed(url: String) {
        memCache.remove(url)
        diskCache = memCache
    }

    suspend fun getAllFeeds(): List<RssFeed> = memCache.values.toList()
}