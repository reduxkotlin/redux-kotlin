package util

import org.gradle.api.Project

/**
 * Whether an Android SDK is available to this build: `sdk.dir` set (non-blank) in the root
 * project's `local.properties`, or `ANDROID_HOME` / `ANDROID_SDK_ROOT` exported. Modules and
 * convention plugins use it to gate the Android target so SDK-less hosts (CI shards, sandboxes)
 * still configure and build the remaining targets.
 */
val Project.hasAndroidSdk: Boolean
    get() {
        val localProps = rootProject.file("local.properties")
        val hasSdkInLocalProperties = localProps.exists() && localProps.readText().lineSequence().any {
            it.trim().startsWith("sdk.dir=") && it.substringAfter("sdk.dir=").isNotBlank()
        }
        val hasSdkInEnv =
            !System.getenv("ANDROID_HOME").isNullOrBlank() ||
                !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
        return hasSdkInLocalProperties || hasSdkInEnv
    }
