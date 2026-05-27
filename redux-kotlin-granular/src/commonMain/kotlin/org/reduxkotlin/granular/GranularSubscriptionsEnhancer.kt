package org.reduxkotlin.granular

import org.reduxkotlin.StoreEnhancer

/**
 * No-op store enhancer intended for use in `createStore(..., compose(applyMiddleware(...),
 * granularSubscriptionsEnhancer()))` chains where the author wants to
 * advertise that granular subscriptions are in use.
 *
 * The granular API itself is a set of extension functions on
 * [org.reduxkotlin.Store]; no store wrapping is needed for correctness.
 * This marker exists so future per-store optimisations (e.g. folding a
 * field registry directly into a store wrapper instead of attaching a
 * separate subscriber per [subscribeFields] block) can take advantage of
 * the enhancer hook without breaking call sites that already use it.
 */
public fun <State> granularSubscriptionsEnhancer(): StoreEnhancer<State> = { storeCreator ->
    {
            reducer, initialState, en ->
        storeCreator(reducer, initialState, en)
    }
}
