package org.reduxkotlin.devtools.monitor

import java.io.File

/** Writes [text] to a file named [suggestedName] on the local filesystem. */
public actual fun saveRecording(suggestedName: String, text: String) {
    File(suggestedName).writeText(text)
}

/** Reads `recording.jsonl` from the current working directory and calls [onLoaded] if it exists. */
public actual fun loadRecording(onLoaded: (String) -> Unit) {
    val f = File("recording.jsonl")
    if (f.exists()) {
        onLoaded(f.readText())
    } else {
        println("loadRecording: ${f.absolutePath} not found — nothing loaded")
    }
}
