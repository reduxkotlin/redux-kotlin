import util.jvmCommonTest

plugins {
  id("convention.library-mpp-loved")
  id("convention.atomicfu")
  id("convention.publishing-mpp")
}

android {
  namespace = "org.reduxkotlin.threadsafe"
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(project(":redux-kotlin"))
        implementation("org.jetbrains.kotlinx:atomicfu:_")
      }
    }
    jvmCommonTest {
      dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:_")
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

// apply(from = rootProject.file("gradle/publish.gradle"))
