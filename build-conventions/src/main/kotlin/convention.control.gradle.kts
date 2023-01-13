import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import util.buildHost

plugins {
  id("convention.common")
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
  extensions.getByType(KotlinMultiplatformExtension::class.java).targets.let(::control)
}
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
  objects.namedDomainObjectList(KotlinTarget::class.java).apply {
    add(extensions.getByType(KotlinJvmProjectExtension::class.java).target)
  }.let(::control)
}
pluginManager.withPlugin("org.jetbrains.kotlin.js") {
  objects.namedDomainObjectList(KotlinTarget::class.java).apply {
    add(extensions.getByType(KotlinJsProjectExtension::class.java).js())
  }.let(::control)
}

fun control(targets: NamedDomainObjectCollection<KotlinTarget>) {
  fun NamedDomainObjectCollection<out KotlinTarget>.onlyBuildIf(enabled: Spec<in Task>) {
    all {
      if (this is KotlinNativeTarget) {
        binaries.all {
          linkTask.onlyIf(enabled)
        }
      }
      compilations.all {
        compileTaskProvider {
          onlyIf(enabled)
        }
      }
    }
  }

  val nativeTargets = targets.withType<KotlinNativeTarget>()
  val windowsHostTargets = nativeTargets.matching { it.konanTarget.buildHost == Family.MINGW }
  val linuxHostTargets = nativeTargets.matching { it.konanTarget.buildHost == Family.LINUX }
  val osxHostTargets = nativeTargets.matching { it.konanTarget.buildHost == Family.OSX }
  val mainHostTargets = targets.matching { it !in nativeTargets }
  linuxHostTargets.onlyBuildIf {
    val enabled = HostManager.hostIsLinux
    printlnCI("[${it.name}] ${!CI} || $SANDBOX || ${HostManager.hostIsLinux} = $enabled")
    enabled
  }
  osxHostTargets.onlyBuildIf {
    val enabled = HostManager.hostIsMac
    printlnCI("[${it.name}] ${!CI} || $SANDBOX || ${HostManager.hostIsMac} = $enabled")
    enabled
  }
  windowsHostTargets.onlyBuildIf {
    val enabled = HostManager.hostIsMingw
    printlnCI("[${it.name}] ${!CI} || $SANDBOX || ${HostManager.hostIsMingw} = $enabled")
    enabled
  }
  mainHostTargets.onlyBuildIf {
    val enabled = !CI || SANDBOX || isMainHost
    printlnCI("[${it.name}] ${!CI} || $SANDBOX || $isMainHost = $enabled")
    enabled
  }
}
