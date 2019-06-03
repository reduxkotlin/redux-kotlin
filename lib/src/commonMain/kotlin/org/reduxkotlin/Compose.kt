package org.reduxkotlin

fun <T> compose(functions: List<(T) -> T>): (T) -> T =
        { x -> functions.foldRight(x, { f, composed -> f(composed) })
}
