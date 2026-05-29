package org.reduxkotlin.routing.codegen

import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

internal data class HandlerInfo(
    val modelFqn: String,
    val modelDecl: KSClassDeclaration,
    val actionDecl: KSClassDeclaration,
    val actionFqn: String,
    val handlerPackage: String,
    val handlerSimpleName: String,
    val handlerFqn: String,
)

internal data class InitialInfo(val modelFqn: String, val providerPackage: String, val providerSimpleName: String)

@Suppress("ReturnCount")
private fun KSType.isUsableConcreteClass(): Boolean {
    if (isMarkedNullable) return false
    if (arguments.isNotEmpty()) return false
    val decl = declaration as? KSClassDeclaration ?: return false
    if (decl.qualifiedName == null) return false
    if (Modifier.INNER in decl.modifiers) return false
    if (decl.classKind == ClassKind.ENUM_ENTRY) return false
    return decl.isPublic() || decl.isInternal()
}

@Suppress("ReturnCount")
internal fun validateReduce(fn: KSFunctionDeclaration, logger: KSPLogger): HandlerInfo? {
    if (fn.functionKind != FunctionKind.TOP_LEVEL) {
        logger.error("@Reduce must be a top-level function.", fn)
        return null
    }
    if (fn.parameters.size != 2) {
        logger.error("@Reduce must take exactly (model, action) parameters.", fn)
        return null
    }
    val modelType = fn.parameters[0].type.resolve()
    val actionType = fn.parameters[1].type.resolve()
    val returnType = fn.returnType?.resolve()
    if (returnType == null || returnType.declaration !== modelType.declaration) {
        logger.error("@Reduce return type must equal the model (first parameter) type.", fn)
        return null
    }
    // isUsableConcreteClass rejects generic/nullable/inner/non-visible/qualifiedName-null;
    // its message contains "non-generic" so the generic-action test's "generic" assertion matches.
    if (!modelType.isUsableConcreteClass()) {
        logger.error("@Reduce model type must be a non-generic, non-null, public/internal class.", fn)
        return null
    }
    if (!actionType.isUsableConcreteClass()) {
        logger.error("@Reduce action type must be a non-generic, non-null, public/internal class.", fn)
        return null
    }
    val modelDecl = modelType.declaration as KSClassDeclaration
    val actionDecl = actionType.declaration as KSClassDeclaration
    val pkg = fn.packageName.asString()
    val simple = fn.simpleName.asString()
    return HandlerInfo(
        modelFqn = modelDecl.qualifiedName!!.asString(),
        modelDecl = modelDecl,
        actionDecl = actionDecl,
        actionFqn = actionDecl.qualifiedName!!.asString(),
        handlerPackage = pkg,
        handlerSimpleName = simple,
        handlerFqn = if (pkg.isEmpty()) simple else "$pkg.$simple",
    )
}

@Suppress("ReturnCount")
internal fun validateInitial(fn: KSFunctionDeclaration, logger: KSPLogger): InitialInfo? {
    if (fn.functionKind != FunctionKind.TOP_LEVEL) {
        logger.error("@ReduxInitial must be a top-level function.", fn)
        return null
    }
    if (fn.parameters.isNotEmpty()) {
        logger.error("@ReduxInitial must take no parameters.", fn)
        return null
    }
    val returnType = fn.returnType?.resolve()
    if (returnType == null || !returnType.isUsableConcreteClass()) {
        logger.error("@ReduxInitial must return a non-generic, non-null, public/internal model class.", fn)
        return null
    }
    val modelDecl = returnType.declaration as KSClassDeclaration
    return InitialInfo(
        modelFqn = modelDecl.qualifiedName!!.asString(),
        providerPackage = fn.packageName.asString(),
        providerSimpleName = fn.simpleName.asString(),
    )
}
