package org.reduxkotlin.devtools.monitor

/** Stub: logs to console until the Blob download is wired in the web entry task. */
public actual fun saveRecording(suggestedName: String, text: String) {
    // TODO(web): Blob download — wired in the web entry task. Minimal stub keeps wasmJs compiling.
    println("saveRecording($suggestedName): ${text.length} chars")
}

/** Stub: no-op until the file-input picker is wired in the web entry task. */
public actual fun loadRecording(onLoaded: (String) -> Unit) {
    // TODO(web): <input type=file> upload — wired in the web entry task.
}
