plugins {
  id("convention.common")
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
  }
}

// TODO(COMPOSITE) Remove once KMP properly supports composite builds
allprojects {
  configurations.all {
    resolutionStrategy {
      dependencySubstitution {
        val reduxVersion = "+"
        substitute(module("org.reduxkotlin:redux-kotlin"))
          .using(module("org.reduxkotlin:redux-kotlin:$reduxVersion"))
        substitute(module("org.reduxkotlin:redux-kotlin-threadsafe"))
          .using(module("org.reduxkotlin:redux-kotlin-threadsafe:$reduxVersion"))
      }
    }
  }
}
