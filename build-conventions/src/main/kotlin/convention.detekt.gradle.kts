import io.gitlab.arturbosch.detekt.Detekt

plugins {
  id("io.gitlab.arturbosch.detekt")
}

dependencies {
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:_")
}

detekt {
  config.from(rootDir.resolve("gradle/detekt.yml"))
  buildUponDefaultConfig = true
  source = files("src/", "*.kts")
}

tasks {
  if (project == rootProject) {
    register("detektAll", Detekt::class) {
      description = "Run Detekt for all modules"
      config.from(project.detekt.config)
      buildUponDefaultConfig = project.detekt.buildUponDefaultConfig
      setSource(files(projectDir))
      exclude("**/klip-kotlin-plugin-native/src")
    }
  }
  afterEvaluate {
    withType<Detekt> {
      parallel = true
      reports {
        // observe findings in your browser with structure and code snippets
        html.required.set(true)
        // checkstyle like format mainly for integrations like Jenkins
        xml.required.set(true)
        // similar to the console output, contains issue signature to manually edit baseline files
        txt.required.set(true)
        // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations with Github Code Scanning
        sarif.required.set(true)
      }
      include("**/*.kt", "**/*.kts")
      exclude("**/build", "scripts/")
    }
  }
}
