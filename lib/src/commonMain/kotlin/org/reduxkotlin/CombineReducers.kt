package org.reduxkotlin

fun combineReducers(vararg reducers: Reducer): Reducer =
    { state, action ->
        reducers.fold(state, { s, reducer -> reducer(s, action) })
    }

/**
 * combine two reducer with + operator
 */
operator fun Reducer.plus(other: Reducer): Reducer = { s, a ->
    other(this(s, a), a)
}