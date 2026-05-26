package com.github.jetbrains.rssreader.datasource.network

import com.github.jetbrains.rssreader.domain.RssFeed
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class FeedLoader(
    private val httpClient: HttpClient
) {
    suspend fun getFeed(url: String, isDefault: Boolean): RssFeed {
        val feed = httpClient.get(url = Url(url)).body<RssFeed>()
        feed.isDefault = isDefault
        feed.sourceUrl = url
        return feed
    }
}