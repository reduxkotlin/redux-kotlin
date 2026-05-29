import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("convention.common")
    kotlin("jvm")
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    target.compilations.getByName("test").compileTaskProvider.configure {
        compilerOptions.optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    testImplementation(kotlin("test"))
    testImplementation(libs.kctfork.ksp)
    // Provides @Reduce/@ReduxInitial on the in-memory compilation classpath (inheritClassPath).
    testImplementation(project(":redux-kotlin-routing"))
}
