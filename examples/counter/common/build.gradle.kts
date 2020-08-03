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
    browser()
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

  iosArm64()
  iosX64()

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
        implementation("org.spekframework.spek2:spek-dsl-metadata:${Versions.org_spekframework_spek2}")
        implementation("ch.tutteli.atrium:atrium-cc-en_GB-robstoll-common:${Versions.org_spekframework_spek2}")
        implementation("io.mockk:mockk-common:${Versions.io_mockk}")
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-junit"))
        implementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.org_spekframework_spek2}")
        implementation("ch.tutteli.atrium:atrium-cc-en_GB-robstoll:${Versions.ch_tutteli_atrium}")
        implementation("io.mockk:mockk:${Versions.ch_tutteli_atrium}")

        runtimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.org_spekframework_spek2}")
        runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")
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

tasks {
  val jvmTest by getting(Test::class) {
    useJUnitPlatform {
      includeEngines("spek2")
    }
  }
}
