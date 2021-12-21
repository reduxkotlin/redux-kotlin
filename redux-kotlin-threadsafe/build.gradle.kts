plugins {
  kotlin("multiplatform")
  id("plugin.atomicfu")
  id("plugin.publishing")
}

kotlin {
//    androidNativeArm32()
//    androidNativeArm64()
//    iosArm32()
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
  linuxX64()
  macosX64()
  mingwX64()
//    mingwX86()
  tvosArm64()
  tvosX64()
  watchosArm32()
  watchosArm64()
  watchosX86()

//    below are currently not supported by atomicfu
//    wasm32("wasm")
//    linuxArm32Hfp("linArm32")
//    linuxMips32("linMips32")
//    linuxMipsel32("linMipsel32")
//    linuxArm64()

  sourceSets {
    commonMain {
      dependencies {
        api(project(":redux-kotlin"))
      }
    }
    commonTest {
      dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
        implementation("io.mockk:mockk-common:_")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:_")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:_")
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-junit"))
        implementation("io.mockk:mockk:_")

        runtimeOnly(kotlin("reflect"))
      }
    }
    val jsTest by getting {
      dependencies {
        implementation(kotlin("test-js"))
        implementation(kotlin("stdlib-js"))
      }
    }
  }
}

afterEvaluate {
  tasks {
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
    // tasks.create("uploadArchives").dependsOn("publishKotlinMultiplatformPublicationToMavenRepository")
  }
}
