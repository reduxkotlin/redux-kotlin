package org.reduxkotlin.threadsafe

import org.reduxkotlin.StoreEnhancer

/**
 * Creates a store enhancer that wraps a Redux store in a synchronization object,
 * causing access to store methods to be synchronized.
 *
 * See `SynchronizedStore` for implementation of synchronization.
 *
 * This enhancer should be placed after all other enhancers that involve access to store methods in
 * the composition chain, as this will result in those enhancers receiving the synchronized store object.

 * @returns {StoreEnhancer} A store enhancer that synchronizes the store.
 */
public fun <State> createThreadSafeStoreEnhancer(): StoreEnhancer<State> {
    return { storeCreator ->
        { reducer, initialState, en: Any? ->
            val store = storeCreator(reducer, initialState, en)
            val synchronizedStore = ThreadSafeStore(store)
            synchronizedStore
        }
    }
}

@Deprecated(
    "Renamed to createThreadSafeStoreEnhancer",
    replaceWith = ReplaceWith(
        expression = "createThreadSafeStoreEnhancer",
        "org.reduxkotlin.threadsafe.createThreadSafeStoreEnhancer"
    )
)
public fun <State> createSynchronizedStoreEnhancer(): StoreEnhancer<State> =
    createThreadSafeStoreEnhancer()
