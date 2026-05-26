package com.github.jetbrains.rssreader.app

sealed class FeedSideEffect {
    data class Error(val error: Exception) : FeedSideEffect()
}
