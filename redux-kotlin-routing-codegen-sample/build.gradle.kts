plugins {
    id("convention.library-mpp-loved")
    id("com.google.devtools.ksp")
}

dependencies {
    add("kspCommonMainMetadata", project(":redux-kotlin-routing-codegen"))
}

ksp {
    arg("routing.moduleName", "SampleModule")
    arg("routing.generatedPackage", "org.reduxkotlin.routing.sample.generated")
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                api(project(":redux-kotlin-routing"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
