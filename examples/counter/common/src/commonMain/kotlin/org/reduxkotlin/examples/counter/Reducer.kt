package org.reduxkotlin.examples.counter

import org.reduxkotlin.Reducer


class Increment
class Decrement

val reducer: Reducer<Int> = {state, action ->
    when (action) {
        is Increment -> state + 1
        is Decrement -> state - 1
        else -> state
    }
}