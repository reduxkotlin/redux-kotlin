plugins {
  id("convention.common")
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
  }
}

// TODO(KT-52172) Remove once KMP properly supports composite builds
allprojects {
  if (findProperty("project.localRepo") == "true") {
    repositories {
      val local = maven("file://${rootProject.projectDir.parentFile}/build/localMaven") {
        name = "Local"
      }
      remove(local)
      add(0, local)
    }
  }
  configurations.all {
    resolutionStrategy {
      dependencySubstitution {
        val reduxVersion = "+"
        substitute(module("org.reduxkotlin:redux-kotlin")).using(module("org.reduxkotlin:redux-kotlin:$reduxVersion"))
        substitute(module("org.reduxkotlin:redux-kotlin-threadsafe")).using(
          module("org.reduxkotlin:redux-kotlin-threadsafe:$reduxVersion")
        )
      }
    }
  }
}
