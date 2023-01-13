plugins {
  java
  kotlin("multiplatform")
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
      dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
        implementation("io.mockk:mockk-common:${Versions.io_mockk}")
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-junit"))
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
