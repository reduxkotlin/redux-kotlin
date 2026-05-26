package com.github.jetbrains.rssreader.app

sealed class FeedSideEffect : Effect {
    data class Error(val error: Exception) : FeedSideEffect()
}
