package org.reduxkotlin.devtools

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One observed store. Captures `(action, state)` cheaply on the dispatch thread and serializes,
 * diffs, and records lifted state on a single background coroutine, emitting [DevToolsEvent]s on
 * [events]. Construct via [create]; the [DevToolsHub] owns sessions in normal use.
 *
 * Concurrency contract: only the consumer coroutine touches [recorder] and [lastStateJson], so the
 * recorder's single-threaded invariant holds without locks. Producers (`init`/`record`) hand off via
 * a non-blocking [Channel.trySend] from the dispatch thread — recording never blocks dispatch.
 */
public class DevToolsSession private constructor(
    /** Stable id for this session (the config's `instanceId` or `name`). */
    public val id: String,
    private val config: DevToolsConfig,
    private val serializer: ValueSerializer,
    private val recorder: LiftedStateRecorder,
    private val scope: CoroutineScope,
) {
    private val denyRegex = config.denylist.map(::Regex)
    private val allowRegex = config.allowlist.map(::Regex)

    // DROP_OLDEST: under sustained burst we drop the oldest pending capture rather than block
    // dispatch. We count drops and warn (throttled) so silent history gaps are visible.
    private val captures = Channel<Capture>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var dropped = 0L
    private val recentLock = kotlinx.atomicfu.locks.SynchronizedObject()
    private val recent = ArrayDeque<DevToolsEvent.ActionRecorded>()

    private val _events = MutableSharedFlow<DevToolsEvent>(
        replay = 1,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Hot stream of recorder events. Supports multiple simultaneous collectors — the in-app drawer
     * and remote WebSocket sink each collect independently without competing for items. Replays the
     * last event to late subscribers so they can observe the most recent state transition. Use
     * [history] to backfill older actions missed before subscribing.
     */
    public val events: SharedFlow<DevToolsEvent> = _events

    // Consumer-coroutine-confined: only [process] reads/writes it. No @Volatile needed.
    private var lastStateJson: JsonElement? = null

    private sealed interface Capture {
        data class Init(val state: Any?) : Capture
        data class Action(val action: Any, val state: Any?, val timestampMillis: Long) : Capture
    }

    init {
        scope.launch {
            for (capture in captures) {
                runCatching { process(capture) }.onFailure { config.logger("devtools process: ${it.message}") }
            }
        }
    }

    /** Records the store's initial state. Call once at store creation, before any reader. */
    public fun init(state: Any?) {
        captures.trySend(Capture.Init(state))
    }

    /** Records a dispatched [action] and the resulting [state]. Cheap; heavy work runs off-thread. */
    public fun record(action: Any, state: Any?) {
        if (!shouldSend(action, denyRegex, allowRegex)) return
        // systemClock() is the dispatch-time capture timestamp (more accurate than the recorder's
        // own clock read on the background thread); the small skew between the two is acceptable.
        val result = captures.trySend(Capture.Action(action, state, systemClock()))
        if (result.isFailure) {
            dropped++
            if (dropped == 1L || dropped % 100L == 0L) {
                config.logger(
                    "devtools: dropped $dropped captures (dispatch outpacing recorder)",
                )
            }
        }
    }

    /** The current lifted-state snapshot (Redux DevTools shape). Safe from any thread after [init]. */
    public fun liftedState(): JsonObject = recorder.liftedState()

    /**
     * A snapshot of recently recorded actions (bounded by `maxAge`), newest last. A subscriber should
     * call this immediately after subscribing to [events], then dedupe by [DevToolsEvent.ActionRecorded.actionId]
     * to backfill the actions it missed before subscribing.
     */
    public fun history(): List<DevToolsEvent.ActionRecorded> =
        kotlinx.atomicfu.locks.synchronized(recentLock) { recent.toList() }

    /** Stops background processing. Idempotent. */
    public fun close() {
        captures.close()
        scope.cancel()
    }

    private fun process(capture: Capture) {
        when (capture) {
            is Capture.Init -> {
                val stateJson = serializer.toJson(capture.state)
                recorder.init(stateJson)
                lastStateJson = stateJson
                kotlinx.atomicfu.locks.synchronized(recentLock) { recent.clear() }
                _events.tryEmit(DevToolsEvent.Initialized(stateJson))
            }

            is Capture.Action -> {
                val actionJson = serializer.toJson(capture.action)
                val stateJson = serializer.toJson(capture.state)
                val before = lastStateJson ?: stateJson
                val diff = diffJson(before, stateJson)
                val recorded = recorder.record(actionJson, stateJson)
                lastStateJson = stateJson
                val event = DevToolsEvent.ActionRecorded(
                    actionId = recorded.actionId,
                    action = actionJson,
                    state = stateJson,
                    diff = diff,
                    timestampMillis = capture.timestampMillis,
                    isExcess = recorded.isExcess,
                )
                kotlinx.atomicfu.locks.synchronized(recentLock) {
                    recent.addLast(event)
                    while (recent.size > config.maxAge) recent.removeFirst()
                }
                _events.tryEmit(event)
            }
        }
    }

    /** Factory used by [DevToolsHub] and tests. [dispatcher] defaults to [Dispatchers.Default]. */
    public companion object {
        /** Creates a session running its single background consumer on [dispatcher]. */
        public fun create(
            config: DevToolsConfig,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): DevToolsSession {
            val serializer = config.serializer ?: platformDefaultSerializer()
            val recorder = LiftedStateRecorder(maxAge = config.maxAge, clock = systemClock)
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            return DevToolsSession(config.instanceId ?: config.name, config, serializer, recorder, scope)
        }
    }
}
