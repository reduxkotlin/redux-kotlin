package org.reduxkotlin


fun combineEnhancers(vararg enhancers: StoreEnhancer): StoreEnhancer =
        { storeCreator ->
            compose(enhancers.map { it })(storeCreator)
        }

