package org.reduxkotlin

/**
 * Composes a list of single argument functions from right to left.
 */
fun <T> compose(functions: List<(T) -> T>): (T) -> T =
        { x -> functions.foldRight(x, { f, composed -> f(composed) })
}
