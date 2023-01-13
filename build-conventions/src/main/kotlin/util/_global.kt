@file:Suppress("PackageDirectoryMismatch")

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.konan.target.HostManager

val NamedDomainObjectContainer<KotlinSourceSet>.jsMain: NamedDomainObjectProvider<KotlinSourceSet>
  get() = named<KotlinSourceSet>("jsMain")

val NamedDomainObjectContainer<KotlinSourceSet>.jsTest: NamedDomainObjectProvider<KotlinSourceSet>
  get() = named<KotlinSourceSet>("jsTest")

val NamedDomainObjectContainer<KotlinSourceSet>.jvmMain: NamedDomainObjectProvider<KotlinSourceSet>
  get() = named<KotlinSourceSet>("jvmMain")

val NamedDomainObjectContainer<KotlinSourceSet>.jvmTest: NamedDomainObjectProvider<KotlinSourceSet>
  get() = named<KotlinSourceSet>("jvmTest")

infix fun <T> Property<T>.by(value: T) = set(value)
infix fun <T> Property<T>.by(value: Provider<T>) = set(value)

val CI = System.getenv("CI") != null
val SANDBOX = System.getenv("SANDBOX") != null

val Project.isMainHost: Boolean
  get() = HostManager.simpleOsName().equals("${properties["project.mainOS"]}", true)

fun printlnCI(text: Any?) {
  if (CI) println("[CI]: $text")
}
