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
                substitute(module("org.reduxkotlin:redux-kotlin:_"))
                    .using(module("org.reduxkotlin:redux-kotlin:_"))
                substitute(module("org.reduxkotlin:redux-kotlin-threadsafe:_"))
                    .using(module("org.reduxkotlin:redux-kotlin-threadsafe:_"))
            }
        }
    }
}
