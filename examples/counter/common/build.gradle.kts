plugins {
  java
  kotlin("multiplatform")
}

repositories {
  maven("https://dl.bintray.com/spekframework/spek-dev")
}

kotlin {
  jvm()
  js(IR) {
    binaries.executable()

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
    commonMain {
      dependencies {
        implementation(project(":redux-kotlin"))
      }
    }
    commonTest {
      kotlin.srcDir("src/test/kotlin")
      dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
        implementation("org.spekframework.spek2:spek-dsl-metadata:${Versions.spek}")
        implementation("ch.tutteli.atrium:atrium-cc-en_GB-robstoll-common:${Versions.atrium}")
        implementation("io.mockk:mockk-common:${Versions.mockk}")
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-junit"))
        implementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spek}")
        implementation("ch.tutteli.atrium:atrium-cc-en_GB-robstoll:${Versions.atrium}")
        implementation("io.mockk:mockk:${Versions.mockk}")

        runtimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spek}")
        runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")
      }
    }
    val jsTest by getting {
      dependencies {
        implementation(kotlin("test-js"))
        implementation(kotlin("stdlib-js"))
      }
    }

    val iosMain by getting
    val iosTest by getting
    val iosSimMain by getting { dependsOn(iosMain) }
    val iosSimTest by getting { dependsOn(iosTest) }
  }
}

tasks {
  val jvmTest by getting(Test::class) {
    useJUnitPlatform {
      includeEngines("spek2")
    }
  }
}
