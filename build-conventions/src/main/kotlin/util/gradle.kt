package util

import java.nio.charset.Charset

object Git {
    val headCommitHash by lazy { execAndCapture("git", "rev-parse", "--verify", "HEAD") }
}

fun execAndCapture(vararg cmd: String): String? {
    val process = ProcessBuilder(*cmd).start()
    val output = process.inputStream.readAllBytes().toString(Charset.defaultCharset()).trim()
    process.waitFor()
    return if (process.exitValue() == 0) output else null
}
