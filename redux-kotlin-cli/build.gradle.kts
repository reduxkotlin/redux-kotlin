import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("convention.common")
    kotlin("jvm")
    application
    // For compose.desktop.currentOs — the host Skiko runtime that `rk snapshot`
    // needs to rasterize. redux-kotlin-snapshot deliberately exports only the
    // host-agnostic compose.desktop.common (so its published POM stays portable);
    // the running binary supplies the host native runtime here.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Pin the compile JDK so installDist is deterministic regardless of the developer's default Java.
kotlin {
    jvmToolchain(17)
}

application {
    applicationName = "rk"
    mainClass.set("org.reduxkotlin.cli.MainKt")
}

// Stamp the project version into a resource that Version.kt reads at runtime for `rk --version`.
tasks.named<ProcessResources>("processResources") {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("rk-version.properties") {
        expand(mapOf("version" to v))
    }
}

// The installDist launcher resolves its runtime Java from JAVA_HOME/PATH, which may be older than
// the required 17. Guard the generated unix launcher so it fails with a clear message instead of a
// cryptic UnsupportedClassVersionError (thrown at class-load, before main() can react).
tasks.startScripts {
    doLast {
        val q = "\""
        val d = "\$"
        val guard = listOf(
            "",
            "# rk requires Java 17+ (see redux-kotlin-cli/README.md).",
            "RK_JV=$d($q${d}JAVACMD$q -version 2>&1 \\",
            "  | awk -F'[$q.]' '/version/{print (${d}2==${q}1$q?${d}3:${d}2);exit}')",
            "if [ -n $q${d}RK_JV$q ] && [ $q${d}RK_JV$q -lt 17 ] 2>/dev/null; then",
            "    echo ${q}rk requires Java 17+, but '${d}JAVACMD' is Java ${d}RK_JV.$q >&2",
            "    echo ${q}Point JAVA_HOME at a JDK 17 or newer and re-run.$q >&2",
            "    exit 1",
            "fi",
            "",
            "",
        ).joinToString("\n")
        val execLine = "exec $q${d}JAVACMD$q $q$d@$q"
        require(unixScript.readText().contains(execLine)) {
            "startScripts template changed: exec line not found, Java-17 guard not injected"
        }
        unixScript.writeText(unixScript.readText().replace(execLine, guard + execLine))
    }
}

dependencies {
    implementation(project(":redux-kotlin-devtools-cli"))
    implementation(project(":redux-kotlin-snapshot"))
    implementation(libs.clikt)
    // Host Skiko/AWT runtime so the bundled `rk snapshot` can rasterize off-screen.
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}
