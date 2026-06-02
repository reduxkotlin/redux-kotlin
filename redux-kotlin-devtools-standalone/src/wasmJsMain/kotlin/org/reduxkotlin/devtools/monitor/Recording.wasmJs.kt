package org.reduxkotlin.devtools.monitor

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader

/** Triggers a browser download of [text] as a blob named [suggestedName]. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
public actual fun saveRecording(suggestedName: String, text: String) {
    val parts = JsArray<JsAny?>()
    parts[0] = text.toJsString()
    val blob = Blob(parts, BlobPropertyBag(type = "application/jsonl"))
    val url = URL.createObjectURL(blob)
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = url
    anchor.download = suggestedName
    anchor.click()
    URL.revokeObjectURL(url)
}

/** Opens a hidden `<input type=file>` picker and reads the chosen file's text into [onLoaded]. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
public actual fun loadRecording(onLoaded: (String) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = ".jsonl,application/jsonl,text/plain"
    input.onchange = { _: Event ->
        val file = input.files?.item(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = { _: Event ->
                (reader.result as? JsString)?.toString()?.let(onLoaded)
            }
            reader.readAsText(file)
        }
    }
    input.click()
}
