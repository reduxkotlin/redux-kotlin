package org.reduxkotlin.granular

import org.reduxkotlin.StoreEnhancer

/**
 * No-op store enhancer intended for use in `createStore(..., compose(applyMiddleware(...),
 * granularSubscriptionsEnhancer()))` chains where the author wants to
 * advertise that granular subscriptions are in use.
 *
 * The granular API itself is a set of extension functions on
 * [org.reduxkotlin.Store]; no store wrapping is needed for correctness.
 * This marker intentionally remains a no-op. Shared selector fan-out is
 * provided by [selectorSubscriptions], which subscribes to the final store
 * instance and therefore cannot be bypassed by the ordering of concurrent,
 * routing, or bundle wrappers in an enhancer chain.
 */
public fun <State> granularSubscriptionsEnhancer(): StoreEnhancer<State> = { storeCreator ->
    { reducer, initialState, en ->
        storeCreator(reducer, initialState, en)
    }
}
