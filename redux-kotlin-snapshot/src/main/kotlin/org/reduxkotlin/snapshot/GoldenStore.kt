package org.reduxkotlin.snapshot

import java.io.File

/** Reads and writes golden PNGs under [dir], keyed by name. */
public class GoldenStore(private val dir: File) {
    /** The golden file for [name] (not guaranteed to exist). */
    public fun goldenFile(name: String): File = File(dir, "$name.png")

    /** Reads golden bytes for [name], or null if absent. */
    public fun read(name: String): ByteArray? = goldenFile(name).takeIf { it.isFile }?.readBytes()

    /** Writes (or overwrites) the golden for [name]. */
    public fun write(name: String, png: ByteArray) {
        dir.mkdirs()
        goldenFile(name).writeBytes(png)
    }
}
