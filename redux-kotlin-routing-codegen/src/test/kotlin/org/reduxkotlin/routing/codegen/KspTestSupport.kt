package org.reduxkotlin.routing.codegen

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp

/** Compiles [sources] with the routing processor under KSP2 and returns the result. */
fun compileWithProcessor(moduleName: String?, vararg sources: SourceFile): JvmCompilationResult {
    val compilation = KotlinCompilation().apply {
        this.sources = sources.toList()
        inheritClassPath = true
        // Routing runtime is compiled for JVM 17 with inline funs (model/on); the in-memory
        // compilation must target 17 too, else "Cannot inline bytecode built with JVM target 17".
        jvmTarget = "17"
        // kctfork 0.12.1: configureKsp{} takes NO useKsp2 arg (KSP2 invoked internally;
        // 0.10+ is KSP2-only). Receiver is KspTool with symbolProcessorProviders + processorOptions.
        configureKsp {
            symbolProcessorProviders.add(RoutingSymbolProcessorProvider())
            if (moduleName != null) processorOptions["routing.moduleName"] = moduleName
        }
    }
    return compilation.compile()
}

/** Reads the single generated registrar file's text, or null. (workingDir/ksp/sources/kotlin) */
fun JvmCompilationResult.generatedRegistrar(name: String): String? =
    outputDirectory.parentFile.resolve("ksp/sources/kotlin")
        .walkTopDown().firstOrNull { it.name == "$name.kt" }?.readText()
