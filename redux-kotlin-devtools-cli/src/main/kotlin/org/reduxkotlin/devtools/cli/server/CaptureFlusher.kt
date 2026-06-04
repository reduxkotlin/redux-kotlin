package org.reduxkotlin.devtools.cli.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.reduxkotlin.devtools.monitor.MonitorIngest
import java.io.File

/** Write every store currently known to [ingest] into [dir] as a `.jsonl` recording. */
internal fun flushAll(ingest: MonitorIngest, dir: File) {
    ingest.registry.state.value.stores.forEach { entry ->
        val rec = ingest.recordingFor(entry.ref.id) ?: return@forEach
        writeStoreCapture(dir, entry.ref.id, rec.first, rec.second)
    }
}

/** Continuously flush captures to [dir] whenever the ingest registry changes. Launches in [scope]. */
internal fun startFlushing(scope: CoroutineScope, ingest: MonitorIngest, dir: File) {
    scope.launch {
        ingest.registry.state.collectLatest { flushAll(ingest, dir) }
    }
}
