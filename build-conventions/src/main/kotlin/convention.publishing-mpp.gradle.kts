import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import util.buildHost

plugins {
  kotlin("multiplatform")
  id("convention.publishing")
}

kotlin {
  fun NamedDomainObjectCollection<out Named>.onlyPublishIf(enabled: Spec<in Task>) {
    publishing {
      publications {
        matching { it.name in this@onlyPublishIf.names }.all {
          val targetPublication = this@all
          tasks {
            withType<AbstractPublishToMaven>()
              .all { onlyIf { publication != targetPublication || enabled(this) } }
            withType<GenerateModuleMetadata>()
              .all { onlyIf { publication.orNull != targetPublication || enabled(this) } }
          }
        }
      }
    }
  }

  val nativeTargets = targets.withType<KotlinNativeTarget>()
  val windowsHostTargets = nativeTargets.matching { it.konanTarget.buildHost == Family.MINGW }
  val linuxHostTargets = nativeTargets.matching { it.konanTarget.buildHost == Family.LINUX }
  val osxHostTargets = nativeTargets.matching { it.konanTarget.buildHost == Family.OSX }
  val mainHostTargets = targets.matching { it !in nativeTargets }
  val androidTargets = targets.withType<KotlinAndroidTarget>()
  val mpp = objects.domainObjectContainer(Named::class.java)
  mpp.add(Named { "kotlinMultiplatform" })

  androidTargets.all {
    if (!CI || SANDBOX || isMainHost) {
      publishLibraryVariants("release", "debug")
    }
  }

  linuxHostTargets.onlyPublishIf {
    val enabled = !CI || SANDBOX || HostManager.hostIsLinux
    printlnCI("[${it.name}] ${!CI} || $SANDBOX || ${HostManager.hostIsLinux} = $enabled")
    enabled
  }
  osxHostTargets.onlyPublishIf {
    val enabled = !CI || SANDBOX || HostManager.hostIsMac
    printlnCI("[${it.name}] ${!CI} || $SANDBOX || ${HostManager.hostIsMac} = $enabled")
    enabled
  }
  windowsHostTargets.onlyPublishIf {
    val enabled = !CI || SANDBOX || HostManager.hostIsMingw
    printlnCI("[${it.name}] ${!CI} || $SANDBOX || ${HostManager.hostIsMingw} = $enabled")
    enabled
  }
  mainHostTargets.onlyPublishIf {
    val enabled = !CI || SANDBOX || isMainHost
    printlnCI("[${it.name}] ${!CI} || $SANDBOX || $isMainHost = $enabled")
    enabled
  }
  mpp.onlyPublishIf {
    val enabled = !CI || SANDBOX || isMainHost
    println("[${it.name}] ${!CI} || $SANDBOX || $isMainHost = $enabled")
    enabled
  }
}
