# redux-kotlin-routing (Phase 1, Mechanism A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a new opt-in companion module `redux-kotlin-routing` that provides a runtime DSL for `(model, action)` routed dispatch over `ModelState`, replacing the `when(action){}` cascade with exact-leaf-class routing.

**Architecture:** A `createModelStore { … }` builder collects per-model initial instances and handlers into a routing table keyed by the *exact* action `KClass`. It emits a plain `Reducer<ModelState>` that drops into the existing `createStore`. Dispatch looks up only the handlers registered for `action::class`, folds them over a transient working view (later handlers see earlier writes), and commits with at most one `ModelState` map copy — preserving `===` identity of untouched models so the granular layer's fast-path is unaffected.

**Tech Stack:** Kotlin Multiplatform (all targets per `convention.library-mpp-loved`); `kotlin-test`; builds on `redux-kotlin` + `redux-kotlin-multimodel`; detekt 2.0.0-alpha.3 with `explicitApi()` + KDoc gate; binary-compatibility-validator (`apiDump`/`apiCheck`).

**Spec:** `docs/superpowers/specs/2026-05-28-reducer-middleware-routing-design.md` (Phase 1 only; KSP codegen, compiler plugin, and the `-effects` module are out of scope).

**Public API shape (target):**

```kotlin
val store: Store<ModelState> = createModelStore(
    devChecks = false,            // opt-in immutability assertion
    onWrite = null,               // opt-in !==-gated write observer
) {
    model(UserModel()) {                                   // structural init: instance supplied here
        on<LoggedIn>  { s, a -> s.copy(user = a.user) }    // single-model, pure (M,A)->M
        on<LoggedOut> { s, _ -> s.copy(user = null) }
    }
    model(CartModel()) {
        on<AddItem> { s, a -> s.copy(items = s.items + a.item) }
    }
    onAction<Checkout> { reads, a ->                       // multi-model, returns a write-set
        val cart = reads.get<CartModel>()
        writeSet {
            set(cart.copy(closed = true))
            set(reads.get<UserModel>().copy(lastOrder = cart.id))
        }
    }
    onBroadcast<Logout> { model, _ ->                      // runs for every installed model
        if (model is Clearable) model.cleared() else model
    }
    install(SomeFeatureModule)                             // modular composition; order fixed here
}
```

---

## File Structure

**New module `redux-kotlin-routing/`:**
- `build.gradle.kts` — module config (mirror `redux-kotlin-registry`); deps `api(project(":redux-kotlin"))` + `api(project(":redux-kotlin-multimodel"))`.
- `src/commonMain/kotlin/org/reduxkotlin/routing/WriteSet.kt` — `Reads` read-view, `WriteSet`, `WriteSetBuilder`, `writeSet { }`.
- `src/commonMain/kotlin/org/reduxkotlin/routing/RoutingDsl.kt` — `RoutingBuilder`, `ModelHandlerScope<M>`, `ReduxModule`, `install`, registration functions (`model`/`on`/`onAction`/`onBroadcast`).
- `src/commonMain/kotlin/org/reduxkotlin/routing/RoutedReducer.kt` — internal `WorkingState`, `Cell`, broadcast materialization, the `Reducer<ModelState>` fold.
- `src/commonMain/kotlin/org/reduxkotlin/routing/CreateModelStore.kt` — `OnWrite` typealias, `createModelStore { }` entry point.
- `src/commonTest/kotlin/org/reduxkotlin/routing/*.kt` — correctness tests + shared test fixtures.
- `src/jvmTest/kotlin/org/reduxkotlin/routing/concurrency/*.kt` — threadsafe routed-dispatch stress.
- `api/redux-kotlin-routing.klib.api` + `api/jvm/redux-kotlin-routing.api` — generated baselines.
- `README.md`.

**Modified existing module `redux-kotlin-multimodel/`:**
- `src/commonMain/kotlin/org/reduxkotlin/multimodel/ModelState.kt` — add public `withAll(changes)` (single batch copy).
- `src/commonTest/kotlin/org/reduxkotlin/multimodel/ModelStateTest.kt` — add/extend tests for `withAll`.
- `api/*.api` — regenerated.

**Modified root:**
- `settings.gradle.kts` — add `:redux-kotlin-routing` to `include(...)`.

---

## Conventions (apply to every task)

- **Package:** `org.reduxkotlin.routing`.
- **`explicitApi()` is on:** every `public` declaration needs an explicit `public` modifier **and** a KDoc comment, including nested classes and their public properties. The code blocks below include the required KDoc — keep it.
- **Detekt auto-correct:** the pre-commit hook runs `detektAll --auto-correct`; formatting (trailing commas, wrapping, brace style) is fixed in place. If a commit rewrites files, re-stage and commit again. Never use `--no-verify`.
- **Commit style:** Conventional Commits (`feat`/`test`/`build`/`docs`), `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` footer is added automatically by the harness convention; here, plain messages are fine.
- **Run a single module's common tests:** `./gradlew :redux-kotlin-routing:jvmTest` (fast inner loop; `allTests` runs every host target).

---

### Task 0: Scaffold the module

**Files:**
- Modify: `settings.gradle.kts:16-24`
- Create: `redux-kotlin-routing/build.gradle.kts`
- Create: `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/.gitkeep`
- Create: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/.gitkeep`

- [ ] **Step 1: Register the module in settings**

Modify `settings.gradle.kts`, adding the module to the `include(...)` list right after `:redux-kotlin-multimodel`:

```kotlin
include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
    ":redux-kotlin-registry",
    ":redux-kotlin-granular",
    ":redux-kotlin-multimodel",
    ":redux-kotlin-routing",
    ":redux-kotlin-compose",
    ":redux-kotlin-multimodel-granular",
    ":redux-kotlin-compose-multimodel",
    ":examples:counter:common",
    ":examples:counter:android",
    ":examples:todos:common",
    ":examples:todos:android",
)
```

- [ ] **Step 2: Create the build file**

Create `redux-kotlin-routing/build.gradle.kts` (mirrors `redux-kotlin-registry`, but depends on multimodel and needs no atomicfu — the routed reducer is pure):

```kotlin
plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
}

val hasAndroidSdk: Boolean = run {
    val localProps = rootProject.file("local.properties")
    val hasSdkInLocalProperties = localProps.exists() && localProps.readText().lineSequence().any {
        it.trim().startsWith("sdk.dir=") && it.substringAfter("sdk.dir=").isNotBlank()
    }
    val hasSdkInEnv =
        !System.getenv("ANDROID_HOME").isNullOrBlank() ||
            !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
    hasSdkInLocalProperties || hasSdkInEnv
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.routing"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                api(project(":redux-kotlin-multimodel"))
            }
        }
    }
}
```

- [ ] **Step 3: Create source directories with placeholders**

Create empty `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/.gitkeep` and `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/.gitkeep` so the source sets exist.

- [ ] **Step 4: Verify the module configures and compiles (empty)**

Run: `./gradlew :redux-kotlin-routing:compileKotlinJvm`
Expected: `BUILD SUCCESSFUL` (nothing to compile yet, but the project must resolve).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts redux-kotlin-routing/build.gradle.kts redux-kotlin-routing/src
git commit -m "build(routing): scaffold redux-kotlin-routing module"
```

---

### Task 1: Add `ModelState.withAll` to multimodel (batch single-copy write)

The routing module cannot see `ModelState.models` (it is `internal`), and the dispatch fold must apply N model writes with **one** map copy. Add a public batch-write that mirrors `with`'s key-set check.

**Files:**
- Modify: `redux-kotlin-multimodel/src/commonMain/kotlin/org/reduxkotlin/multimodel/ModelState.kt`
- Test: `redux-kotlin-multimodel/src/commonTest/kotlin/org/reduxkotlin/multimodel/ModelStateTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `ModelStateTest.kt` (create the file with this content if it does not exist; if it exists, add these functions inside the existing test class — check the file first). If creating fresh:

```kotlin
package org.reduxkotlin.multimodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private data class A(val n: Int)
private data class B(val s: String)
private data class Unregistered(val x: Int)

class ModelStateWithAllTest {

    @Test
    fun withAll_replaces_multiple_models_in_one_call() {
        val state = ModelState.of(A(1), B("x"))
        val next = state.withAll(mapOf(A::class to A(2), B::class to B("y")))
        assertEquals(A(2), next.get<A>())
        assertEquals(B("y"), next.get<B>())
    }

    @Test
    fun withAll_with_empty_map_returns_same_instance() {
        val state = ModelState.of(A(1))
        assertSame(state, state.withAll(emptyMap()))
    }

    @Test
    fun withAll_rejects_unregistered_model_class() {
        val state = ModelState.of(A(1))
        assertFailsWith<IllegalStateException> {
            state.withAll(mapOf(Unregistered::class to Unregistered(0)))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-multimodel:jvmTest --tests "org.reduxkotlin.multimodel.ModelStateWithAllTest"`
Expected: FAIL — `withAll` unresolved reference.

- [ ] **Step 3: Implement `withAll`**

In `ModelState.kt`, add this method inside the `ModelState` class, immediately after the non-reified `with(modelClass, model)` method:

```kotlin
    /**
     * Returns a new [ModelState] with every slot named in [changes]
     * replaced, in a single map copy. Other models are shared with the
     * receiver. Returns the receiver unchanged when [changes] is empty.
     *
     * This is the batch form of [with]; it exists so a routed reducer
     * can apply several model writes from one dispatch with exactly one
     * allocation, preserving the `===` identity of untouched slots.
     *
     * @throws IllegalStateException if any key in [changes] was not
     *   declared at construction; the key set is fixed by
     *   [Companion.of].
     */
    public fun withAll(changes: Map<KClass<*>, Any>): ModelState {
        if (changes.isEmpty()) return this
        for (klass in changes.keys) {
            check(klass in models) {
                "Cannot replace model ${klass.simpleName ?: klass} that wasn't declared at construction. " +
                    "ModelState's key set is fixed by ModelState.of(...)."
            }
        }
        return ModelState(models + changes)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-multimodel:jvmTest --tests "org.reduxkotlin.multimodel.ModelStateWithAllTest"`
Expected: PASS.

- [ ] **Step 5: Regenerate the multimodel API dump**

Run: `./gradlew :redux-kotlin-multimodel:apiDump`
Expected: `BUILD SUCCESSFUL`; `redux-kotlin-multimodel/api/*.api` now lists `withAll`.

- [ ] **Step 6: Commit**

```bash
git add redux-kotlin-multimodel/src redux-kotlin-multimodel/api
git commit -m "feat(multimodel): add ModelState.withAll for single-copy batch writes"
```

---

### Task 2: `Reads` + `WriteSet` + `writeSet { }`

**Files:**
- Create: `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/WriteSet.kt`
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/WriteSetTest.kt`

- [ ] **Step 1: Write the failing test**

Create `WriteSetTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals

private data class Foo(val n: Int)
private data class Bar(val s: String)

class WriteSetTest {

    @Test
    fun writeSet_collects_models_keyed_by_class() {
        val ws = writeSet {
            set(Foo(1))
            set(Bar("x"))
        }
        assertEquals(2, ws.changes.size)
        assertEquals(Foo(1), ws.changes[Foo::class])
        assertEquals(Bar("x"), ws.changes[Bar::class])
    }

    @Test
    fun writeSet_last_set_for_a_class_wins() {
        val ws = writeSet {
            set(Foo(1))
            set(Foo(2))
        }
        assertEquals(1, ws.changes.size)
        assertEquals(Foo(2), ws.changes[Foo::class])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.WriteSetTest"`
Expected: FAIL — unresolved references `writeSet`, `set`, `changes`.

- [ ] **Step 3: Implement `WriteSet.kt`**

```kotlin
package org.reduxkotlin.routing

import kotlin.reflect.KClass

/**
 * A read-only view of model state passed to multi-model handlers
 * during a dispatch. Reads reflect writes already produced by earlier
 * handlers in the same dispatch (fold semantics), so a later handler
 * observes prior handlers' results.
 */
public interface Reads {
    /**
     * Returns the current working instance of [modelClass] for this
     * dispatch.
     *
     * @throws IllegalStateException if [modelClass] was not registered
     *   in the [createModelStore] block.
     */
    public fun <M : Any> get(modelClass: KClass<M>): M
}

/**
 * Reified convenience for [Reads.get].
 */
public inline fun <reified M : Any> Reads.get(): M = get(M::class)

/**
 * The set of model replacements returned by a multi-model handler
 * ([onAction]). Each entry replaces one model slot; a model not named
 * here is left untouched. Build instances with [writeSet].
 */
public class WriteSet @PublishedApi internal constructor(
    /** The replacements, keyed by concrete model class. */
    @PublishedApi internal val changes: Map<KClass<*>, Any>,
)

/**
 * Builder for [WriteSet]; the receiver of the [writeSet] lambda.
 */
public class WriteSetBuilder @PublishedApi internal constructor() {

    @PublishedApi
    internal val changes: MutableMap<KClass<*>, Any> = LinkedHashMap()

    /**
     * Stages [model] as the new instance for model type [M]. A later
     * [set] for the same type overrides an earlier one.
     */
    public inline fun <reified M : Any> set(model: M): Unit = set(M::class, model)

    /**
     * Non-reified overload of [set] for callers holding a [KClass].
     */
    public fun <M : Any> set(modelClass: KClass<M>, model: M) {
        changes[modelClass] = model
    }
}

/**
 * Builds a [WriteSet] from a sequence of [WriteSetBuilder.set] calls.
 */
public fun writeSet(block: WriteSetBuilder.() -> Unit): WriteSet {
    val builder = WriteSetBuilder()
    builder.block()
    return WriteSet(builder.changes.toMap())
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.WriteSetTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "feat(routing): add Reads view and WriteSet builder"
```

---

### Task 3: Internal routing runtime — `WorkingState`, `Cell`, routed reducer

This task adds the internal engine the DSL will populate. It has no public surface yet; it is exercised through `CreateModelStore`/`RoutingDsl` in Task 4. To keep TDD honest, write the engine and a white-box test that constructs cells directly.

**Files:**
- Create: `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/RoutedReducer.kt`
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/RoutedReducerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `RoutedReducerTest.kt`:

```kotlin
package org.reduxkotlin.routing

import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private data class Counter(val n: Int)
private data class Flag(val on: Boolean)
private object Inc
private object Toggle
private object Unhandled

class RoutedReducerTest {

    private fun table(): Map<Any, List<Cell>> = mapOf(
        Inc::class to listOf(
            Cell("counter") { working, _ ->
                val c = working.get(Counter::class)
                mapOf(Counter::class to c.copy(n = c.n + 1))
            },
        ),
    )

    @Test
    fun dispatch_runs_only_the_matching_cell() {
        val reducer = routedReducer(table(), broadcasts = emptyList(), devChecks = false, onWrite = null)
        val s0 = ModelState.of(Counter(0), Flag(false))
        val s1 = reducer(s0, Inc)
        assertEquals(1, s1.get<Counter>().n)
        assertEquals(false, s1.get<Flag>().on)
    }

    @Test
    fun unhandled_action_returns_same_instance() {
        val reducer = routedReducer(table(), broadcasts = emptyList(), devChecks = false, onWrite = null)
        val s0 = ModelState.of(Counter(0), Flag(false))
        assertSame(s0, reducer(s0, Unhandled))
    }

    @Test
    fun handled_action_with_no_change_returns_same_instance() {
        val noop = mapOf<Any, List<Cell>>(
            Toggle::class to listOf(Cell("flag") { _, _ -> emptyMap() }),
        )
        val reducer = routedReducer(noop, broadcasts = emptyList(), devChecks = false, onWrite = null)
        val s0 = ModelState.of(Flag(false))
        assertSame(s0, reducer(s0, Toggle))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.RoutedReducerTest"`
Expected: FAIL — unresolved `Cell`, `routedReducer`, `WorkingState.get`.

- [ ] **Step 3: Implement `RoutedReducer.kt`**

```kotlin
package org.reduxkotlin.routing

import org.reduxkotlin.Reducer
import org.reduxkotlin.multimodel.ModelState
import kotlin.reflect.KClass

/**
 * A single registered handler keyed under one action class. Given the
 * working state and the dispatched action, it returns the model
 * replacements it wants to make (possibly empty). The change gate and
 * the [OnWrite] notification are applied centrally by [routedReducer].
 */
internal class Cell(
    val source: String,
    val handler: (working: WorkingState, action: Any) -> Map<KClass<*>, Any>,
)

/**
 * A broadcast handler that runs once per installed model for its action
 * class. Materialized into per-model [Cell]s at reducer-build time so
 * it covers every model regardless of declaration order.
 */
internal class Broadcast(
    val actionClass: KClass<*>,
    val perModel: (model: Any, action: Any) -> Any,
)

/**
 * Transient per-dispatch working view over a base [ModelState]. Reads
 * return staged writes first (so later handlers see earlier writes),
 * falling back to the committed base. The staging map is allocated
 * lazily on the first write, so a dispatch that changes nothing makes
 * no map copy.
 */
internal class WorkingState(private val base: ModelState) : Reads {

    var staged: MutableMap<KClass<*>, Any>? = null
        private set

    override fun <M : Any> get(modelClass: KClass<M>): M {
        @Suppress("UNCHECKED_CAST")
        return peek(modelClass) as M
    }

    fun peek(modelClass: KClass<*>): Any {
        staged?.get(modelClass)?.let { return it }
        return base.get(modelClass)
    }

    fun stage(modelClass: KClass<*>, model: Any) {
        val current = staged ?: LinkedHashMap<KClass<*>, Any>().also { staged = it }
        current[modelClass] = model
    }
}

/**
 * Builds the routed [Reducer] over [ModelState] from a finished routing
 * table plus broadcast registrations.
 */
internal fun routedReducer(
    table: Map<Any, List<Cell>>,
    broadcasts: List<Broadcast>,
    devChecks: Boolean,
    onWrite: OnWrite?,
    modelClasses: List<KClass<*>> = emptyList(),
): Reducer<ModelState> {
    // Materialize broadcasts into the table as per-model cells.
    val full: Map<Any, List<Cell>> = if (broadcasts.isEmpty()) {
        table
    } else {
        val merged = LinkedHashMap<Any, MutableList<Cell>>()
        for ((key, cells) in table) merged[key] = cells.toMutableList()
        for (broadcast in broadcasts) {
            val cells = merged.getOrPut(broadcast.actionClass) { mutableListOf() }
            for (modelClass in modelClasses) {
                cells += Cell("broadcast:${modelClass.simpleName}") { working, action ->
                    val current = working.peek(modelClass)
                    val next = broadcast.perModel(current, action)
                    if (next !== current) mapOf(modelClass to next) else emptyMap()
                }
            }
        }
        merged
    }

    return reducer@{ state, action ->
        val cells = full[action::class] ?: return@reducer state
        val working = WorkingState(state)
        for (cell in cells) {
            val writes = cell.handler(working, action)
            for ((modelClass, next) in writes) {
                val prev = working.peek(modelClass)
                if (next !== prev) {
                    check(!(devChecks && next == prev)) {
                        "Handler '${cell.source}' returned a new but structurally-equal instance for " +
                            "${modelClass.simpleName ?: modelClass}. This allocates without changing state and " +
                            "would fire subscribers spuriously. Return the same instance when nothing changed."
                    }
                    onWrite?.invoke(action, modelClass, prev, next, cell.source)
                    working.stage(modelClass, next)
                }
            }
        }
        val staged = working.staged ?: return@reducer state
        state.withAll(staged)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.RoutedReducerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "feat(routing): add internal routed-reducer engine"
```

---

### Task 4: The DSL — `RoutingBuilder`, `model`/`on`, and `createModelStore`

Wires the public builder to the engine and gives the first end-to-end single-model dispatch.

**Files:**
- Create: `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/RoutingDsl.kt`
- Create: `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/CreateModelStore.kt`
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/CreateModelStoreTest.kt`
- Test fixtures: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/Fixtures.kt`

- [ ] **Step 1: Write shared test fixtures**

Create `Fixtures.kt` (shared sealed/data action + model types used across tests):

```kotlin
package org.reduxkotlin.routing

/** A user model for tests. */
internal data class UserModel(val user: String? = null, val lastOrder: Int = -1)

/** A cart model for tests. */
internal data class CartModel(val items: List<String> = emptyList(), val id: Int = 0, val closed: Boolean = false)

/** Actions used across routing tests. */
internal data class LoggedIn(val user: String)
internal object LoggedOut
internal data class AddItem(val item: String)
internal object Checkout
internal object ResetAll
internal object NeverHandled
```

- [ ] **Step 2: Write the failing test**

Create `CreateModelStoreTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CreateModelStoreTest {

    private fun store() = createModelStore {
        model(UserModel()) {
            on<LoggedIn> { s, a -> s.copy(user = a.user) }
            on<LoggedOut> { s, _ -> s.copy(user = null) }
        }
        model(CartModel()) {
            on<AddItem> { s, a -> s.copy(items = s.items + a.item) }
        }
    }

    @Test
    fun single_model_handler_updates_its_model() {
        val s = store()
        s.dispatch(LoggedIn("ann"))
        assertEquals("ann", s.state.get<UserModel>().user)
    }

    @Test
    fun action_routes_only_to_its_model() {
        val s = store()
        val before = s.state.get<CartModel>()
        s.dispatch(LoggedIn("ann"))
        // Cart untouched -> same instance preserved (=== identity).
        assertSame(before, s.state.get<CartModel>())
    }

    @Test
    fun unhandled_action_leaves_state_identity_unchanged() {
        val s = store()
        val before = s.state
        s.dispatch(NeverHandled)
        assertSame(before, s.state)
    }

    @Test
    fun logged_out_clears_user() {
        val s = store()
        s.dispatch(LoggedIn("ann"))
        s.dispatch(LoggedOut)
        assertNull(s.state.get<UserModel>().user)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.CreateModelStoreTest"`
Expected: FAIL — unresolved `createModelStore`, `model`, `on`.

- [ ] **Step 4: Implement `RoutingDsl.kt`**

```kotlin
package org.reduxkotlin.routing

import org.reduxkotlin.Reducer
import org.reduxkotlin.multimodel.ModelState
import kotlin.reflect.KClass

/**
 * Collects model initial instances and action handlers, then produces
 * the initial [ModelState] and the routed [Reducer]. This is the
 * receiver of the [createModelStore] lambda and of [ReduxModule]
 * contributions. Registration order across the whole block (including
 * [install]ed modules) fixes the dispatch order for any action handled
 * by more than one handler.
 */
public class RoutingBuilder @PublishedApi internal constructor() {

    @PublishedApi
    internal val initials: MutableList<Any> = mutableListOf()

    @PublishedApi
    internal val modelClasses: MutableList<KClass<*>> = mutableListOf()

    @PublishedApi
    internal val table: MutableMap<Any, MutableList<Cell>> = LinkedHashMap()

    @PublishedApi
    internal val broadcasts: MutableList<Broadcast> = mutableListOf()

    /**
     * Registers a model of type [M] with its [initial] instance and its
     * single-model handlers. The initial instance is the sole source of
     * that model's starting value — there is no INIT action fan-out.
     *
     * @throws IllegalArgumentException if [M] is registered more than
     *   once.
     */
    public inline fun <reified M : Any> model(initial: M, block: ModelHandlerScope<M>.() -> Unit) {
        registerModel(M::class, initial)
        ModelHandlerScope(M::class, this).block()
    }

    /**
     * Registers a multi-model handler for action [A]. The handler reads
     * any models via [Reads] and returns a [WriteSet] of replacements.
     */
    public inline fun <reified A : Any> onAction(noinline handler: (reads: Reads, action: A) -> WriteSet) {
        addCell(A::class, "onAction:${A::class.simpleName}") { working, action ->
            @Suppress("UNCHECKED_CAST")
            handler(working, action as A).changes
        }
    }

    /**
     * Registers a broadcast handler for action [A] that runs once per
     * installed model. [transform] receives each model instance and
     * returns its (possibly unchanged) replacement. Use for
     * cross-cutting actions such as reset/logout that every model must
     * observe.
     */
    public inline fun <reified A : Any> onBroadcast(noinline transform: (model: Any, action: A) -> Any) {
        @Suppress("UNCHECKED_CAST")
        registerBroadcast(A::class) { model, action -> transform(model, action as A) }
    }

    @PublishedApi
    internal fun registerModel(modelClass: KClass<*>, initial: Any) {
        require(modelClasses.none { it == modelClass }) {
            "Model ${modelClass.simpleName ?: modelClass} registered more than once in createModelStore { }."
        }
        modelClasses += modelClass
        initials += initial
    }

    @PublishedApi
    internal fun addCell(actionClass: KClass<*>, source: String, handler: (WorkingState, Any) -> Map<KClass<*>, Any>) {
        table.getOrPut(actionClass) { mutableListOf() } += Cell(source, handler)
    }

    @PublishedApi
    internal fun registerBroadcast(actionClass: KClass<*>, perModel: (Any, Any) -> Any) {
        broadcasts += Broadcast(actionClass, perModel)
    }

    internal fun buildInitialState(): ModelState = ModelState.of(*initials.toTypedArray())

    internal fun buildReducer(devChecks: Boolean, onWrite: OnWrite?): Reducer<ModelState> =
        routedReducer(table, broadcasts, devChecks, onWrite, modelClasses.toList())
}

/**
 * The receiver of a [RoutingBuilder.model] block; registers
 * single-model handlers for model type [M].
 */
public class ModelHandlerScope<M : Any> @PublishedApi internal constructor(
    @PublishedApi internal val modelClass: KClass<M>,
    @PublishedApi internal val builder: RoutingBuilder,
) {
    /**
     * Registers a pure single-model handler for action [A]: given the
     * current model and the action, return the next model instance
     * (return the same instance to signal "no change"). Matching is by
     * exact leaf class — a handler on [A] does not catch subtypes of
     * [A].
     */
    public inline fun <reified A : Any> on(noinline reducer: (model: M, action: A) -> M) {
        builder.addCell(A::class, "model:${modelClass.simpleName}") { working, action ->
            val model = working.get(modelClass)
            @Suppress("UNCHECKED_CAST")
            val next = reducer(model, action as A)
            if (next !== model) mapOf<KClass<*>, Any>(modelClass to next) else emptyMap()
        }
    }
}

/**
 * A reusable bundle of model and handler registrations that can be
 * [install]ed into a [RoutingBuilder]. Lets feature modules package
 * their slice of the store; the app fixes ordering by the sequence of
 * [install] calls.
 */
public fun interface ReduxModule {
    /** Contributes this module's models and handlers to the builder. */
    public fun RoutingBuilder.contribute()
}

/**
 * Applies a [ReduxModule]'s registrations to this builder, in call
 * order.
 */
public fun RoutingBuilder.install(module: ReduxModule) {
    with(module) { contribute() }
}
```

- [ ] **Step 5: Implement `CreateModelStore.kt`**

```kotlin
package org.reduxkotlin.routing

import org.reduxkotlin.Store
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.createStore
import org.reduxkotlin.multimodel.ModelState
import kotlin.reflect.KClass

/**
 * Observer invoked for every effective model write during a dispatch,
 * i.e. only when the new instance is referentially different
 * (`next !== prev`). Fired synchronously during the fold, before the
 * dispatch commits, so under last-write-wins it can observe
 * intermediate (clobbered) values. It MUST be a pure observer: it must
 * not call `dispatch` or read the store.
 */
public typealias OnWrite = (action: Any, modelClass: KClass<*>, prev: Any, next: Any, source: String) -> Unit

/**
 * Builds a [Store] of [ModelState] from a routing [block]. Each model's
 * starting value comes from its [RoutingBuilder.model] declaration;
 * there is no INIT-action fan-out. Dispatch routes by the exact leaf
 * class of the action.
 *
 * @param enhancer optional store enhancer (e.g. `applyMiddleware`),
 *   passed straight to [createStore].
 * @param devChecks when true, throws if a handler returns a new but
 *   structurally-equal model instance (a wasteful no-op write that
 *   would fire subscribers spuriously).
 * @param onWrite optional [OnWrite] observer for tracing/conflict
 *   detection.
 */
public fun createModelStore(
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> {
    val builder = RoutingBuilder()
    builder.block()
    val initialState = builder.buildInitialState()
    val reducer = builder.buildReducer(devChecks, onWrite)
    return createStore(reducer, initialState, enhancer)
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.CreateModelStoreTest"`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "feat(routing): add createModelStore DSL with single-model handlers"
```

---

### Task 5: Exact-leaf matching guard

Lock the documented narrowing: a handler registered on a type does **not** catch its subtypes.

**Files:**
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/ExactLeafMatchingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ExactLeafMatchingTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private sealed interface Nav
private data class Open(val screen: String) : Nav
private data class Close(val screen: String) : Nav

private data class NavModel(val current: String = "home", val opens: Int = 0)

class ExactLeafMatchingTest {

    private fun store() = createModelStore {
        model(NavModel()) {
            on<Open> { s, a -> s.copy(current = a.screen, opens = s.opens + 1) }
        }
    }

    @Test
    fun handler_matches_its_exact_leaf_class() {
        val s = store()
        s.dispatch(Open("profile"))
        assertEquals("profile", s.state.get<NavModel>().current)
        assertEquals(1, s.state.get<NavModel>().opens)
    }

    @Test
    fun handler_does_not_match_a_sibling_subtype() {
        val s = store()
        val before = s.state
        s.dispatch(Close("profile")) // no handler registered for Close
        assertSame(before, s.state)
    }
}
```

- [ ] **Step 2: Run test to verify it passes (already-correct behavior)**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.ExactLeafMatchingTest"`
Expected: PASS — the engine keys by `action::class`, so `Close` finds no cell. This test documents and locks the behavior.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "test(routing): lock exact-leaf-class matching semantics"
```

---

### Task 6: Multi-model handlers — `onAction`, fold ordering, last-write-wins

**Files:**
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/MultiModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `MultiModelTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MultiModelTest {

    private fun store() = createModelStore {
        model(UserModel()) {
            on<LoggedIn> { s, a -> s.copy(user = a.user) }
        }
        model(CartModel(id = 7)) {
            on<AddItem> { s, a -> s.copy(items = s.items + a.item) }
        }
        onAction<Checkout> { reads, _ ->
            val cart = reads.get<CartModel>()
            writeSet {
                set(cart.copy(closed = true))
                set(reads.get<UserModel>().copy(lastOrder = cart.id))
            }
        }
    }

    @Test
    fun multi_model_handler_writes_several_models() {
        val s = store()
        s.dispatch(LoggedIn("ann"))
        s.dispatch(AddItem("book"))
        s.dispatch(Checkout)
        assertTrue(s.state.get<CartModel>().closed)
        assertEquals(7, s.state.get<UserModel>().lastOrder)
    }

    @Test
    fun later_handler_in_same_action_sees_earlier_write() {
        // Two handlers for one action: a single-model cart writer runs
        // first, then a multi-model handler reads the updated cart.
        val s = createModelStore {
            model(CartModel(id = 1)) {
                on<Checkout> { c, _ -> c.copy(items = c.items + "auto") }
            }
            model(UserModel()) {}
            onAction<Checkout> { reads, _ ->
                val cart = reads.get<CartModel>() // must see the "auto" item
                writeSet { set(reads.get<UserModel>().copy(user = cart.items.joinToString())) }
            }
        }
        s.dispatch(Checkout)
        assertEquals("auto", s.state.get<UserModel>().user)
    }

    @Test
    fun unchanged_models_keep_identity_after_multi_write() {
        val s = store()
        s.dispatch(LoggedIn("ann"))
        val userBefore = s.state.get<UserModel>()
        s.dispatch(AddItem("book")) // touches only cart
        assertSame(userBefore, s.state.get<UserModel>())
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.MultiModelTest"`
Expected: PASS — `onAction` and the fold are already implemented in Tasks 3–4; this exercises the multi-model path and fold ordering.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "test(routing): cover multi-model handlers and fold ordering"
```

---

### Task 7: Broadcast handlers — `onBroadcast` runs for every installed model

**Files:**
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/BroadcastTest.kt`

- [ ] **Step 1: Write the failing test**

Create `BroadcastTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BroadcastTest {

    @Test
    fun broadcast_runs_for_every_installed_model() {
        val s = createModelStore {
            model(UserModel(user = "ann")) {}
            model(CartModel(items = listOf("book"))) {}
            // Reset every model to a cleared instance.
            onBroadcast<ResetAll> { model, _ ->
                when (model) {
                    is UserModel -> UserModel()
                    is CartModel -> CartModel()
                    else -> model
                }
            }
        }
        s.dispatch(ResetAll)
        assertNull(s.state.get<UserModel>().user)
        assertEquals(emptyList(), s.state.get<CartModel>().items)
    }

    @Test
    fun broadcast_covers_models_declared_after_it() {
        // onBroadcast registered before the second model; must still
        // cover it because broadcasts materialize at build time.
        val s = createModelStore {
            model(UserModel(user = "ann")) {}
            onBroadcast<ResetAll> { model, _ -> if (model is UserModel) UserModel() else model }
            model(CartModel(items = listOf("book"))) {}
        }
        s.dispatch(ResetAll)
        assertNull(s.state.get<UserModel>().user)
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.BroadcastTest"`
Expected: PASS — `onBroadcast` + build-time materialization implemented in Tasks 3–4.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "test(routing): cover onBroadcast fan-out across installed models"
```

---

### Task 8: Modular composition — `install(ReduxModule)` and order-at-creation

**Files:**
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/ModuleInstallTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ModuleInstallTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals

private data class Audit(val log: List<String> = emptyList())

class ModuleInstallTest {

    private val userModule = ReduxModule {
        model(UserModel()) {
            on<LoggedIn> { s, a -> s.copy(user = a.user) }
        }
    }

    @Test
    fun installed_module_contributes_models_and_handlers() {
        val s = createModelStore {
            install(userModule)
        }
        s.dispatch(LoggedIn("ann"))
        assertEquals("ann", s.state.get<UserModel>().user)
    }

    @Test
    fun handler_order_follows_install_order() {
        // Two handlers for the same action append to the audit log in
        // registration order; install order is the composition point.
        val first = ReduxModule {
            model(Audit()) {
                on<Checkout> { a, _ -> a.copy(log = a.log + "first") }
            }
        }
        val second = ReduxModule {
            onAction<Checkout> { reads, _ ->
                val a = reads.get<Audit>()
                writeSet { set(a.copy(log = a.log + "second")) }
            }
        }
        val s = createModelStore {
            install(first)
            install(second)
        }
        s.dispatch(Checkout)
        assertEquals(listOf("first", "second"), s.state.get<Audit>().log)
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.ModuleInstallTest"`
Expected: PASS — `install` and ordered table append implemented in Task 4.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "test(routing): cover ReduxModule install and creation-time ordering"
```

---

### Task 9: `onWrite` hook — `!==`-gated, intermediate values, source label

**Files:**
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/OnWriteHookTest.kt`

- [ ] **Step 1: Write the failing test**

Create `OnWriteHookTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class Write(val modelClass: KClass<*>, val prev: Any, val next: Any, val source: String)

class OnWriteHookTest {

    @Test
    fun hook_fires_once_per_effective_write_with_source() {
        val writes = mutableListOf<Write>()
        val s = createModelStore(
            onWrite = { _, modelClass, prev, next, source -> writes += Write(modelClass, prev, next, source) },
        ) {
            model(UserModel()) {
                on<LoggedIn> { u, a -> u.copy(user = a.user) }
            }
        }
        s.dispatch(LoggedIn("ann"))
        assertEquals(1, writes.size)
        assertEquals(UserModel::class, writes[0].modelClass)
        assertEquals(UserModel(), writes[0].prev)
        assertEquals(UserModel(user = "ann"), writes[0].next)
        assertTrue(writes[0].source.contains("UserModel"))
    }

    @Test
    fun hook_does_not_fire_for_no_op_handler() {
        val writes = mutableListOf<Write>()
        val s = createModelStore(
            onWrite = { _, modelClass, prev, next, source -> writes += Write(modelClass, prev, next, source) },
        ) {
            model(UserModel()) {
                on<LoggedOut> { u, _ -> u } // returns same instance -> no write
            }
        }
        s.dispatch(LoggedOut)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun hook_observes_intermediate_values_under_last_write_wins() {
        val nexts = mutableListOf<Any>()
        // Two handlers for the same action both write UserModel.
        val s = createModelStore(
            onWrite = { _, _, _, next, _ -> nexts += next },
        ) {
            model(UserModel()) {
                on<Checkout> { u, _ -> u.copy(user = "a") }
            }
            onAction<Checkout> { reads, _ ->
                writeSet { set(reads.get<UserModel>().copy(user = "b")) }
            }
        }
        s.dispatch(Checkout)
        assertEquals(listOf(UserModel(user = "a"), UserModel(user = "b")), nexts)
        assertEquals("b", s.state.get<UserModel>().user) // committed = last write
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.OnWriteHookTest"`
Expected: PASS — `onWrite` plumbed in Tasks 3–4.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "test(routing): cover the !==-gated onWrite hook"
```

---

### Task 10: `devChecks` immutability assertion

**Files:**
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/DevChecksTest.kt`

- [ ] **Step 1: Write the failing test**

Create `DevChecksTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DevChecksTest {

    @Test
    fun devChecks_throws_on_structurally_equal_new_instance() {
        val s = createModelStore(devChecks = true) {
            model(UserModel()) {
                // Returns a brand-new instance that is structurally equal: a wasteful no-op write.
                on<LoggedIn> { u, _ -> u.copy() }
            }
        }
        assertFailsWith<IllegalStateException> {
            s.dispatch(LoggedIn("ann"))
        }
    }

    @Test
    fun devChecks_allows_real_change() {
        val s = createModelStore(devChecks = true) {
            model(UserModel()) {
                on<LoggedIn> { u, a -> u.copy(user = a.user) }
            }
        }
        s.dispatch(LoggedIn("ann")) // does not throw
    }

    @Test
    fun without_devChecks_no_op_copy_is_silently_committed() {
        val s = createModelStore(devChecks = false) {
            model(UserModel()) {
                on<LoggedIn> { u, _ -> u.copy() }
            }
        }
        s.dispatch(LoggedIn("ann")) // no throw; just a wasteful write
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.DevChecksTest"`
Expected: PASS — the `check(!(devChecks && next == prev))` guard is implemented in Task 3.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "test(routing): cover devChecks immutability assertion"
```

---

### Task 11: All-or-nothing error semantics

**Files:**
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/ErrorSemanticsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ErrorSemanticsTest.kt`:

```kotlin
package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class Boom : RuntimeException("boom")

class ErrorSemanticsTest {

    @Test
    fun throwing_handler_aborts_dispatch_without_partial_commit() {
        val s = createModelStore {
            model(UserModel()) {
                on<Checkout> { u, _ -> u.copy(user = "ann") } // staged first
            }
            onAction<Checkout> { _, _ -> throw Boom() } // throws before commit
        }
        val before = s.state
        assertFailsWith<Boom> { s.dispatch(Checkout) }
        // No partial commit: the earlier staged write must not survive.
        assertSame(before, s.state)
        assertTrue(s.state.get<UserModel>().user == null)
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.ErrorSemanticsTest"`
Expected: PASS — staging is local to `WorkingState`; a throw propagates before `withAll`, so `createStore` never reassigns `currentState`.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-routing/src
git commit -m "test(routing): lock all-or-nothing dispatch error semantics"
```

---

### Task 12: Granular integration — precise `===` firing

Confirms the routed store composes with `redux-kotlin-granular` (which is not a routing dependency, so the test lives where granular is available — add a test-only dependency).

**Files:**
- Modify: `redux-kotlin-routing/build.gradle.kts` (add `commonTest` dependency on granular + multimodel-granular)
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/GranularIntegrationTest.kt`

- [ ] **Step 1: Add the test dependency**

In `redux-kotlin-routing/build.gradle.kts`, add a `commonTest` source-set block inside `sourceSets { … }` (after the `commonMain { … }` block):

```kotlin
        commonTest {
            dependencies {
                implementation(project(":redux-kotlin-granular"))
            }
        }
```

- [ ] **Step 2: Write the failing test**

Create `GranularIntegrationTest.kt`:

```kotlin
package org.reduxkotlin.routing

import org.reduxkotlin.granular.subscribeTo
import kotlin.test.Test
import kotlin.test.assertEquals

class GranularIntegrationTest {

    @Test
    fun subscriber_fires_only_when_its_model_changes() {
        val s = createModelStore {
            model(UserModel()) {
                on<LoggedIn> { u, a -> u.copy(user = a.user) }
            }
            model(CartModel()) {
                on<AddItem> { c, a -> c.copy(items = c.items + a.item) }
            }
        }
        var userFires = 0
        s.subscribeTo(selector = { it.get<UserModel>() }, triggerOnSubscribe = false) { _, _ -> userFires++ }

        s.dispatch(AddItem("book")) // cart only -> user selector unchanged
        assertEquals(0, userFires)

        s.dispatch(LoggedIn("ann")) // user changes
        assertEquals(1, userFires)
    }
}
```

- [ ] **Step 3: Run test to verify it fails, then passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.GranularIntegrationTest"`
Expected: PASS once the dependency resolves. (If `subscribeTo`'s parameter names differ, open `redux-kotlin-granular/src/commonMain/kotlin/org/reduxkotlin/granular/SubscribeFields.kt` and match the actual signature — verified at plan time as `subscribeTo(selector, triggerOnSubscribe, listener)`.)

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-routing/build.gradle.kts redux-kotlin-routing/src
git commit -m "test(routing): verify precise granular subscription firing"
```

---

### Task 13: JVM concurrency stress under the thread-safe wrapper

**Files:**
- Modify: `redux-kotlin-routing/build.gradle.kts` (add `commonTest`/`jvmTest` dependency on threadsafe)
- Test: `redux-kotlin-routing/src/jvmTest/kotlin/org/reduxkotlin/routing/concurrency/RoutedThreadSafeStressTest.kt`

- [ ] **Step 1: Add the threadsafe test dependency**

In `redux-kotlin-routing/build.gradle.kts`, add to the `commonTest` dependencies block created in Task 12:

```kotlin
                implementation(project(":redux-kotlin-threadsafe"))
```

- [ ] **Step 2: Write the failing test**

The routed reducer is internal, so a test cannot re-wrap it with `createThreadSafeStore` directly; production code wraps the whole `Store` returned by `createModelStore`. This test instead serializes dispatch with an explicit lock — the same single-lock contract `redux-kotlin-threadsafe` provides — to validate that the routed fold + `withAll` commit is internally consistent under concurrent drive. Create `RoutedThreadSafeStressTest.kt`:

```kotlin
package org.reduxkotlin.routing.concurrency

import org.reduxkotlin.routing.AddItem
import org.reduxkotlin.routing.CartModel
import org.reduxkotlin.routing.createModelStore
import org.reduxkotlin.routing.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutedThreadSafeStressTest {

    @Test
    fun externally_synchronized_concurrent_dispatch_does_not_lose_writes() {
        val store = createModelStore {
            model(CartModel()) {
                on<AddItem> { c, a -> c.copy(items = c.items + a.item) }
            }
        }
        val lock = Any()
        val threads = 8
        val perThread = 1000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.submit {
                start.await()
                repeat(perThread) { synchronized(lock) { store.dispatch(AddItem("x")) } }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()
        assertEquals(threads * perThread, store.state.get<CartModel>().items.size)
    }
}
```

This validates that the routed fold + `withAll` commit is internally consistent under serialized concurrent dispatch (the contract the `redux-kotlin-threadsafe` wrapper provides via one lock). The `threadsafe` test dependency added in Step 1 documents the intended production wrapping even though this test synchronizes directly.

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.concurrency.RoutedThreadSafeStressTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-routing/build.gradle.kts redux-kotlin-routing/src
git commit -m "test(routing): add JVM concurrency stress for routed dispatch"
```

---

### Task 14: README, API dump, full-tree gate

**Files:**
- Create: `redux-kotlin-routing/README.md`
- Create (generated): `redux-kotlin-routing/api/redux-kotlin-routing.klib.api`, `redux-kotlin-routing/api/jvm/redux-kotlin-routing.api`

- [ ] **Step 1: Write the README**

Create `redux-kotlin-routing/README.md`:

```markdown
# redux-kotlin-routing

Routed `(model, action)` dispatch over `ModelState`. Replaces the
`when(action) {}` cascade with exact-leaf-class routing: an action only
visits the handlers registered for its concrete class, and only the
models a handler changes are rebuilt (preserving `===` identity of the
rest, so the granular subscription layer stays precise).

## Quick start

```kotlin
val store = createModelStore {
    model(UserModel()) {
        on<LoggedIn>  { s, a -> s.copy(user = a.user) }
        on<LoggedOut> { s, _ -> s.copy(user = null) }
    }
    model(CartModel()) {
        on<AddItem> { s, a -> s.copy(items = s.items + a.item) }
    }
    onAction<Checkout> { reads, _ ->
        val cart = reads.get<CartModel>()
        writeSet { set(cart.copy(closed = true)) }
    }
    onBroadcast<Logout> { model, _ -> /* reset each model */ model }
    install(SomeFeatureModule)
}
```

## Semantics

- **Exact-leaf matching.** `on<Open>` matches `Open`, not subtypes of a
  shared sealed parent. Register each leaf, or use `onBroadcast` for
  cross-cutting actions.
- **Structural init.** A model's starting value is its `model(initial)`
  declaration. There is no INIT-action fan-out.
- **Order fixed at creation.** Handlers for the same action run in
  registration order; `install(module)` order is the composition point.
- **Last-write-wins** on same-model writes within one dispatch.
- **Immutability is required.** Return a new instance to signal a
  change, the same instance for "no change". Enable `devChecks = true`
  to fail fast on wasteful structurally-equal copies.
- **All-or-nothing.** A handler that throws aborts the whole dispatch;
  no partial commit.

Built on `redux-kotlin` + `redux-kotlin-multimodel`. Wrap with
`createThreadSafeStore` for cross-thread access; composes with
`redux-kotlin-granular` and the Compose bindings unchanged.
```

- [ ] **Step 2: Generate the API baseline**

Run: `./gradlew :redux-kotlin-routing:apiDump`
Expected: `BUILD SUCCESSFUL`; `redux-kotlin-routing/api/` now contains the `.klib.api` and `jvm/*.api` baselines listing the public surface (`createModelStore`, `RoutingBuilder`, `ModelHandlerScope`, `ReduxModule`, `install`, `Reads`, `WriteSet`, `WriteSetBuilder`, `writeSet`, `OnWrite`).

- [ ] **Step 3: Run the full module gate**

Run: `./gradlew :redux-kotlin-routing:build`
Expected: `BUILD SUCCESSFUL` — compiles, runs tests, passes `apiCheck`, passes detekt (`explicitApi` + KDoc). If detekt reports a missing KDoc on a public symbol, add it (do not hand-fix formatting — the pre-commit auto-correct handles that).

- [ ] **Step 4: Run detekt across the tree**

Run: `./gradlew detektAll`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-routing/README.md redux-kotlin-routing/api
git commit -m "docs(routing): add README and public API baseline"
```

---

### Task 15: Cross-target verification & plan self-check

**Files:** none (verification only)

- [ ] **Step 1: Run the host's full target tests for the new module**

Run: `./gradlew :redux-kotlin-routing:allTests`
Expected: `BUILD SUCCESSFUL` across the targets this host can run (JVM, JS, wasmJs, native for the host OS). If `iosSimulatorArm64Test` fails with an Xcode SDK error, that is environmental — trust CI (per CLAUDE.md).

- [ ] **Step 2: Run the multimodel module build (regression for `withAll`)**

Run: `./gradlew :redux-kotlin-multimodel:build`
Expected: `BUILD SUCCESSFUL` (includes `apiCheck` against the dump regenerated in Task 1).

- [ ] **Step 3: Confirm no unintended public-API drift across the repo**

Run: `./gradlew apiCheck`
Expected: `BUILD SUCCESSFUL`. Only `redux-kotlin-routing` (new) and `redux-kotlin-multimodel` (`withAll`) dumps should have changed in this branch.

- [ ] **Step 4: Final smoke build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

---

## Self-Review (run before handoff)

**Spec coverage (Phase 1 obligations → tasks):**
- Exact-leaf matching → Tasks 3 (engine keys by `action::class`), 5 (locked).
- Return-a-write-set single idiom → Tasks 2 (`WriteSet`/`writeSet`), 4 (`on` returns `M`), 6 (`onAction` returns `WriteSet`).
- Structural init, no INIT fan-out → Task 4 (`model(initial)`, `buildInitialState`); README + KDoc state it.
- `onBroadcast` for cross-cutting actions → Task 7.
- Copy-on-first-write / zero-alloc no-op / `===` preservation → Tasks 1 (`withAll` single copy), 3 (lazy `staged`, unhandled returns same instance), 4 & 6 (identity tests).
- All-or-nothing error semantics → Task 11.
- Immutability hard contract + dev assertion → Task 10.
- `!==`-gated `onWrite` hook (with `source`, intermediate values) → Task 9.
- All KMP targets → Task 15 (`allTests`).
- detekt `explicitApi` + KDoc → KDoc in every public code block; Task 14 Steps 3–4.
- `apiDump` → Tasks 1 (multimodel), 14 (routing).
- Module conventions (`library-mpp-loved` + `publishing-mpp`, package, registry template) → Task 0.

**Out of scope (correctly absent):** KSP codegen (B), compiler plugin (C), `-effects`/coroutines module, devtools strippable-module wiring, serialization, dynamic post-creation registration / Play Feature Delivery splits — all deferred per the spec.

**Type-name consistency check:** `createModelStore`, `RoutingBuilder`, `ModelHandlerScope<M>.on`, `RoutingBuilder.onAction`/`onBroadcast`/`model`, `ReduxModule.contribute`, `install`, `Reads.get`, `WriteSet.changes`, `WriteSetBuilder.set`, `writeSet`, `OnWrite`, internal `Cell`/`Broadcast`/`WorkingState`/`routedReducer`, `ModelState.withAll` — names are used identically across Tasks 1–14.

**Known wiring note:** Task 13 uses an externally-synchronized test because the routed reducer is `internal` and cannot be re-wrapped by `createThreadSafeStore` directly in a test; production code wraps the whole `Store` returned by `createModelStore`. The `threadsafe` test dependency is added to document that intended production wrapping.

---

## Execution Handoff

Plan complete. Choose execution mode (offered after save).
