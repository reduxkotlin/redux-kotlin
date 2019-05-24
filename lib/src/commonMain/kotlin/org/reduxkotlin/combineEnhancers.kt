package org.reduxkotlin


fun <S> combineEnhancers(vararg enhancers: StoreEnhancer<S>): StoreEnhancer<S> =
        { storeCreator ->
            compose(enhancers.map { it })(storeCreator)
        }

