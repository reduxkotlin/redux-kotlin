package org.reduxkotlin

fun <S> combineReducers(vararg reducers: Reducer<S>): Reducer<S> =
        { state, action ->
            reducers.fold(state, { s, reducer -> reducer(s, action) })
        }

fun <S> Reducer<S>.combinedWith(vararg reducers: Reducer<S>): Reducer<S> {
    return { state, action ->
        val sAfterFirstReducer = this(state, action)
        reducers.fold(sAfterFirstReducer, { s, reducer -> reducer(s, action) })
    }
}

/**
 * combine two reducer with + operator
 */
operator fun <S> Reducer<S>.plus(other: Reducer<S>): Reducer<S> = { s, a ->
    other(this(s, a), a)
}