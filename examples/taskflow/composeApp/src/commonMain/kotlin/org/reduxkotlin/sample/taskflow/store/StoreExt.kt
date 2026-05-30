package org.reduxkotlin.sample.taskflow.store

import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState

/**
 * Reads the typed model [M] out of this [ModelState].
 *
 * Convenience alias for [ModelState.get]; all model slots are declared up front when the store is
 * built, so the lookup never misses.
 *
 * @return the model instance of type [M] held in this state.
 */
public inline fun <reified M : Any> ModelState.getModel(): M = get(M::class)

/**
 * Reads the typed model [M] from this store's current state.
 *
 * A non-reactive snapshot read for effects/handlers that need a model outside the routing layer.
 * Compose UI should subscribe via the compose-multimodel bindings instead of polling this.
 *
 * @return the model instance of type [M] held in the store's current [ModelState].
 */
public inline fun <reified M : Any> Store<ModelState>.getModel(): M = state.get(M::class)
