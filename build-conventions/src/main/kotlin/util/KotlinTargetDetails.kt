package util

import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

val KonanTarget.buildHost: Family
    get() = when (family) {
        Family.OSX,
        Family.IOS,
        Family.TVOS,
        Family.WATCHOS -> Family.OSX

        Family.ANDROID,
        Family.ZEPHYR,
        Family.WASM,
        Family.LINUX -> Family.LINUX

        Family.MINGW -> Family.MINGW
    }
