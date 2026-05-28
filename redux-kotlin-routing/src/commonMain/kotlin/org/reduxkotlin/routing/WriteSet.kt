package org.reduxkotlin.routing

import kotlin.reflect.KClass

/**
 * A read-only view of model state passed to multi-model handlers
 * during a dispatch. Reads reflect writes already produced by earlier
 * handlers in the same dispatch (fold semantics), so a later handler
 * observes prior handlers' results.
 */
public interface Reads {
    /**
     * Returns the current working instance of [modelClass] for this
     * dispatch.
     *
     * @throws IllegalStateException if [modelClass] was not registered
     *   in the createModelStore block.
     */
    public fun <M : Any> get(modelClass: KClass<M>): M
}

/**
 * Reified convenience for [Reads.get].
 */
public inline fun <reified M : Any> Reads.get(): M = get(M::class)

/**
 * The set of model replacements returned by a multi-model handler
 * (onAction). Each entry replaces one model slot; a model not named
 * here is left untouched. Build instances with [writeSet].
 */
public class WriteSet @PublishedApi internal constructor(
    /** The replacements, keyed by concrete model class. */
    @PublishedApi internal val changes: Map<KClass<*>, Any>,
)

/**
 * Builder for [WriteSet]; the receiver of the [writeSet] lambda.
 */
public class WriteSetBuilder @PublishedApi internal constructor() {

    @PublishedApi
    internal val changes: MutableMap<KClass<*>, Any> = LinkedHashMap()

    /**
     * Stages [model] as the new instance for model type [M]. A later
     * [set] for the same type overrides an earlier one.
     */
    public inline fun <reified M : Any> set(model: M): Unit = set(M::class, model)

    /**
     * Non-reified overload of [set] for callers holding a [KClass].
     */
    public fun <M : Any> set(modelClass: KClass<M>, model: M) {
        changes[modelClass] = model
    }
}

/**
 * Builds a [WriteSet] from a sequence of [WriteSetBuilder.set] calls.
 */
public fun writeSet(block: WriteSetBuilder.() -> Unit): WriteSet {
    val builder = WriteSetBuilder()
    builder.block()
    return WriteSet(builder.changes.toMap())
}
