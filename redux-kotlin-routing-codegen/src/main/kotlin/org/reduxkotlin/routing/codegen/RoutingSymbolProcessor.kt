package org.reduxkotlin.routing.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/** Collects @Reduce/@ReduxInitial functions and generates a ReduxModule registrar. */
public class RoutingSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    private val reduceAnno = "org.reduxkotlin.routing.Reduce"
    private val initialAnno = "org.reduxkotlin.routing.ReduxInitial"

    @Suppress("ReturnCount")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val reduceFns = resolver.getSymbolsWithAnnotation(reduceAnno)
            .filterIsInstance<KSFunctionDeclaration>().toList()
        val initialFns = resolver.getSymbolsWithAnnotation(initialAnno)
            .filterIsInstance<KSFunctionDeclaration>().toList()
        if (reduceFns.isEmpty() && initialFns.isEmpty()) return emptyList()

        val moduleName = options["routing.moduleName"]
        if (moduleName == null) {
            logger.error(
                "redux-kotlin-routing-codegen: missing KSP arg 'routing.moduleName'. " +
                    "Add ksp { arg(\"routing.moduleName\", \"YourFeature\") } to this module's build.gradle.kts.",
            )
            return emptyList()
        }

        val handlers = reduceFns.mapNotNull { validateReduce(it, logger) }
        val initials = mutableMapOf<String, InitialInfo>()
        for (fn in initialFns) {
            val info = validateInitial(fn, logger) ?: continue
            if (initials.put(info.modelFqn, info) != null) {
                logger.error("Duplicate @ReduxInitial for model ${info.modelFqn}.", fn)
            }
        }
        val byModel = handlers.groupBy { it.modelFqn }
        for ((modelFqn, _) in byModel) {
            if (modelFqn !in initials) {
                logger.error(
                    "No @ReduxInitial found in this module for model $modelFqn. " +
                        "Add a top-level @ReduxInitial provider for it in this module, " +
                        "or register its handlers with the hand-written DSL (cross-module model sharing is not supported in v1).",
                )
            }
        }
        // Generation added in Task 4.
        return emptyList()
    }
}
