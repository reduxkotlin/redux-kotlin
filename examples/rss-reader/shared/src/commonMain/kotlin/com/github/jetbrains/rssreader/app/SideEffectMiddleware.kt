package com.github.jetbrains.rssreader.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.reduxkotlin.Middleware

/**
 * Side-effect bus. The store dispatches a [FeedSideEffect] like any other action; this
 * middleware intercepts it, emits to [effects], and does NOT pass it to the reducer
 * (returns the action directly without calling `next`).
 *
 * Replay = 0 means a missing subscriber drops the effect — fine for transient UI snackbars.
 * extraBufferCapacity = 16 prevents `tryEmit` failures under bursts.
 */
class SideEffectBus {
    private val _effects = MutableSharedFlow<FeedSideEffect>(replay = 0, extraBufferCapacity = 16)
    val effects: SharedFlow<FeedSideEffect> = _effects.asSharedFlow()

    fun middleware(): Middleware<FeedState> = { _ ->
        { next ->
            { action ->
                if (action is FeedSideEffect) {
                    _effects.tryEmit(action)
                    action
                } else {
                    next(action)
                }
            }
        }
    }
}
