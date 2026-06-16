plugins {
    id("convention.common")
    kotlin("jvm")
    application
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Pin the compile JDK (not just the bytecode target) so the build is deterministic regardless of
// the developer's default Java, and so we never accidentally compile against a >17 JDK API. The
// foojay resolver (settings.gradle.kts) auto-downloads JDK 17 when it isn't already installed.
kotlin {
    jvmToolchain(17)
}

application {
    applicationName = "rk-devtools"
    mainClass.set("org.reduxkotlin.devtools.cli.MainKt")
}

// The installDist launcher resolves its runtime Java from JAVA_HOME/PATH, which may be older than
// the required 17. Guard the generated unix launcher so it fails with a clear message instead of a
// cryptic UnsupportedClassVersionError (which is thrown at class-load, before main() can react).
tasks.startScripts {
    doLast {
        val q = "\""
        val d = "\$"
        val guard = listOf(
            "",
            "# rk-devtools requires Java 17+ (see redux-kotlin-devtools-cli/README.md).",
            "RKDT_JV=$d($q${d}JAVACMD$q -version 2>&1 \\",
            "  | awk -F'[$q.]' '/version/{print (${d}2==${q}1$q?${d}3:${d}2);exit}')",
            "if [ -n $q${d}RKDT_JV$q ] && [ $q${d}RKDT_JV$q -lt 17 ] 2>/dev/null; then",
            "    echo ${q}rk-devtools requires Java 17+, but '${d}JAVACMD' is Java ${d}RKDT_JV.$q >&2",
            "    echo ${q}Point JAVA_HOME at a JDK 17 or newer and re-run.$q >&2",
            "    exit 1",
            "fi",
            "",
            "",
        ).joinToString("\n")
        val execLine = "exec $q${d}JAVACMD$q $q$d@$q"
        unixScript.writeText(unixScript.readText().replace(execLine, guard + execLine))
    }
}

dependencies {
    implementation(project(":redux-kotlin-devtools-core"))
    implementation(project(":redux-kotlin-devtools-bridge"))
    implementation(project(":redux-kotlin-devtools-standalone"))
    implementation(project(":redux-kotlin-devtools-ui"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.ui)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
