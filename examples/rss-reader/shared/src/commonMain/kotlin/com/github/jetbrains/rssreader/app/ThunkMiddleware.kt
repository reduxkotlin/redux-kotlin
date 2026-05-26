package com.github.jetbrains.rssreader.app

import org.reduxkotlin.Middleware

/**
 * Recognizes a [Thunk] dispatched in place of an action: invokes it with the store's
 * dispatch + getState, returns its result, and does NOT pass the thunk on to the reducer.
 * Anything that isn't a Thunk passes through unchanged.
 *
 * Hand-rolled equivalent of the `redux-kotlin-thunk` middleware, inlined here to keep
 * the sample dep-free.
 *
 * Note: because [Thunk] is a typealias for a function type, `action is Thunk` at runtime
 * matches any 2-arg `Function2`. The only things dispatched in this app are FeedAction
 * subclasses, FeedSideEffect, and Thunks, so false positives don't happen in practice.
 */
fun thunkMiddleware(): Middleware<FeedState> = { store ->
    { next ->
        { action ->
            // function-type generic args are erased at runtime; cast is structurally safe
            // because the only Function2 we ever dispatch is a Thunk (see KDoc above).
            @Suppress("UNCHECKED_CAST")
            val asThunk = action as? Thunk
            if (asThunk != null) asThunk(store.dispatch, store.getState)
            else next(action)
        }
    }
}
