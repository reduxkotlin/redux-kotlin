# redux-kotlin DevTools Pipeline Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pipeline capture to `redux-kotlin-devtools-core` — drop-in combinators (`devToolsMiddleware`, `devToolsCombineReducers`, `named`) that record the static structure `dispatch → [middleware…] → rootReducer{named reducers}` once, and a per-action trace (which nodes traversed, per-node timing, forwarded/changed) that the in-app Pipeline tab lights up.

**Architecture:** The combinators own the middleware list / reducer set, so they capture order + labels for free. Each is wrapped to time itself and append a node to the **current dispatch's trace** held by a per-session `PipelineRecorder` (a stack, for nested re-dispatch). The trace is built id-less during the synchronous dispatch; the wrapper commits it to `session.pendingTrace`; the existing `devTools()` enhancer — which records on the same thread right after the chain returns — picks it up and bundles it into the same capture, so the consumer coroutine assigns **one** recorder id to both the `ActionRecorded` and the `PipelineTraced` events. No second id source, correlates even when captures are dropped.

**Tech Stack:** Builds on Plan 1 (`redux-kotlin-devtools-core`). `kotlin.time.TimeSource.Monotonic` for nanos (multiplatform, no expect/actual). No new module, no new dependency.

**Depends on:** Plan 1 complete (`DevToolsSession`, `DevToolsHub`, `devTools()`, `DevToolsEvent`, `DevToolsConfig`).

**This plan modifies Plan 1 files** (same module): adds cases to the sealed `DevToolsEvent`; extends `DevToolsSession` (the `pendingTrace` rendezvous, `PipelineRecorder`, `registerPipeline`, bundling in `record`/`process`); and amends the `devTools()` enhancer to pick up `pendingTrace`. These are additive — Plan 1's tests must still pass after each task.

**Wiring contract (resolved design spike — read before coding):**
- **Same config keys the session.** `devTools(config)`, `devToolsMiddleware(config, …)`, and `devToolsCombineReducers(config, …)` must be given the **same** `DevToolsConfig` (or at least the same `name`/`instanceId`) so all three resolve the one `DevToolsSession` via the hub. Document this loudly; mismatched ids silently disable pipeline capture (degrade-not-break).
- **Composition.** `createStore(rootReducer, state, compose(devTools(cfg), devToolsMiddleware(cfg, …)))` where `rootReducer` may be `devToolsCombineReducers(cfg, …)`. `compose` is right-to-left, so `devTools` is the outer dispatch wrapper and runs `record()` **after** the inner chain (middleware + reducer) returns — exactly when `pendingTrace` is ready.
- **Degrade-not-break.** If the session is absent (no `devTools()`, or id mismatch), every wrapper calls the real middleware/reducer and returns its exact result with zero recording. Forgetting to swap combinators leaves the store fully working with only aggregate boundary timing.
- **Single-dispatch-thread assumption.** `PipelineRecorder`'s stack is touched only during synchronous dispatch on the store's dispatch thread (redux-kotlin serializes dispatch per store; threadsafe variants lock it). Nested re-dispatch from middleware pushes/pops the stack correctly (LIFO).

---

## File Structure

### New files in `redux-kotlin-devtools-core` (package `org.reduxkotlin.devtools`)
- `PipelineModel.kt` — `PipelineNodeKind`, `PipelineNode`, `PipelineStructure`, `PipelineNodeTrace`, `PipelineTrace`.
- `PipelineRecorder.kt` — internal per-session trace accumulator (nested stack).
- `Combinators.kt` — `NamedMiddleware`, `NamedReducer`, `named(...)` (two overloads), `devToolsMiddleware(...)`, `devToolsCombineReducers(...)`.

### Modified Plan 1 files
- `DevToolsEvent.kt` — add `PipelineRegistered`, `PipelineTraced`.
- `DevToolsSession.kt` — add `pipeline: PipelineRecorder`, `pendingTrace`, `registerPipeline()`, bundle trace into `Capture.Action` + emit `PipelineTraced` in `process()`.
- `DevTools.kt` — in the dispatch wrapper, read+clear `session` pending trace and pass it to `record(...)`.

---

## Task 1: Pipeline model types (`PipelineModel.kt`)

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/PipelineModel.kt`

- [ ] **Step 1: Create the file**

```kotlin
package org.reduxkotlin.devtools

/** The role a [PipelineNode] plays in the dispatch pipeline. */
public enum class PipelineNodeKind {
    /** The dispatch entry point (`dispatch(action)`). */
    ENTRY,

    /** A middleware in the chain. */
    MIDDLEWARE,

    /** The root reducer boundary. */
    REDUCER,

    /** A named reducer combined under the root (redux-kotlin combines whole-state reducers). */
    SLICE,
}

/**
 * One node in the static pipeline structure.
 *
 * @property id stable node id used to correlate a [PipelineNodeTrace] to this node.
 * @property label human-readable label (the middleware/reducer name).
 * @property kind the node's role.
 */
public data class PipelineNode(
    public val id: String,
    public val label: String,
    public val kind: PipelineNodeKind,
)

/**
 * The static structure of a store's dispatch pipeline, registered once. The UI draws this map and
 * lights nodes from a per-action [PipelineTrace].
 *
 * @property nodes ordered nodes: `ENTRY`, then `MIDDLEWARE`s in chain order, then `REDUCER`, then `SLICE`s.
 */
public data class PipelineStructure(public val nodes: List<PipelineNode>)

/**
 * Per-action timing/outcome for a single node.
 *
 * @property nodeId the [PipelineNode.id] this refers to.
 * @property durationNanos wall time spent in the node (monotonic clock).
 * @property forwarded for middleware: whether it called `next` (forwarded the action). `true` for reducers/slices.
 * @property changed for reducers/slices: whether the node produced a new state reference. `false` for middleware.
 */
public data class PipelineNodeTrace(
    public val nodeId: String,
    public val durationNanos: Long,
    public val forwarded: Boolean,
    public val changed: Boolean,
)

/**
 * The trace of one dispatched action through the pipeline.
 *
 * @property actionId the matching [DevToolsEvent.ActionRecorded.actionId]; correlates the trace to its action.
 * @property nodes per-node traces, in traversal order.
 */
public data class PipelineTrace(
    public val actionId: Int,
    public val nodes: List<PipelineNodeTrace>,
)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :redux-kotlin-devtools-core:compileKotlinJvm --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/PipelineModel.kt
git commit -m "feat(devtools-core): add pipeline model types"
```

---

## Task 2: Add pipeline events to `DevToolsEvent`

**Files:**
- Modify: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsEvent.kt`

- [ ] **Step 1: Add two cases to the sealed interface**

Add inside `DevToolsEvent`, after `ActionRecorded`:

```kotlin
    /**
     * The static pipeline structure for the session, emitted once when a pipeline combinator is wired.
     *
     * @property structure the ordered node map.
     */
    public data class PipelineRegistered(public val structure: PipelineStructure) : DevToolsEvent

    /**
     * A per-action pipeline trace. Emitted alongside the [ActionRecorded] with the same `actionId`.
     *
     * @property trace which nodes the action traversed, with timing and forwarded/changed flags.
     */
    public data class PipelineTraced(public val trace: PipelineTrace) : DevToolsEvent
```

- [ ] **Step 2: Compile + confirm Plan 1 tests still pass**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --console=plain`
Expected: PASS (all Plan 1 suites; the sealed addition does not break existing `when`/`is` checks because none exhaustively switch without `else`).

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsEvent.kt
git commit -m "feat(devtools-core): add pipeline events to DevToolsEvent"
```

---

## Task 3: `PipelineRecorder` (per-dispatch trace accumulator)

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/PipelineRecorder.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/PipelineRecorderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineRecorderTest {

    @Test
    fun begin_node_commit_produces_a_trace_in_order() {
        val r = PipelineRecorder()
        assertFalse(r.isActive)
        r.begin()
        assertTrue(r.isActive)
        r.node("mw_logger", durationNanos = 10, forwarded = true, changed = false)
        r.node("slice_todos", durationNanos = 20, forwarded = true, changed = true)
        val trace = r.commit(actionId = 7)
        assertEquals(7, trace?.actionId)
        assertEquals(listOf("mw_logger", "slice_todos"), trace?.nodes?.map { it.nodeId })
        assertFalse(r.isActive)
    }

    @Test
    fun nested_begin_commits_lifo_and_routes_nodes_to_top() {
        val r = PipelineRecorder()
        r.begin()                                   // outer (action A)
        r.node("mw_a", 1, true, false)
        r.begin()                                   // inner (re-dispatched action B)
        r.node("mw_b", 1, true, false)
        val inner = r.commit(actionId = 2)
        r.node("slice_a", 1, true, true)            // back on outer
        val outer = r.commit(actionId = 1)
        assertEquals(listOf("mw_b"), inner?.nodes?.map { it.nodeId })
        assertEquals(listOf("mw_a", "slice_a"), outer?.nodes?.map { it.nodeId })
    }

    @Test
    fun node_without_begin_is_ignored_and_commit_is_null() {
        val r = PipelineRecorder()
        r.node("orphan", 1, true, false)            // no active trace — dropped, no crash
        assertNull(r.commit(actionId = 1))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*PipelineRecorderTest*' --console=plain`
Expected: FAIL — `PipelineRecorder` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.reduxkotlin.devtools

/**
 * Accumulates the current dispatch's [PipelineTrace]. Touched only on the store's dispatch thread
 * during synchronous dispatch; a stack handles nested re-dispatch (middleware dispatching another
 * action) so each action's nodes land on its own trace (LIFO). Not thread-safe by design — dispatch
 * is serialized per store.
 */
internal class PipelineRecorder {
    private val stack = ArrayDeque<MutableList<PipelineNodeTrace>>()

    /** Whether a trace is currently open. */
    val isActive: Boolean get() = stack.isNotEmpty()

    /** Opens a new trace frame for a dispatch. */
    fun begin() {
        stack.addLast(ArrayList())
    }

    /** Appends a node to the current (top) trace frame. No-op if none is open. */
    fun node(nodeId: String, durationNanos: Long, forwarded: Boolean, changed: Boolean) {
        stack.lastOrNull()?.add(PipelineNodeTrace(nodeId, durationNanos, forwarded, changed))
    }

    /** Closes the current frame and returns its trace tagged with [actionId], or `null` if none open. */
    fun commit(actionId: Int): PipelineTrace? {
        val frame = stack.removeLastOrNull() ?: return null
        return PipelineTrace(actionId, frame.toList())
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*PipelineRecorderTest*' --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/PipelineRecorder.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/PipelineRecorderTest.kt
git commit -m "feat(devtools-core): add nested PipelineRecorder accumulator"
```

---

## Task 4: Wire pipeline into `DevToolsSession` + `devTools()`

The session gains the `PipelineRecorder`, a `pendingTrace` rendezvous, and `registerPipeline`. The `record(...)` path bundles any pending (id-less) trace into the capture; the consumer assigns the recorder id to both the action and the trace and emits both events. The `devTools()` enhancer hands the pending trace to `record(...)`.

**Files:**
- Modify: `DevToolsSession.kt`, `DevTools.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/PipelineSessionTest.kt`

- [ ] **Step 1: Extend `DevToolsSession`**

Add these members to `DevToolsSession` (keep the Plan 1 body; only additions/edits shown):

```kotlin
    /** Per-session pipeline trace accumulator, used by the combinators. Dispatch-thread confined. */
    internal val pipeline: PipelineRecorder = PipelineRecorder()

    // The id-less trace committed by the last-returning dispatch wrapper, awaiting pickup by record().
    // Dispatch-thread confined (set during the chain, read by record() right after it returns).
    private var pendingTrace: List<PipelineNodeTrace>? = null

    private var structureRegistered = false

    /** Registers the static pipeline [structure] once and emits [DevToolsEvent.PipelineRegistered]. */
    public fun registerPipeline(structure: PipelineStructure) {
        if (structureRegistered) return
        structureRegistered = true
        _events.tryEmit(DevToolsEvent.PipelineRegistered(structure))
    }

    /** Called by a combinator wrapper when it commits a completed trace frame for the current dispatch. */
    internal fun submitTrace(nodes: List<PipelineNodeTrace>) {
        pendingTrace = nodes
    }

    /** Removes and returns the pending trace nodes (called by the enhancer right after the chain returns). */
    internal fun takePendingTrace(): List<PipelineNodeTrace>? = pendingTrace.also { pendingTrace = null }
```

Change `record(...)` to accept the trace and carry it in the capture:

```kotlin
    /** Records a dispatched [action] + resulting [state], plus an optional pipeline [traceNodes]. */
    public fun record(action: Any, state: Any?, traceNodes: List<PipelineNodeTrace>? = null) {
        if (!shouldSend(action, denyRegex, allowRegex)) return
        val result = captures.trySend(Capture.Action(action, state, systemClock(), traceNodes))
        if (result.isFailure) {
            dropped++
            if (dropped == 1L || dropped % 100L == 0L) config.logger("devtools: dropped $dropped captures (dispatch outpacing recorder)")
        }
    }
```

Add the field to `Capture.Action`:

```kotlin
        data class Action(
            val action: Any,
            val state: Any?,
            val timestampMillis: Long,
            val traceNodes: List<PipelineNodeTrace>?,
        ) : Capture
```

In `process(...)`, after emitting `ActionRecorded`, emit the paired trace with the same id:

```kotlin
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
                capture.traceNodes?.let { nodes ->
                    _events.tryEmit(DevToolsEvent.PipelineTraced(PipelineTrace(recorded.actionId, nodes)))
                }
            }
```

- [ ] **Step 2: Amend the `devTools()` enhancer to pick up the pending trace**

In `DevTools.kt`, change the dispatch wrapper's record call to pass the pending trace (read on the same thread, right after the inner chain returns):

```kotlin
                store.dispatch = { action ->
                    val result = origDispatch(action)
                    @Suppress("TooGenericExceptionCaught") // devtools must never break the host store
                    try {
                        session.record(action, store.getState(), session.takePendingTrace())
                    } catch (t: Throwable) {
                        config.logger("devtools: record failed: ${t.message}")
                    }
                    result
                }
```

- [ ] **Step 3: Write the failing test (trace bundled with the action, same id)**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineSessionTest {

    private data class St(val n: Int)

    @Test
    fun submitted_trace_is_emitted_with_the_matching_action_id() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "p"), dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }

        session.init(St(0))
        // Simulate what a combinator wrapper + the enhancer do on the dispatch thread:
        session.submitTrace(listOf(PipelineNodeTrace("mw_logger", 5, forwarded = true, changed = false)))
        session.record("Inc", St(1), session.takePendingTrace())
        testScheduler.advanceUntilIdle()
        session.close(); job.cancel()

        val action = received.filterIsInstance<DevToolsEvent.ActionRecorded>().single()
        val traced = received.filterIsInstance<DevToolsEvent.PipelineTraced>().single()
        assertEquals(action.actionId, traced.trace.actionId)
        assertEquals("mw_logger", traced.trace.nodes.single().nodeId)
    }
}
```

- [ ] **Step 4: Run test to verify it fails, then passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*PipelineSessionTest*' --console=plain`
Expected: first FAIL (`submitTrace`/`takePendingTrace`/3-arg `record` unresolved) until Step 1–2 are applied, then PASS.

- [ ] **Step 5: Run full module (Plan 1 + pipeline)**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --console=plain`
Expected: PASS (all suites; the new `record` arg is defaulted so Plan 1 callers are unaffected).

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsSession.kt \
        redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevTools.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/PipelineSessionTest.kt
git commit -m "feat(devtools-core): bundle pipeline trace with its action (one id source)"
```

---

## Task 5: `devToolsMiddleware` + `named` (middleware)

A drop-in for `applyMiddleware` that owns the ordered list, registers the structure, and wraps each middleware to time it and detect whether it forwarded (`next` called). The outer wrapper brackets the dispatch with `pipeline.begin()/commit()` and submits the trace.

**Files:**
- Create: `redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/Combinators.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsMiddlewareTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.Store
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevToolsMiddlewareTest {

    private data class St(val n: Int = 0)
    private object Inc

    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @AfterTest fun cleanup() = DevToolsHub.reset()

    @Test
    fun wrapped_middleware_runs_in_order_and_returns_correct_state() {
        val log = mutableListOf<String>()
        val a: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any =
            { _ -> { next -> { action -> log.add("a"); next(action) } } }
        val b: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any =
            { _ -> { next -> { action -> log.add("b"); next(action) } } }

        val cfg = DevToolsConfig(name = "mw")
        val store = createStore(reducer, St(), compose(devTools(cfg), devToolsMiddleware(cfg, named("a", a), named("b", b))))
        store.dispatch(Inc)

        assertEquals(1, store.state.n)
        assertEquals(listOf("a", "b"), log)
    }

    @Test
    fun structure_and_trace_are_emitted_for_a_dispatch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cfg = DevToolsConfig(name = "mw2")
        // Pre-create the session on the test dispatcher so emissions are observable deterministically.
        val session = DevToolsHub.createSessionForTest(cfg, dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }

        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any =
            { _ -> { next -> { action -> next(action) } } }
        val store = createStore(reducer, St(), compose(devTools(cfg), devToolsMiddleware(cfg, named("logger", mw))))
        store.dispatch(Inc)
        testScheduler.advanceUntilIdle()
        job.cancel()

        val structure = received.filterIsInstance<DevToolsEvent.PipelineRegistered>().single().structure
        assertTrue(structure.nodes.any { it.kind == PipelineNodeKind.MIDDLEWARE && it.label == "logger" })
        val traced = received.filterIsInstance<DevToolsEvent.PipelineTraced>().last()
        assertTrue(traced.trace.nodes.any { it.nodeId.contains("logger") })
    }
}
```

> The second test needs the session bound to the test dispatcher (so `advanceUntilIdle` drains it) yet created via the hub (so the enhancer reuses it). Add a tiny test-only hub helper in Task 4's session file or here: `internal fun DevToolsHub.createSessionForTest(config, dispatcher)` that calls `DevToolsSession.create(config, dispatcher)` and registers it under the id. Place it in `commonTest` as an extension on the hub if its internals allow, otherwise add an `internal` overload `DevToolsHub.createSession(config, dispatcher)` guarded for test use. Keep production `createSession(config)` unchanged.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsMiddlewareTest*' --console=plain`
Expected: FAIL — `devToolsMiddleware` / `named` unresolved.

- [ ] **Step 3: Write the implementation in `Combinators.kt`**

```kotlin
package org.reduxkotlin.devtools

import org.reduxkotlin.Dispatcher
import org.reduxkotlin.Middleware
import org.reduxkotlin.Store
import org.reduxkotlin.StoreCreator
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.compose
import kotlin.time.TimeSource

/** A [Middleware] paired with a display [label] for the pipeline view. Create with [named]. */
public class NamedMiddleware<State> internal constructor(
    internal val label: String,
    internal val middleware: Middleware<State>,
)

/** Labels a [middleware] for the pipeline view. */
public fun <State> named(label: String, middleware: Middleware<State>): NamedMiddleware<State> =
    NamedMiddleware(label, middleware)

private fun mwNodeId(index: Int, label: String): String = "mw_${index}_$label"

/**
 * A drop-in replacement for `applyMiddleware` that captures the pipeline. It owns the ordered
 * middleware list, so it records the structure (`dispatch → [middleware…]`) once and, per dispatch,
 * times each middleware and records whether it forwarded the action. Pass the **same** [config] used
 * for [devTools] so both resolve the one session; if no session exists the wrappers are transparent.
 *
 * @param config the shared DevTools config (keys the session).
 * @param middlewares the labeled middleware chain, applied left-to-right.
 */
public fun <State> devToolsMiddleware(
    config: DevToolsConfig,
    vararg middlewares: NamedMiddleware<State>,
): StoreEnhancer<State> = { storeCreator: StoreCreator<State> ->
    { reducer, initialState, enhancer ->
        val store: Store<State> = storeCreator(reducer, initialState, enhancer)
        val session = DevToolsHub.session(config.instanceId ?: config.name)

        registerStructure(session, middlewares)

        val origDispatch = store.dispatch
        store.dispatch = {
            error(
                "Dispatching while constructing your middleware is not allowed. " +
                    "Other middleware would not be applied to this dispatch.",
            )
        }
        // Wrap each middleware: time it, record forwarded = did it call next.
        val chain = middlewares.mapIndexed { index, nm ->
            val real = nm.middleware(store)
            val nodeId = mwNodeId(index, nm.label)
            { next: Dispatcher ->
                val realNext = real(next)
                { action: Any ->
                    var forwarded = false
                    val wrappedNext: Dispatcher = { a -> forwarded = true; next(a) }
                    val realHandlerWithProbe = real(wrappedNext)
                    val mark = TimeSource.Monotonic.markNow()
                    val result = realHandlerWithProbe(action)
                    session?.pipeline?.node(nodeId, mark.elapsedNow().inWholeNanoseconds, forwarded, changed = false)
                    result
                }.also { _ -> realNext } // realNext referenced to keep construction parity; see note
            }
        }
        val composed = compose(chain)(origDispatch)
        store.dispatch = bracketed(session, composed)
        store
    }
}

private fun <State> registerStructure(session: DevToolsSession?, middlewares: Array<out NamedMiddleware<State>>) {
    if (session == null) return
    val nodes = ArrayList<PipelineNode>()
    nodes.add(PipelineNode("dispatch", "dispatch(action)", PipelineNodeKind.ENTRY))
    middlewares.forEachIndexed { i, nm -> nodes.add(PipelineNode(mwNodeId(i, nm.label), nm.label, PipelineNodeKind.MIDDLEWARE)) }
    nodes.add(PipelineNode("rootReducer", "rootReducer", PipelineNodeKind.REDUCER))
    session.registerPipeline(PipelineStructure(nodes))
}

// Brackets a dispatch: open a trace frame, run the chain (middleware + reducer append nodes), then
// commit and hand the id-less nodes to the session for pickup by the devTools() enhancer's record().
private fun bracketed(session: DevToolsSession?, composed: Dispatcher): Dispatcher = { action ->
    if (session == null) {
        composed(action)
    } else {
        session.pipeline.begin()
        try {
            composed(action)
        } finally {
            session.pipeline.commit(actionId = -1)?.let { session.submitTrace(it.nodes) }
        }
    }
}
```

> **Cleanup note (resolve at implementation):** the wrapper above double-constructs the real handler to probe `forwarded` (`real(next)` then `real(wrappedNext)`); keep only the `wrappedNext` construction — the `realNext`/`.also` line is parity scaffolding, delete it and call `real(wrappedNext)(action)` once. The intent: build the handler with a `next` that flips `forwarded` when invoked, time the call, record the node. `commit(actionId = -1)` uses a throwaway id because the real `actionId` is assigned later by the recorder; only `commit`'s node list is used (via `submitTrace`), so the `-1` is never surfaced. If cleaner, add a `PipelineRecorder.commitNodes(): List<PipelineNodeTrace>?` that returns just the frame's nodes without an id, and call that here.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsMiddlewareTest*' --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/Combinators.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsMiddlewareTest.kt
git commit -m "feat(devtools-core): devToolsMiddleware combinator with per-node timing"
```

---

## Task 6: `devToolsCombineReducers` + `named` (reducer)

A drop-in for `combineReducers` (whole-state reducers folded in order). It contributes the `SLICE` nodes to the structure and, per dispatch, times each named reducer and records `changed = output !== input`. If no middleware combinator opened a trace frame, the reducer self-brackets so pipeline still works reducer-only.

**Files:**
- Modify: `Combinators.kt`
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsCombineReducersTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.reduxkotlin.devtools

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevToolsCombineReducersTest {

    private data class St(val todos: Int = 0, val filter: String = "ALL")
    private object AddTodo
    private data class SetFilter(val f: String)

    private val todos: (St, Any) -> St = { s, a -> if (a is AddTodo) s.copy(todos = s.todos + 1) else s }
    private val filter: (St, Any) -> St = { s, a -> if (a is SetFilter) s.copy(filter = a.f) else s }

    @AfterTest fun cleanup() = DevToolsHub.reset()

    @Test
    fun combined_reducer_folds_named_reducers_in_order() {
        val root = devToolsCombineReducers(DevToolsConfig(name = "r"), named("todos", todos), named("filter", filter))
        val store = createStore(root, St(), devTools(DevToolsConfig(name = "r")))
        store.dispatch(AddTodo)
        store.dispatch(SetFilter("DONE"))
        assertEquals(St(todos = 1, filter = "DONE"), store.state)
    }

    @Test
    fun slice_changed_flag_reflects_which_reducer_produced_new_state() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cfg = DevToolsConfig(name = "r2")
        val session = DevToolsHub.createSessionForTest(cfg, dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }

        val root = devToolsCombineReducers(cfg, named("todos", todos), named("filter", filter))
        val store = createStore(root, St(), compose(devTools(cfg), devToolsMiddleware(cfg)))
        store.dispatch(AddTodo)                      // only "todos" changes
        testScheduler.advanceUntilIdle()
        job.cancel()

        val traced = received.filterIsInstance<DevToolsEvent.PipelineTraced>().last()
        val todosNode = traced.trace.nodes.first { it.nodeId.contains("todos") }
        val filterNode = traced.trace.nodes.first { it.nodeId.contains("filter") }
        assertTrue(todosNode.changed)
        assertTrue(!filterNode.changed)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsCombineReducersTest*' --console=plain`
Expected: FAIL — `devToolsCombineReducers` / reducer `named` unresolved.

- [ ] **Step 3: Add to `Combinators.kt`**

```kotlin
import org.reduxkotlin.Reducer

/** A [Reducer] paired with a display [label] for the pipeline view. Create with [named]. */
public class NamedReducer<State> internal constructor(
    internal val label: String,
    internal val reducer: Reducer<State>,
)

/** Labels a whole-state [reducer] for the pipeline view. */
public fun <State> named(label: String, reducer: Reducer<State>): NamedReducer<State> =
    NamedReducer(label, reducer)

private fun sliceNodeId(label: String): String = "slice_$label"

/**
 * A drop-in replacement for `combineReducers` that captures the reducer pipeline. redux-kotlin folds
 * whole-state reducers in order; this records each as a `SLICE` node, times it, and flags
 * `changed = output !== input`. Pass the **same** [config] used for [devTools]; without a session the
 * reducers run transparently. If [devToolsMiddleware] is not used, this self-brackets the trace.
 *
 * @param config the shared DevTools config (keys the session).
 * @param reducers the labeled whole-state reducers, folded left-to-right.
 */
public fun <State> devToolsCombineReducers(
    config: DevToolsConfig,
    vararg reducers: NamedReducer<State>,
): Reducer<State> {
    val id = config.instanceId ?: config.name
    return { state, action ->
        val session = DevToolsHub.session(id)
        if (session != null) {
            // Append SLICE structure lazily on first run (after the session exists).
            maybeRegisterSlices(session, reducers)
            val selfBracket = !session.pipeline.isActive
            if (selfBracket) session.pipeline.begin()
            val next = reducers.fold(state) { acc, nr ->
                val mark = TimeSource.Monotonic.markNow()
                val out = nr.reducer(acc, action)
                session.pipeline.node(
                    sliceNodeId(nr.label),
                    mark.elapsedNow().inWholeNanoseconds,
                    forwarded = true,
                    changed = out !== acc,
                )
                out
            }
            if (selfBracket) session.pipeline.commit(actionId = -1)?.let { session.submitTrace(it.nodes) }
            next
        } else {
            reducers.fold(state) { acc, nr -> nr.reducer(acc, action) }
        }
    }
}

private fun <State> maybeRegisterSlices(session: DevToolsSession, reducers: Array<out NamedReducer<State>>) {
    val nodes = ArrayList<PipelineNode>()
    nodes.add(PipelineNode("dispatch", "dispatch(action)", PipelineNodeKind.ENTRY))
    nodes.add(PipelineNode("rootReducer", "rootReducer", PipelineNodeKind.REDUCER))
    reducers.forEach { nodes.add(PipelineNode(sliceNodeId(it.label), it.label, PipelineNodeKind.SLICE)) }
    session.registerPipeline(PipelineStructure(nodes))
}
```

> **Structure-merge note (resolve at implementation):** both `devToolsMiddleware` (Task 5) and `devToolsCombineReducers` call `session.registerPipeline(...)`, but Task 4 made `registerPipeline` register-once (first writer wins). That means whichever runs first determines the emitted structure, and the other's nodes are lost. Fix one of two ways: (a) make `registerPipeline` **merge** — accumulate nodes by `id`, keep order ENTRY → MIDDLEWARE(s) → REDUCER → SLICE(s), and emit/replace the merged `PipelineRegistered` each time it grows; or (b) have the middleware combinator pass the slice labels through (not feasible — it doesn't know them). Choose (a): change `registerPipeline` to merge-and-re-emit, dedupe by `PipelineNode.id`, and sort by `kind` ordinal then insertion order. Update the Task 4 `registerPipeline` accordingly and add a test that a store using **both** combinators emits one structure containing the entry, middleware, reducer, and slice nodes.

- [ ] **Step 4: Apply the `registerPipeline` merge fix (per the note) + test it**

Replace `registerPipeline` in `DevToolsSession.kt` with a merging version:

```kotlin
    private val structureNodes = LinkedHashMap<String, PipelineNode>()

    /** Registers/merges static pipeline nodes and (re)emits [DevToolsEvent.PipelineRegistered]. */
    public fun registerPipeline(structure: PipelineStructure) {
        var grew = false
        for (node in structure.nodes) if (structureNodes.put(node.id, node) == null) grew = true
        if (!grew) return
        val ordered = structureNodes.values.sortedBy { it.kind.ordinal }
        _events.tryEmit(DevToolsEvent.PipelineRegistered(PipelineStructure(ordered)))
    }
```

(Remove the `structureRegistered` boolean from Task 4.) Add to `DevToolsMiddlewareTest` or a new test:

```kotlin
    @Test
    fun both_combinators_yield_one_structure_with_all_node_kinds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cfg = DevToolsConfig(name = "both")
        val session = DevToolsHub.createSessionForTest(cfg, dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }

        val mw: (org.reduxkotlin.Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        val root = devToolsCombineReducers(cfg, named("todos", todos), named("filter", filter))
        val store = createStore(root, St(), compose(devTools(cfg), devToolsMiddleware(cfg, named("logger", mw))))
        store.dispatch(AddTodo)
        testScheduler.advanceUntilIdle(); job.cancel()

        val kinds = received.filterIsInstance<DevToolsEvent.PipelineRegistered>().last().structure.nodes.map { it.kind }.toSet()
        assertEquals(setOf(PipelineNodeKind.ENTRY, PipelineNodeKind.MIDDLEWARE, PipelineNodeKind.REDUCER, PipelineNodeKind.SLICE), kinds)
    }
```

(Place this test in a file that defines `St`/`todos`/`filter`/`AddTodo` — fold it into `DevToolsCombineReducersTest` where those are declared.)

- [ ] **Step 5: Run reducer + merge tests**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*DevToolsCombineReducersTest*' --console=plain`
Expected: PASS.

- [ ] **Step 6: Full module green**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --console=plain`
Expected: PASS (all suites).

- [ ] **Step 7: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/Combinators.kt \
        redux-kotlin-devtools-core/src/commonMain/kotlin/org/reduxkotlin/devtools/DevToolsSession.kt \
        redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/DevToolsCombineReducersTest.kt
git commit -m "feat(devtools-core): devToolsCombineReducers combinator + merged pipeline structure"
```

---

## Task 7: Graceful-degradation test (combinators forgotten / id mismatch)

**Files:**
- Test: `redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/PipelineDegradeTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package org.reduxkotlin.devtools

import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import org.reduxkotlin.Store
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineDegradeTest {

    private data class St(val n: Int = 0)
    private object Inc
    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @AfterTest fun cleanup() = DevToolsHub.reset()

    @Test
    fun plain_applyMiddleware_with_devTools_still_works_no_pipeline() {
        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        val store = createStore(reducer, St(), compose(devTools(DevToolsConfig(name = "d")), applyMiddleware(mw)))
        store.dispatch(Inc)
        assertEquals(1, store.state.n)               // store unaffected; pipeline simply has no trace
    }

    @Test
    fun combinator_with_mismatched_config_id_is_transparent() {
        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        // devTools uses "x"; devToolsMiddleware uses "y" — no session found, wrappers transparent.
        val store = createStore(
            reducer, St(),
            compose(devTools(DevToolsConfig(name = "x")), devToolsMiddleware(DevToolsConfig(name = "y"), named("m", mw))),
        )
        store.dispatch(Inc)
        assertEquals(1, store.state.n)
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :redux-kotlin-devtools-core:jvmTest --tests '*PipelineDegradeTest*' --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-devtools-core/src/commonTest/kotlin/org/reduxkotlin/devtools/PipelineDegradeTest.kt
git commit -m "test(devtools-core): pipeline degrades safely when combinators omitted or misconfigured"
```

---

## Task 8: API dump + full gate

- [ ] **Step 1: Regenerate the core API dump (new public symbols added)**

Run: `./gradlew :redux-kotlin-devtools-core:apiDump --console=plain`
Expected: `api/*.api` updated to include `devToolsMiddleware`, `devToolsCombineReducers`, `named` (both), `NamedMiddleware`, `NamedReducer`, `PipelineNode*`, `PipelineStructure`, `PipelineTrace`, `DevToolsEvent.PipelineRegistered`, `DevToolsEvent.PipelineTraced`, `DevToolsSession.{pipeline(internal — not in dump),registerPipeline}`.

- [ ] **Step 2: Full build (detekt + apiCheck + tests, all targets the host runs)**

Run: `./gradlew :redux-kotlin-devtools-core:build --console=plain`
Expected: BUILD SUCCESSFUL. (KDoc on every new public symbol; `explicitApi` satisfied.) If `iosSimulatorArm64Test` fails on an Xcode SDK error, that's environmental — `-x iosSimulatorArm64Test` to confirm the rest.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-devtools-core/api/
git commit -m "build(devtools-core): update API dump for pipeline combinators"
```

---

## Self-Review (against spec)

**Spec "Pipeline capture" coverage:**
- "devtools-owned drop-in combinators own the ordered list" → Tasks 5–6. ✔
- "`devToolsMiddleware(vararg NamedMiddleware)` … each middleware wrapped to record enter/exit + duration; wrapper returns the real result untouched; `named(...)` label; fallback labels" → Task 5 (transparent wrapper, `forwarded` probe, `mwNodeId` fallback embeds index). ✔ *(Fallback to a JVM reflection name when unlabeled is deferred — all middleware are passed via `named(...)`; a bare-middleware overload can be added later.)*
- "`devToolsCombineReducers(...)` captures slice names + per-slice timing + which slices changed" → Task 6 (`changed = out !== acc`; whole-state reducer model confirmed against `CombineReducers.kt`). ✔
- "Timing: `kotlin.time.TimeSource.Monotonic`" → Tasks 5–6. ✔
- "`PipelineModel`: static structure registered once; per-action trace = list of (nodeId, duration, forwarded/changed)" → Tasks 1, 4. ✔ (structure merges both combinators — Task 6 fix).
- "UI draws the static map and lights nodes from the selected action's trace" → events carry structure + per-action trace correlated by `actionId`; consumed by Plan 3. ✔
- "Graceful degradation: plain `applyMiddleware`/`combineReducers` → no per-node detail, never breaks" → Task 7. ✔
- "Open detail: combine may key by type vs string — verify" → resolved: redux-kotlin `combineReducers` folds **whole-state** reducers (no keys); `devToolsCombineReducers` introduces explicit `named(...)` labels and reference-equality change detection. ✔

**Placeholder scan:** two **cleanup notes** (the middleware `forwarded`-probe double-construction; `commit(actionId = -1)` throwaway id) and one **structure-merge note** are present — each states the concrete fix and the executor applies it in the same task (Task 5 note → simplify to one `real(wrappedNext)`; Task 6 note → merging `registerPipeline`, which Task 6 Step 4 actually implements + tests). The `createSessionForTest` helper is specified (test-only hub overload). No bare TBDs.

**Type consistency:** `named` overloaded for `Middleware`/`Reducer` (distinct types); `NamedMiddleware`/`NamedReducer`; `devToolsMiddleware(config, vararg NamedMiddleware)`; `devToolsCombineReducers(config, vararg NamedReducer): Reducer`; `PipelineRecorder.{begin,node,commit,isActive}`; `DevToolsSession.{pipeline,registerPipeline,submitTrace,takePendingTrace,record(...,traceNodes)}`; `DevToolsEvent.{PipelineRegistered,PipelineTraced}` used consistently across Tasks 1–7.

**Threading:** `PipelineRecorder` + `pendingTrace` are dispatch-thread-confined (dispatch serialized per store); the trace is handed to the consumer coroutine bundled in one `Capture`, which assigns the single recorder id — no cross-thread mutable sharing, traces correlate to actions by one id even under capture drops. `registerPipeline` emits via the thread-safe `tryEmit`.
