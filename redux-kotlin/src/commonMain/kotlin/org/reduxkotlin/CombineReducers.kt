package org.reduxkotlin

/**
 * Combines multiple reducers into single reducer chain
 */
public fun <State> combineReducers(vararg reducers: Reducer<State>): Reducer<State> = { state, action ->
  reducers.fold(state) { s, reducer -> reducer(s, action) }
}

/**
 * Combines two reducers with the + operator.
 */
public operator fun <State> Reducer<State>.plus(other: Reducer<State>): Reducer<State> = { s, a ->
  other(this(s, a), a)
}
