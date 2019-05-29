package org.reduxkotlin

fun <S> combineReducers(vararg reducers: Reducer): Reducer =
        { state, action ->
            reducers.fold(state, { s, reducer -> reducer(s, action) })
        }

fun <S> Reducer.combinedWith(vararg reducers: Reducer): Reducer {
    return { state, action ->
        val sAfterFirstReducer = this(state, action)
        reducers.fold(sAfterFirstReducer, { s, reducer -> reducer(s, action) })
    }
}

/**
 * combine two reducer with + operator
 */
operator fun <S> Reducer.plus(other: Reducer): Reducer = { s, a ->
    other(this(s, a), a)
}