package org.reduxkotlin.examples.counter

import org.reduxkotlin.Reducer

class Increment
class Decrement

/**
 * This is a reducer, a pure function with (state, action) -> state signature.
 * It describes how an action transforms the state into the next state.
 *
 * The shape of the state is up to you: it can be a primitive, an array, an object,
 * Usually this will be a data class.  The copy method is useful for creating the new state.
 * In this contrived example, we are just using an Int for the state.
 *
 * In this example, we use a `when` statement and type checking, but other methods are possible,
 * such as a 'type' string field, or delegating the reduction to a method on the action objects.
 */
val reducer: Reducer<Int> = { state, action ->
  when (action) {
    is Increment -> state + 1
    is Decrement -> state - 1
    else -> state
  }
}
