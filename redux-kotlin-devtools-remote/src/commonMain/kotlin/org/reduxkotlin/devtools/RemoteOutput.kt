package org.reduxkotlin.devtools.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.DevToolsOutput
import org.reduxkotlin.devtools.DevToolsSession
import org.reduxkotlin.devtools.remote.wire.MessageContext
import org.reduxkotlin.devtools.remote.wire.actionMessage
import org.reduxkotlin.devtools.remote.wire.stateMessage

/**
 * Streams a [DevToolsSession]'s feed to the external Redux DevTools monitor over WebSocket.
 *
 * Off by default — it carries connection overhead. Whoever binds this output to a session — the
 * in-app Outputs panel, or your own setup code — calls [start]; it should consult [startEnabled]
 * to decide whether to connect immediately.
 *
 * Late-start correctness: because the monitor expects a full STATE snapshot on (re)connect and the
 * session's flow only replays its single most recent event, [start] seeds the connection with the
 * current [DevToolsSession.liftedState] before following [DevToolsSession.events]. Events are handed
 * to the connection's bounded outbound buffer (`enqueue`), which the connect loop drains once the WS
 * handshake completes — so events captured before the socket is up are buffered, not lost.
 *
 * @param config connection settings.
 */
public class RemoteOutput(private val config: RemoteConfig) : DevToolsOutput {

    override val id: String = "remote"
    override val label: String = "Remote (WebSocket)"

    private var scope: CoroutineScope? = null
    private var connection: RemoteConnection? = null

    /** Whether the output is currently subscribed/connected. */
    public val isRunning: Boolean get() = scope != null

    /**
     * Whether this output should connect as soon as it is bound to a session
     * (mirrors [RemoteConfig.startEnabled]).
     */
    public val startEnabled: Boolean get() = config.startEnabled

    /**
     * Subscribes to [session] and opens the WebSocket connection.
     *
     * Not thread-safe; call [start]/[stop] from a single thread (e.g. the UI thread).
     */
    override fun start(session: DevToolsSession) {
        if (isRunning) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        val conn = RemoteConnection(config, session).also { connection = it }
        conn.start()
        runCatching { conn.enqueueState(session.liftedState()) }
        session.events
            .onEach { event -> runCatching { conn.enqueue(toWireMessage(event, session)) } }
            .launchIn(s)
    }

    /**
     * Cancels the subscription and closes the WebSocket connection.
     *
     * Not thread-safe; call [start]/[stop] from a single thread (e.g. the UI thread).
     */
    override fun stop() {
        connection?.stop()
        connection = null
        scope?.cancel()
        scope = null
    }

    private fun toWireMessage(event: DevToolsEvent, session: DevToolsSession): JsonObject {
        val ctx = MessageContext(socketId = null, name = session.id, instanceId = session.id)
        return when (event) {
            is DevToolsEvent.Initialized -> stateMessage(ctx, session.liftedState())

            is DevToolsEvent.ActionRecorded -> {
                val performAction = JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("PERFORM_ACTION"),
                        "action" to event.action,
                        "timestamp" to JsonPrimitive(event.timestampMillis),
                        "stack" to JsonNull,
                    ),
                )
                actionMessage(
                    ctx = ctx,
                    performAction = performAction,
                    state = event.state,
                    nextActionId = event.actionId + 1,
                    isExcess = event.isExcess,
                )
            }
        }
    }
}
