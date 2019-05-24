package org.reduxkotlin


fun <P1, P2, P3, R> Function3<P1, P2, P3, R>.curried(): (P1) -> (P2) -> (P3) -> R {
    return { p1: P1 -> { p2: P2 -> { p3: P3 -> this(p1, p2, p3) } } }
}

fun <P1, P2, R> Function2<P1, P2, R>.curried(): (P1) -> (P2) -> R {
    return { p1: P1 -> { p2: P2 -> this(p1, p2) } }
}

fun <T> compose(functions: List<(T) -> T>): (T) -> T =
        { x -> functions.foldRight(x, { f, composed -> f(composed) })
}

fun <T> compose(vararg functions: (T) -> T): (T) -> T =
        if (functions.size == 1) {
            functions[0]
        } else {
            functions.reduce { a, b -> { a(b(it)) } }
        }
