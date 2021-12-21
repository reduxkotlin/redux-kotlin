package org.reduxkotlin

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
fun <State> createSynchronizedStoreEnhancer(): StoreEnhancer<State> {
  return { storeCreator ->
    { reducer, initialState, en: Any? ->
      val store = storeCreator(reducer, initialState, en)
      val synchronizedStore = SynchronizedStore(store)
      synchronizedStore
    }
  }
}
