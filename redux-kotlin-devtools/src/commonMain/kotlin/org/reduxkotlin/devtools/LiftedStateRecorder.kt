package org.reduxkotlin.devtools

import kotlin.concurrent.Volatile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The result of recording one dispatched action — the data needed to relay an ACTION message. */
internal data class RecordedAction(
    val actionId: Int,
    val actionJson: JsonElement,
    val timestamp: Long,
    val isExcess: Boolean,
)

/**
 * Maintains the Redux DevTools "lifted state" for monitoring only: assigns action ids, keeps a
 * ring buffer of the last [maxAge] actions, and builds the lifted-state JSON the monitor renders.
 * No time-travel recomputation — that is phase 2.
 */
internal class LiftedStateRecorder(private val maxAge: Int, private val clock: EpochMillis) {
    private data class Entry(val id: Int, val actionJson: JsonElement, val timestamp: Long, val state: JsonElement)

    private var nextActionId = 1
    private val staged = ArrayDeque<Entry>()
    private var committedState: JsonElement = JsonPrimitive(0)

    /**
     * Immutable snapshot of lifted state, published by the writer (dispatch thread) and consumed
     * by readers on other threads. One action behind is acceptable for a debug tool.
     */
    @Volatile
    private var snapshot: JsonObject? = null

    /** Seeds the history with the @@INIT action (id 0) and the initial [state]. */
    fun init(state: JsonElement) {
        committedState = state
        staged.clear()
        staged.addLast(Entry(id = 0, actionJson = initActionJson(), timestamp = clock(), state = state))
        nextActionId = 1
        snapshot = buildSnapshot()
    }

    /** Records a dispatched [action] and the resulting [state]; returns the relay payload. */
    fun record(action: JsonElement, state: JsonElement): RecordedAction {
        val id = nextActionId++
        val ts = clock()
        staged.addLast(Entry(id = id, actionJson = action, timestamp = ts, state = state))
        val excess = staged.size > maxAge
        if (excess) {
            val dropped = staged.removeFirst()
            committedState = dropped.state
        }
        snapshot = buildSnapshot()
        return RecordedAction(actionId = id, actionJson = action, timestamp = ts, isExcess = excess)
    }

    /** Returns the last published lifted-state snapshot; safe to call from any thread. */
    fun liftedState(): JsonObject = snapshot ?: buildSnapshot()

    /** Builds the full lifted-state JSON object (the STATE message payload). */
    private fun buildSnapshot(): JsonObject {
        val ids = staged.map { it.id }
        val actionsById = JsonObject(
            staged.associate { entry ->
                entry.id.toString() to performAction(entry.actionJson, entry.timestamp)
            },
        )
        val computed = JsonArray(staged.map { JsonObject(mapOf("state" to it.state)) })
        return JsonObject(
            mapOf(
                "monitorState" to JsonNull,
                "nextActionId" to JsonPrimitive(nextActionId),
                "actionsById" to actionsById,
                "stagedActionIds" to JsonArray(ids.map { JsonPrimitive(it) }),
                "skippedActionIds" to JsonArray(emptyList()),
                "committedState" to committedState,
                "currentStateIndex" to JsonPrimitive(staged.size - 1),
                "computedStates" to computed,
                "isLocked" to JsonPrimitive(false),
                "isPaused" to JsonPrimitive(false),
            ),
        )
    }

    private fun performAction(actionJson: JsonElement, timestamp: Long): JsonObject = JsonObject(
        mapOf(
            "type" to JsonPrimitive("PERFORM_ACTION"),
            "action" to actionJson,
            "timestamp" to JsonPrimitive(timestamp),
            "stack" to JsonNull,
        ),
    )

    private fun initActionJson(): JsonObject = JsonObject(mapOf("type" to JsonPrimitive("@@INIT")))
}
