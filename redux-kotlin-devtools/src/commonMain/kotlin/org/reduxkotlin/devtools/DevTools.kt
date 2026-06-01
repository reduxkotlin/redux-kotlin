package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.Store
import org.reduxkotlin.StoreCreator
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.devtools.wire.MessageContext
import org.reduxkotlin.devtools.wire.actionMessage

/**
 * Creates a [StoreEnhancer] that streams dispatched actions and state to the Redux DevTools
 * Remote monitor. Read-only: it never alters store state. Recommended for `debugImplementation`.
 *
 * Usage:
 * ```
 * val store = createStore(reducer, initialState, devTools(DevToolsConfig(name = "MyStore")))
 * ```
 */
public fun <State> devTools(config: DevToolsConfig = DevToolsConfig()): StoreEnhancer<State> =
    { storeCreator -> enhancedCreator(config, storeCreator) }

private fun <State> enhancedCreator(config: DevToolsConfig, storeCreator: StoreCreator<State>): StoreCreator<State> =
    { reducer, initialState, enhancer ->
        val store: Store<State> = storeCreator(reducer, initialState, enhancer)
        val serializer = config.serializer ?: platformDefaultSerializer()
        val recorder = LiftedStateRecorder(maxAge = config.maxAge, clock = systemClock)
        recorder.init(serializer.toJson(store.getState()))

        val session = DevToolsSession(config, recorder)
        session.start()

        val instanceId = config.instanceId ?: config.name
        val ctx = MessageContext(socketId = null, name = config.name, instanceId = instanceId)
        val denyRegex = config.denylist.map { Regex(it) }
        val allowRegex = config.allowlist.map { Regex(it) }
        val origDispatch = store.dispatch
        store.dispatch = { action ->
            val result = origDispatch(action)
            if (shouldSend(action, denyRegex, allowRegex)) {
                relay(serializer, recorder, session, ctx, store, action)
            }
            result
        }
        store
    }

private fun <State> relay(
    serializer: ValueSerializer,
    recorder: LiftedStateRecorder,
    session: DevToolsSession,
    ctx: MessageContext,
    store: Store<State>,
    action: Any,
) {
    // Serialization runs synchronously on the dispatch thread; debug-only, keep it cheap.
    val actionJson: JsonElement = serializer.toJson(action)
    val stateJson = serializer.toJson(store.getState())
    val recorded = recorder.record(actionJson, stateJson)
    val performAction = performActionJson(actionJson, recorded.timestamp)
    session.enqueue(
        actionMessage(
            ctx = ctx,
            performAction = performAction,
            nextActionId = recorded.actionId + 1,
            isExcess = recorded.isExcess,
        ),
    )
}

private fun performActionJson(actionJson: JsonElement, timestamp: Long): JsonObject = JsonObject(
    mapOf(
        "type" to JsonPrimitive("PERFORM_ACTION"),
        "action" to actionJson,
        "timestamp" to JsonPrimitive(timestamp),
        "stack" to JsonNull,
    ),
)

/** Returns true if [action] passes the [denylist]/[allowlist] regex filters. */
internal fun shouldSend(action: Any, denylist: List<Regex>, allowlist: List<Regex>): Boolean {
    val key = action::class.simpleName ?: action.toString()
    val denied = denylist.any { it.containsMatchIn(key) }
    val allowed = allowlist.isEmpty() || allowlist.any { it.containsMatchIn(key) }
    return !denied && allowed
}
