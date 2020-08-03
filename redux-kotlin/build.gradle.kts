plugins {
    java
    kotlin("multiplatform")
}

repositories {
    maven("https://dl.bintray.com/spekframework/spek-dev")
}


kotlin {
    androidNativeArm32()
    androidNativeArm64()
    iosArm32()
    iosArm64()
    iosX64()
    js(BOTH) {
        browser()
        nodejs()

        listOf(compilations["main"], compilations["test"]).forEach {
            with(it.kotlinOptions) {
                moduleKind = "umd"
                sourceMap = true
                sourceMapEmbedSources = "always"
                metaInfo = true
            }
        }
    }
    jvm()
    linuxArm32Hfp()
    linuxArm64()
    linuxMips32()
    linuxMipsel32()
    linuxX64()
    macosX64()
    mingwX64()
    mingwX86()
    tvosArm64()
    tvosX64()
    wasm32()
    watchosArm32()
    watchosArm64()
    watchosX86()

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(Libs.spek_dsl_metadata)
                implementation(Libs.atrium_cc_en_gb_robstoll_common)
                implementation(Libs.mockk_common)
            }
        }

        val fallback: (org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.() -> Unit) = {
            kotlin.srcDir("src/fallbackMain")
        }
        val ios: (org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.() -> Unit) = {
            kotlin.srcDir("src/iosMain")
        }
        val androidNativeArm32Main by getting(fallback)
        val androidNativeArm64Main by getting(fallback)
        val iosArm32Main by getting(ios)
        val iosArm32Test by getting(ios)
        val iosArm64Main by getting(ios)
        val iosArm64Test by getting(ios)
        val tvosArm64Main by getting(ios)
        val tvosX64Main by getting(ios)
        val watchosArm32Main by getting(ios)
        val watchosArm64Main by getting(ios)
        val watchosX86Main by getting(ios)
        val iosX64Main by getting { dependsOn(iosArm32Main) }
        val iosX64Test by getting { dependsOn(iosArm32Test) }
        val linuxArm32HfpMain by getting(fallback)
        val linuxArm64Main by getting(fallback)
        val linuxMips32Main by getting(fallback)
        val linuxMipsel32Main by getting(fallback)
        val linuxX64Main by getting(fallback)
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation(Libs.kotlinx_coroutines_test)
                implementation(Libs.kotlinx_coroutines_core_jvm)
                implementation(Libs.spek_dsl_jvm)
                implementation(Libs.atrium_cc_en_gb_robstoll)
                implementation(Libs.mockk)

                runtimeOnly(Libs.spek_runner_junit5)
                runtimeOnly(Libs.kotlin_reflect)
            }
        }
        val mingwX64Main by getting(fallback)
        val mingwX86Main by getting(fallback)
        val wasm32Main by getting(fallback)
    }
}

afterEvaluate {
    tasks {
        val jvmTest by getting(Test::class) {
            useJUnitPlatform {
                includeEngines("spek2")
            }
        }

        // Alias the task names we use elsewhere to the new task names.
        create("installMP").dependsOn("publishKotlinMultiplatformPublicationToMavenLocal")
        create("installLocally") {
            dependsOn("publishKotlinMultiplatformPublicationToTestRepository")
            dependsOn("publishJvmPublicationToTestRepository")
            dependsOn("publishJsPublicationToTestRepository")
            dependsOn("publishMetadataPublicationToTestRepository")
        }
        create("installIosLocally") {
            dependsOn("publishKotlinMultiplatformPublicationToTestRepository")
            dependsOn("publishIosArm32PublicationToTestRepository")
            dependsOn("publishIosArm64PublicationToTestRepository")
            dependsOn("publishIosX64PublicationToTestRepository")
            dependsOn("publishMetadataPublicationToTestRepository")
        }
        // NOTE: We do not alias uploadArchives because CI runs it on Linux and we only want to run it on Mac OS.
        //tasks.create("uploadArchives").dependsOn("publishKotlinMultiplatformPublicationToMavenRepository")
    }
}

apply(from = rootProject.file("gradle/publish.gradle"))
