package org.reduxkotlin.granular

import kotlinx.atomicfu.atomic

/**
 * Creates a selector that runs [transform] only when [inputSelector]'s result
 * changes. Input comparison uses referential equality first, then structural
 * equality, matching granular subscription change detection.
 *
 * Hoist the returned selector out of a Composable, or create it with
 * `remember(key)`, so its cache survives recompositions. Concurrent callers
 * can compute a duplicate transform during a cache race, but never receive a
 * result for a different input.
 */
public fun <State, Input, Result> memoizedSelector(
    inputSelector: (State) -> Input,
    transform: (Input) -> Result,
): (State) -> Result {
    val cache = atomic<CachedResult<Input, Result>?>(null)
    return { state ->
        val input = inputSelector(state)
        val cached = cache.value
        if (cached != null && valuesMatch(input, cached.input)) {
            cached.result
        } else {
            transform(input).also { result ->
                cache.value = CachedResult(input, result)
            }
        }
    }
}

/**
 * Creates a selector that runs [transform] only when either declared input
 * changes. Inputs are compared independently with referential equality first,
 * then structural equality.
 */
public fun <State, First, Second, Result> memoizedSelector(
    firstInputSelector: (State) -> First,
    secondInputSelector: (State) -> Second,
    transform: (First, Second) -> Result,
): (State) -> Result {
    val cache = atomic<CachedTwoInputResult<First, Second, Result>?>(null)
    return { state ->
        val first = firstInputSelector(state)
        val second = secondInputSelector(state)
        val cached = cache.value
        if (
            cached != null &&
            valuesMatch(first, cached.first) &&
            valuesMatch(second, cached.second)
        ) {
            cached.result
        } else {
            transform(first, second).also { result ->
                cache.value = CachedTwoInputResult(first, second, result)
            }
        }
    }
}

private data class CachedResult<Input, Result>(val input: Input, val result: Result)

private data class CachedTwoInputResult<First, Second, Result>(
    val first: First,
    val second: Second,
    val result: Result,
)

internal fun valuesMatch(first: Any?, second: Any?): Boolean = first === second || first == second
