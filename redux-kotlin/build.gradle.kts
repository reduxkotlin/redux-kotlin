plugins {
  java
  kotlin("multiplatform")
}

repositories {
  maven("https://dl.bintray.com/spekframework/spek-dev")
}


kotlin {
  jvm()
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

  iosArm64("ios")
  iosX64("iosSim")
  macosX64("macos")
  mingwX64("win")
  wasm32("wasm")
  linuxArm32Hfp("linArm32")
  linuxMips32("linMips32")
  linuxMipsel32("linMipsel32")
  linuxX64("lin64")

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
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-junit"))
        implementation(Libs.kotlin_coroutines_test)
        implementation(Libs.kotlin_coroutines_jvm)
        implementation(Libs.spek_dsl_jvm)
        implementation(Libs.atrium_cc_en_gb_robstoll)
        implementation(Libs.mockk)

        runtimeOnly(Libs.spek_runner_junit5)
        runtimeOnly(Libs.kotlin_reflect)
      }
    }
    val jsTest by getting {
      dependencies {
        implementation(kotlin("test-js"))
      }
    }
    val winMain by getting {
      kotlin.srcDir("src/fallbackMain")
    }
    val wasmMain by getting {
      kotlin.srcDir("src/fallbackMain")
    }
    val linArm32Main by getting {
      kotlin.srcDir("src/fallbackMain")
    }
    val linMips32Main by getting {
      kotlin.srcDir("src/fallbackMain")
    }
    val linMipsel32Main by getting {
      kotlin.srcDir("src/fallbackMain")
    }
    val lin64Main by getting {
      kotlin.srcDir("src/fallbackMain")
    }

    val iosMain by getting
    val iosTest by getting
    val iosSimMain by getting { dependsOn(iosMain) }
    val iosSimTest by getting { dependsOn(iosTest) }
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
