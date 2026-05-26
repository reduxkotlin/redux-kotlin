package util

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

fun NamedDomainObjectContainer<KotlinSourceSet>.withName(name: String, action: Action<KotlinSourceSet>) {
    matching { it.name == name }.all(action)
}

private fun NamedDomainObjectContainer<KotlinSourceSet>.sharedSourceSets(
    vararg sourceSets: String,
    action: Action<KotlinSourceSet>,
) {
    sourceSets.forEach { withName(it, action) }
}

fun NamedDomainObjectContainer<KotlinSourceSet>.jvmCommonMain(action: Action<KotlinSourceSet>) {
    sharedSourceSets("jvmCommonMain", "androidMain", action = action)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.jvmCommonTest(action: Action<KotlinSourceSet>) {
    sharedSourceSets("jvmCommonTest", "androidUnitTest", "androidHostTest", action = action)
}
