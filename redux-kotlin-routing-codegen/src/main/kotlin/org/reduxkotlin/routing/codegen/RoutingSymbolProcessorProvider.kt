package org.reduxkotlin.routing.codegen

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/** KSP entry point that creates the redux-kotlin routing processor. */
public class RoutingSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RoutingSymbolProcessor(environment.codeGenerator, environment.logger, environment.options)
}
