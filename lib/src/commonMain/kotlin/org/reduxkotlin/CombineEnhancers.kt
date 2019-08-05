package org.reduxkotlin


fun<State> combineEnhancers(vararg enhancers: StoreEnhancer<State>): StoreEnhancer<State> =
        { storeCreator ->
            compose(enhancers.map { it })(storeCreator)
        }

