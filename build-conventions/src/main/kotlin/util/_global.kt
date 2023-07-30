@file:Suppress("PackageDirectoryMismatch")

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.konan.target.HostManager

infix fun <T> Property<T>.by(value: T) = set(value)
infix fun <T> Property<T>.by(value: Provider<T>) = set(value)

val CI = System.getenv("CI") != null
val SANDBOX = System.getenv("SANDBOX") != null

val Project.isMainHost: Boolean
    get() = HostManager.simpleOsName().equals("${properties["project.mainOS"]}", true)

fun printlnCI(text: Any?) {
    if (CI) println("[CI]: $text")
}

internal inline val Project.libs get() = the<LibrariesForLibs>()
