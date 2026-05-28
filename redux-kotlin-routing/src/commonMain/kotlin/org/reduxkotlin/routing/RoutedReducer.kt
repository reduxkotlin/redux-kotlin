package org.reduxkotlin.routing

import org.reduxkotlin.Reducer
import org.reduxkotlin.multimodel.ModelState
import kotlin.reflect.KClass

/**
 * A single registered handler keyed under one action class. Given the
 * working state and the dispatched action, it returns the model
 * replacements it wants to make (possibly empty). The change gate and
 * the onWrite notification are applied centrally by [routedReducer].
 */
internal class Cell(val source: String, val handler: (working: WorkingState, action: Any) -> Map<KClass<*>, Any>)

/**
 * A broadcast handler that runs once per installed model for its action
 * class. Materialized into per-model [Cell]s at reducer-build time so
 * it covers every model regardless of declaration order.
 */
internal class Broadcast(val actionClass: KClass<*>, val perModel: (model: Any, action: Any) -> Any)

/**
 * Transient per-dispatch working view over a base [ModelState]. Reads
 * return staged writes first (so later handlers see earlier writes),
 * falling back to the committed base. The staging map is allocated
 * lazily on the first write, so a dispatch that changes nothing makes
 * no map copy.
 */
internal class WorkingState(private val base: ModelState) : Reads {

    var staged: MutableMap<KClass<*>, Any>? = null
        private set

    override fun <M : Any> get(modelClass: KClass<M>): M {
        @Suppress("UNCHECKED_CAST")
        return peek(modelClass) as M
    }

    fun peek(modelClass: KClass<*>): Any {
        staged?.get(modelClass)?.let { return it }
        return base.get(modelClass)
    }

    fun stage(modelClass: KClass<*>, model: Any) {
        val current = staged ?: LinkedHashMap<KClass<*>, Any>().also { staged = it }
        current[modelClass] = model
    }
}

/**
 * Builds the routed [Reducer] over [ModelState] from a finished routing
 * table plus broadcast registrations.
 */
internal fun routedReducer(
    table: Map<Any, List<Cell>>,
    broadcasts: List<Broadcast>,
    devChecks: Boolean,
    onWrite: ((action: Any, modelClass: KClass<*>, prev: Any, next: Any, source: String) -> Unit)?,
    modelClasses: List<KClass<*>> = emptyList(),
): Reducer<ModelState> {
    // Materialize broadcasts into the table as per-model cells.
    val full: Map<Any, List<Cell>> = if (broadcasts.isEmpty()) {
        table
    } else {
        val merged = LinkedHashMap<Any, MutableList<Cell>>()
        for ((key, cells) in table) merged[key] = cells.toMutableList()
        for (broadcast in broadcasts) {
            val cells = merged.getOrPut(broadcast.actionClass) { mutableListOf() }
            for (modelClass in modelClasses) {
                cells += Cell("broadcast:${modelClass.simpleName}") { working, action ->
                    val current = working.peek(modelClass)
                    val next = broadcast.perModel(current, action)
                    if (next !== current) mapOf(modelClass to next) else emptyMap()
                }
            }
        }
        merged
    }

    return reducer@{ state, action ->
        val cells = full[action::class] ?: return@reducer state
        val working = WorkingState(state)
        for (cell in cells) {
            val writes = cell.handler(working, action)
            for ((modelClass, next) in writes) {
                val prev = working.peek(modelClass)
                if (next !== prev) {
                    check(!(devChecks && next == prev)) {
                        "Handler '${cell.source}' returned a new but structurally-equal instance for " +
                            "${modelClass.simpleName ?: modelClass}. This allocates without changing state and " +
                            "would fire subscribers spuriously. Return the same instance when nothing changed."
                    }
                    onWrite?.invoke(action, modelClass, prev, next, cell.source)
                    working.stage(modelClass, next)
                }
            }
        }
        val staged = working.staged ?: return@reducer state
        state.withAll(staged)
    }
}
