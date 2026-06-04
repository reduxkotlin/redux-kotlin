package org.reduxkotlin.devtools.monitor

// RecordingHeader / encodeRecording / decodeRecording now live in :redux-kotlin-devtools-bridge
// (org.reduxkotlin.devtools.bridge). Platform file pickers remain here.

/** Writes [text] as a recording file/blob named [suggestedName]; platform-specific. */
public expect fun saveRecording(suggestedName: String, text: String)

/** Prompts for + reads a recording file/blob; calls [onLoaded] with its contents (async on web). */
public expect fun loadRecording(onLoaded: (String) -> Unit)
