package org.reduxkotlin.routing

/**
 * Marks a top-level single-model reducer handler of shape `(M, A) -> M`,
 * to be collected by the redux-kotlin-routing-codegen KSP processor and
 * emitted as an `on<A>` registration for model `M`. Matching is by the
 * action's exact leaf class.
 *
 * The annotated function must be top-level, take exactly two parameters
 * (the model, then the action), return the model type, and use
 * non-generic, non-nullable, public/internal model and action types.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Reduce

/**
 * Marks a top-level zero-argument provider of a model's initial
 * instance (`() -> M`). Exactly one `@ReduxInitial` per model type must
 * exist in the same module as that model's [Reduce] handlers; the
 * generated registrar calls it to seed the model via `model(provider())`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class ReduxInitial
