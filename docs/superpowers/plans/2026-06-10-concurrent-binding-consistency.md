# Concurrent Store ↔ Compose Binding Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the "dispatch-then-read-stale" footgun: the concurrent store updates state synchronously but notifies subscribers asynchronously, so Compose bindings lag a frame. Make the bindings lag-free, give apps a main-thread-coalescing notification context, and add first-class preloaded state for restore — plus document the consistency model.

**Architecture:** Four independent improvements. **A** rewrites `selectorState`/`fieldState` (redux-kotlin-compose) so the returned `State` reads `getState()` synchronously on every read and uses the subscription only as an invalidation signal — always fresh, regardless of async notification. **B** adds `coalescingNotificationContext(...)` (redux-kotlin-concurrent) that runs subscriber callbacks inline when already on the target thread, else posts. **C** adds `preloadedState: ModelState?` to `createModelStore` (redux-kotlin-routing) and `createConcurrentModelStore` (redux-kotlin-bundle), backed by a new `ModelState.withAll(other: ModelState)` overload (redux-kotlin-multimodel), so restored state can seed the store at construction. **D** documents the sync-write/async-notify model across the touched KDoc plus a reference note.

**Tech Stack:** Kotlin Multiplatform, Compose runtime (`State`, `mutableIntStateOf`, `DisposableEffect`), redux-kotlin core + granular (`subscribeTo`), redux-kotlin-multimodel (`ModelState`), routing DSL, kotlinx ABI validation (`apiDump`/`apiCheck`), detekt (`explicitApi` → KDoc on every public decl).

**Scope boundaries (confirmed):**
- Library-only. Do NOT modify `examples/taskflow` (its manual workarounds stay; adopt later).
- Branch `feat/concurrent-binding-consistency` off master, own PR.
- A changes no public signatures → no compose `apiDump`. B/C add public declarations → regenerate dumps for `redux-kotlin-concurrent`, `redux-kotlin-multimodel`, `redux-kotlin-routing`, `redux-kotlin-bundle`.

**Verified facts (do NOT re-derive):**
- `CallerSerializedStore` (`redux-kotlin-concurrent/.../ConcurrentStore.kt`): `dispatch` runs the reducer under lock and sets `mirror.value = inner.getState()` before returning (state sync); subscriber callbacks go through `notificationContext.post { ... }` (async when the context posts). `getState()` off-context returns `mirror.value`.
- `NotificationContext` (`redux-kotlin-concurrent/.../NotificationContext.kt`): `public fun interface NotificationContext { public fun post(block: () -> Unit) }` with companion `Inline` (`NotificationContext { block -> block() }`).
- `selectorState`/`fieldState` (`redux-kotlin-compose/.../FieldState.kt:43-93`): currently store the value pushed by the async subscriber into a `mutableStateOf`; only the initial value + a DisposableEffect re-sample are synchronous reads. `redux-kotlin-compose-multimodel`'s `fieldState`/`fieldStateOf` delegate to `selectorState`, so fixing it fixes both.
- `subscribeTo` (`redux-kotlin-granular`): `fun <State,F> Store<State>.subscribeTo(selector: (State)->F, triggerOnSubscribe: Boolean = true, listener: (oldValue: F, newValue: F) -> Unit): StoreSubscription`; and a `KProperty1` overload with the same shape. Listener fires only when the selected value changes.
- `ModelState` (`redux-kotlin-multimodel/.../ModelState.kt`): immutable map keyed by concrete `KClass<*>`; `@PublishedApi internal val models`; `public fun withAll(changes: Map<KClass<*>, Any>): ModelState` exists and checks keys ⊆ declared set; `companion.of(vararg models: Any)` builds it.
- `createModelStore` (`redux-kotlin-routing/.../CreateModelStore.kt:35-46`): builds initial via `builder.buildInitialState()` then `createStore(reducer, initialState, enhancer)`.
- `createConcurrentModelStore` (`redux-kotlin-bundle/.../StoreFactory.kt:26-35`): `createModelStore(...).asConcurrent(notificationContext, onError)`.
- All five modules apply `convention.library-mpp-loved` + `convention.publishing-mpp` (explicitApi + ABI validation). Gate: `./gradlew detektAll`, `./gradlew apiCheck`, `./gradlew apiDump`. Verify on JVM (`:module:jvmTest`); native/iOS are host-gated — trust CI.

---

## File Structure

- **Modify** `redux-kotlin-compose/src/commonMain/kotlin/org/reduxkotlin/compose/FieldState.kt` — lag-free `selectorState` + `fieldState` (A).
- **Modify** `redux-kotlin-compose/src/jvmTest/kotlin/org/reduxkotlin/compose/FieldStateTest.kt` — add the async-notify regression test + a deferred-notify fake store (A).
- **Modify** `redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/NotificationContext.kt` — add `coalescingNotificationContext` (B).
- **Modify** `redux-kotlin-concurrent/src/commonTest/kotlin/org/reduxkotlin/concurrent/NotificationContextTest.kt` — coalescing tests (B).
- **Modify** `redux-kotlin-multimodel/src/commonMain/kotlin/org/reduxkotlin/multimodel/ModelState.kt` — `withAll(other: ModelState)` overload (C).
- **Modify** `redux-kotlin-multimodel/src/commonTest/kotlin/org/reduxkotlin/multimodel/ModelStateTest.kt` — overload test (C).
- **Modify** `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/CreateModelStore.kt` — `preloadedState` param (C).
- **Modify** `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/` — preload test (C).
- **Modify** `redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/StoreFactory.kt` — `preloadedState` param (C).
- **Modify** `redux-kotlin-bundle/src/commonTest/kotlin/org/reduxkotlin/bundle/` — preload test (C).
- **Create** `docs/agent/references/store-consistency-model.md` — the consistency model note (D).
- **Regenerate** `api/**/*.api` + `api/*.klib.api` for concurrent, multimodel, routing, bundle (B/C).

---

### Task 1: Lag-free `selectorState` / `fieldState` (A)

**Files:**
- Modify: `redux-kotlin-compose/src/commonMain/kotlin/org/reduxkotlin/compose/FieldState.kt`
- Test: `redux-kotlin-compose/src/jvmTest/kotlin/org/reduxkotlin/compose/FieldStateTest.kt`

- [ ] **Step 1: Write the failing test**

Add a deferred-notify fake store + a regression test to `FieldStateTest.kt`. The fake models the concurrent store: state updates synchronously, subscriber callbacks are queued until `drain()`. Append inside the test class (and the fake as a top-level class in the same file):

```kotlin
// Top-level in FieldStateTest.kt — models sync-write / async-notify (like the concurrent store).
private class DeferredNotifyStore<S>(initial: S) : org.reduxkotlin.Store<S> {
    private var current: S = initial
    private val listeners = mutableListOf<org.reduxkotlin.StoreSubscriber>()
    private val pending = mutableListOf<() -> Unit>()
    fun drain() { val copy = pending.toList(); pending.clear(); copy.forEach { it() } }
    override val getState: org.reduxkotlin.GetState<S> = { current }
    override var dispatch: org.reduxkotlin.Dispatcher = { action ->
        // State updates synchronously; subscriber notifications are deferred (queued).
        if (action is S) {
            @Suppress("UNCHECKED_CAST")
            current = action as S
        }
        val snapshot = listeners.toList()
        snapshot.forEach { l -> pending.add { l() } }
        action
    }
    override val subscribe: (org.reduxkotlin.StoreSubscriber) -> org.reduxkotlin.StoreSubscription = { l ->
        listeners.add(l)
        ({ listeners.remove(l); Unit })
    }
    override val replaceReducer: (org.reduxkotlin.Reducer<S>) -> Unit = { }
}
```

```kotlin
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun selectorStateReadsFreshStateWhenRecomposedBeforeAsyncNotifyDrains() = runComposeUiTest {
        val store = DeferredNotifyStore("a")
        val external = mutableStateOf(0)
        setContent {
            // Reading `external` lets us force a recomposition WITHOUT draining the store's
            // deferred notifications — exercising the dispatch-then-read-stale path.
            val tick = external.value
            val value by store.selectorState { it }
            Text("v=$value t=$tick")
        }
        waitForIdle()
        onAllNodesWithText("v=a t=0").assertCountEquals(1)

        // Dispatch updates state synchronously, but the subscriber notification is NOT drained.
        store.dispatch("b")
        // Force a recomposition via the external state (the store subscriber hasn't fired yet).
        external.value = 1
        waitForIdle()

        // The binding must reflect the fresh store state, not the stale "a".
        onAllNodesWithText("v=b t=1").assertCountEquals(1)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-compose:jvmTest --tests "org.reduxkotlin.compose.FieldStateTest"`
Expected: the new test FAILS — current impl shows `v=a t=1` (binding stale until `drain()`).

- [ ] **Step 3: Rewrite `selectorState` + `fieldState`**

Replace the bodies in `FieldState.kt`. New `selectorState`:

```kotlin
@Composable
public fun <S, F> Store<S>.selectorState(selector: (S) -> F): State<F> {
    val store = this
    val rememberedSelector = remember(store) { selector }
    // Bumped by the granular subscription only when the selected value actually changes; this
    // schedules recomposition. The value is read fresh from getState() in the returned State's
    // getter, so it always reflects the latest *synchronous* store state and never lags an
    // asynchronous NotificationContext (e.g. a concurrent store posting callbacks to the main thread).
    val tick = remember(store, rememberedSelector) { mutableIntStateOf(0) }
    DisposableEffect(store, rememberedSelector) {
        val sub = store.subscribeTo(rememberedSelector, triggerOnSubscribe = false) { _, _ -> tick.intValue++ }
        onDispose { sub() }
    }
    return remember(store, rememberedSelector) {
        object : State<F> {
            override val value: F
                get() {
                    tick.intValue // read the invalidation signal so reads recompose on change
                    return rememberedSelector(store.state) // always-fresh synchronous read
                }
        }
    }
}
```

New `fieldState`:

```kotlin
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public fun <S, F> Store<S>.fieldState(property: KProperty1<S, F>): State<F> {
    val store = this
    val tick = remember(store, property) { mutableIntStateOf(0) }
    DisposableEffect(store, property) {
        val sub = store.subscribeTo(property, triggerOnSubscribe = false) { _, _ -> tick.intValue++ }
        onDispose { sub() }
    }
    return remember(store, property) {
        object : State<F> {
            override val value: F
                get() {
                    tick.intValue // read the invalidation signal so reads recompose on change
                    return property.get(store.state) // always-fresh synchronous read
                }
        }
    }
}
```

Update the imports block: ensure `androidx.compose.runtime.mutableIntStateOf` is imported; remove `androidx.compose.runtime.mutableStateOf` if it is now unused. Keep `Composable`, `DisposableEffect`, `State`, `remember`. Update the two functions' KDoc to state: "Always reflects `selector(store.state)` — the value is read synchronously from the store on every read, and the subscription only schedules recomposition; this holds even when the store delivers notifications asynchronously." Remove the now-obsolete "B3 race" wording (the read is inherently race-free now).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :redux-kotlin-compose:jvmTest --tests "org.reduxkotlin.compose.FieldStateTest"`
Expected: PASS (new test + all existing tests). If an existing test asserted timing tied to the old `mutableStateOf` behavior, confirm it still asserts correct *values* (it should — the value is now fresher, never staler).

Run: `./gradlew :redux-kotlin-compose-multimodel:jvmTest`
Expected: PASS (delegates to `selectorState`).

Run: `./gradlew detektAll`
Expected: PASS (KDoc on the public funs is present).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-compose/src/commonMain/kotlin/org/reduxkotlin/compose/FieldState.kt \
        redux-kotlin-compose/src/jvmTest/kotlin/org/reduxkotlin/compose/FieldStateTest.kt
git commit -m "fix(compose): selectorState/fieldState read store state synchronously (no async-notify lag)"
```

---

### Task 2: `coalescingNotificationContext` (B)

**Files:**
- Modify: `redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/NotificationContext.kt`
- Test: `redux-kotlin-concurrent/src/commonTest/kotlin/org/reduxkotlin/concurrent/NotificationContextTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `NotificationContextTest.kt`:

```kotlin
    @Test
    fun coalescingRunsInlineWhenOnTargetThread() {
        val posted = mutableListOf<() -> Unit>()
        var onTarget = true
        val ctx = coalescingNotificationContext(isOnTargetThread = { onTarget }, post = { posted.add(it) })

        var ran = false
        ctx.post { ran = true }
        assertTrue(ran, "on the target thread the block runs inline")
        assertEquals(0, posted.size, "nothing is deferred when inline")
    }

    @Test
    fun coalescingDefersWhenOffTargetThread() {
        val posted = mutableListOf<() -> Unit>()
        val ctx = coalescingNotificationContext(isOnTargetThread = { false }, post = { posted.add(it) })

        var ran = false
        ctx.post { ran = true }
        assertFalse(ran, "off the target thread the block is deferred to post")
        assertEquals(1, posted.size)
        posted.single().invoke()
        assertTrue(ran)
    }
```

Ensure imports: `kotlin.test.Test`, `assertTrue`, `assertFalse`, `assertEquals`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-concurrent:jvmTest --tests "org.reduxkotlin.concurrent.NotificationContextTest"`
Expected: FAIL — `coalescingNotificationContext` unresolved.

- [ ] **Step 3: Add the factory**

Append to `NotificationContext.kt` (inside the file, top-level):

```kotlin
/**
 * A [NotificationContext] that runs the callback **inline** when already on the target
 * thread, and otherwise hands it to [post] for marshaling.
 *
 * This avoids the read-after-dispatch lag that a purely-posting context introduces: when a UI
 * thread dispatches and a subscriber updates UI state, an always-posting context (e.g. a bare
 * `Handler.post`) delivers the callback on a *later* loop iteration, so observers can briefly read
 * stale state. Running inline on the target thread keeps the subscriber synchronous with the
 * dispatch (matching a plain synchronous store), while off-thread dispatches still marshal via
 * [post] (preserving the off-main-effects rule).
 *
 * @param isOnTargetThread returns true when the calling thread is the target (e.g. the main thread).
 * @param post marshals [block] to the target thread (e.g. `handler::post`); used only when
 *   [isOnTargetThread] returns false.
 * @return a [NotificationContext] that coalesces to inline execution on the target thread.
 */
public fun coalescingNotificationContext(
    isOnTargetThread: () -> Boolean,
    post: (block: () -> Unit) -> Unit,
): NotificationContext = NotificationContext { block ->
    if (isOnTargetThread()) block() else post(block)
}
```

- [ ] **Step 4: Run test + regenerate API dump**

Run: `./gradlew :redux-kotlin-concurrent:jvmTest --tests "org.reduxkotlin.concurrent.NotificationContextTest"`
Expected: PASS.

Run: `./gradlew :redux-kotlin-concurrent:updateKotlinAbi` (or root `./gradlew apiDump`)
Then `./gradlew detektAll` and `./gradlew :redux-kotlin-concurrent:checkKotlinAbi`
Expected: dump now includes `coalescingNotificationContext`; checks PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/NotificationContext.kt \
        redux-kotlin-concurrent/src/commonTest/kotlin/org/reduxkotlin/concurrent/NotificationContextTest.kt \
        redux-kotlin-concurrent/api/
git commit -m "feat(concurrent): coalescingNotificationContext — inline on target thread, else post"
```

---

### Task 3: `ModelState.withAll(other: ModelState)` overload (C)

**Files:**
- Modify: `redux-kotlin-multimodel/src/commonMain/kotlin/org/reduxkotlin/multimodel/ModelState.kt`
- Test: `redux-kotlin-multimodel/src/commonTest/kotlin/org/reduxkotlin/multimodel/ModelStateTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `ModelStateTest.kt` (use the model types already defined in that test file; if it defines e.g. `data class A(...)`/`data class B(...)`, reuse them — read the file first and match its fixtures):

```kotlin
    @Test
    fun withAllFromAnotherModelStateOverridesMatchingSlots() {
        val base = ModelState.of(Counter(0), Label("x"))
        val overrides = ModelState.of(Counter(5))
        val merged = base.withAll(overrides)
        assertEquals(5, merged.get<Counter>().n)
        assertEquals("x", merged.get<Label>().text) // untouched slot preserved
    }

    @Test
    fun withAllFromModelStateRejectsUndeclaredKey() {
        val base = ModelState.of(Counter(0))
        val foreign = ModelState.of(Label("y"))
        assertFailsWith<IllegalStateException> { base.withAll(foreign) }
    }
```

> NOTE: `Counter(n: Int)` / `Label(text: String)` are placeholders for whatever model fixtures `ModelStateTest.kt` already declares. Read the file and use its real fixtures; do not add new ones if suitable ones exist.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-multimodel:jvmTest --tests "org.reduxkotlin.multimodel.ModelStateTest"`
Expected: FAIL — `withAll(ModelState)` unresolved.

- [ ] **Step 3: Add the overload**

In `ModelState.kt`, immediately after the existing `withAll(changes: Map<KClass<*>, Any>)`:

```kotlin
    /**
     * Returns a copy with every slot present in [other] overridden by [other]'s value, leaving all
     * other slots untouched. The key set is unchanged: every model class in [other] must already be
     * declared in this [ModelState] (use [Companion.of] to build [other] from a subset of models).
     *
     * Typical use: seeding restored/persisted values over a freshly-built default state.
     *
     * @param other the models to overlay; its key set must be a subset of this state's key set.
     * @return a new [ModelState] with [other]'s slots applied.
     * @throws IllegalStateException if [other] contains a model class not declared here.
     */
    public fun withAll(other: ModelState): ModelState = withAll(other.models)
```

- [ ] **Step 4: Run test + regenerate API dump**

Run: `./gradlew :redux-kotlin-multimodel:jvmTest --tests "org.reduxkotlin.multimodel.ModelStateTest"`
Expected: PASS.

Run: `./gradlew :redux-kotlin-multimodel:updateKotlinAbi` then `./gradlew detektAll :redux-kotlin-multimodel:checkKotlinAbi`
Expected: dump includes the new `withAll(ModelState)`; checks PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-multimodel/src/commonMain/kotlin/org/reduxkotlin/multimodel/ModelState.kt \
        redux-kotlin-multimodel/src/commonTest/kotlin/org/reduxkotlin/multimodel/ModelStateTest.kt \
        redux-kotlin-multimodel/api/
git commit -m "feat(multimodel): ModelState.withAll(other: ModelState) overlay overload"
```

---

### Task 4: `preloadedState` in `createModelStore` (C)

**Files:**
- Modify: `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/CreateModelStore.kt`
- Test: `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/` (add `CreateModelStorePreloadTest.kt`, matching the package + fixture style of the existing routing tests — read one first)

- [ ] **Step 1: Write the failing test**

Create `redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/CreateModelStorePreloadTest.kt` (reuse model/action fixtures from the existing routing tests if present; the snippet below assumes simple local ones):

```kotlin
package org.reduxkotlin.routing

import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CounterModel(val n: Int = 0)
private data class LabelModel(val text: String = "default")
private data class Inc(val by: Int)

class CreateModelStorePreloadTest {
    @Test
    fun preloadedStateOverridesDeclaredDefaults() {
        val store = createModelStore(
            preloadedState = ModelState.of(CounterModel(42)),
        ) {
            model(CounterModel()) { on<Inc> { m, a -> m.copy(n = m.n + a.by) } }
            model(LabelModel()) { }
        }
        assertEquals(42, store.state.get<CounterModel>().n) // preloaded value, not default 0
        assertEquals("default", store.state.get<LabelModel>().text) // untouched
    }

    @Test
    fun nullPreloadedStateUsesDeclaredDefaults() {
        val store = createModelStore {
            model(CounterModel()) { on<Inc> { m, a -> m.copy(n = m.n + a.by) } }
        }
        assertEquals(0, store.state.get<CounterModel>().n)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.CreateModelStorePreloadTest"`
Expected: FAIL — `createModelStore` has no `preloadedState` parameter.

- [ ] **Step 3: Add the parameter**

In `CreateModelStore.kt`, update `createModelStore` (add the param + apply the overlay; keep existing params/order, insert `preloadedState` before `block` so the trailing-lambda call site is unaffected):

```kotlin
public fun createModelStore(
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    preloadedState: ModelState? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> {
    val builder = RoutingBuilder()
    builder.block()
    val declared = builder.buildInitialState()
    val initialState = if (preloadedState == null) declared else declared.withAll(preloadedState)
    val reducer = builder.buildReducer(devChecks, onWrite)
    return createStore(reducer, initialState, enhancer)
}
```

Add a KDoc line for the new param: `@param preloadedState optional restored/persisted models overlaid onto the declared defaults at construction (its key set must be a subset of the declared models). Use to rehydrate state synchronously instead of dispatching after first render.`

- [ ] **Step 4: Run test + regenerate API dump**

Run: `./gradlew :redux-kotlin-routing:jvmTest --tests "org.reduxkotlin.routing.CreateModelStorePreloadTest"`
Expected: PASS.

Run: `./gradlew :redux-kotlin-routing:updateKotlinAbi` then `./gradlew detektAll :redux-kotlin-routing:checkKotlinAbi`
Expected: dump reflects the new signature; checks PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/CreateModelStore.kt \
        redux-kotlin-routing/src/commonTest/kotlin/org/reduxkotlin/routing/CreateModelStorePreloadTest.kt \
        redux-kotlin-routing/api/
git commit -m "feat(routing): createModelStore preloadedState — overlay restored models at construction"
```

---

### Task 5: `preloadedState` in `createConcurrentModelStore` (C)

**Files:**
- Modify: `redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/StoreFactory.kt`
- Test: `redux-kotlin-bundle/src/commonTest/kotlin/org/reduxkotlin/bundle/` (add `CreateConcurrentModelStorePreloadTest.kt`)

- [ ] **Step 1: Write the failing test**

Create `redux-kotlin-bundle/src/commonTest/kotlin/org/reduxkotlin/bundle/CreateConcurrentModelStorePreloadTest.kt`:

```kotlin
package org.reduxkotlin.bundle

import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CounterModel(val n: Int = 0)
private data class Inc(val by: Int)

class CreateConcurrentModelStorePreloadTest {
    @Test
    fun preloadedStateSeedsConcurrentStore() {
        val store = createConcurrentModelStore(
            preloadedState = ModelState.of(CounterModel(7)),
        ) {
            model(CounterModel()) { on<Inc> { m, a -> m.copy(n = m.n + a.by) } }
        }
        assertEquals(7, store.state.get<CounterModel>().n)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-bundle:jvmTest --tests "org.reduxkotlin.bundle.CreateConcurrentModelStorePreloadTest"`
Expected: FAIL — no `preloadedState` parameter.

- [ ] **Step 3: Add the parameter + forward**

In `StoreFactory.kt`, update `createConcurrentModelStore` (insert `preloadedState` before `block`):

```kotlin
public fun createConcurrentModelStore(
    enhancer: StoreEnhancer<ModelState>? = null,
    notificationContext: NotificationContext = NotificationContext.Inline,
    onError: (Throwable) -> Unit = LogAndContinue,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    preloadedState: ModelState? = null,
    block: RoutingBuilder.() -> Unit,
): ConcurrentStore<ModelState> =
    createModelStore(enhancer = enhancer, devChecks = devChecks, onWrite = onWrite, preloadedState = preloadedState, block = block)
        .asConcurrent(notificationContext, onError)
```

Add KDoc for the new param: `@param preloadedState optional restored/persisted models overlaid onto the declared defaults at construction; forwarded to createModelStore. Seeds the store synchronously so the first read/render already reflects restored state.`

- [ ] **Step 4: Run test + regenerate API dump**

Run: `./gradlew :redux-kotlin-bundle:jvmTest --tests "org.reduxkotlin.bundle.CreateConcurrentModelStorePreloadTest"`
Expected: PASS.

Run: `./gradlew :redux-kotlin-bundle:updateKotlinAbi` then `./gradlew detektAll :redux-kotlin-bundle:checkKotlinAbi`
Expected: dump reflects new signature; checks PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/StoreFactory.kt \
        redux-kotlin-bundle/src/commonTest/kotlin/org/reduxkotlin/bundle/CreateConcurrentModelStorePreloadTest.kt \
        redux-kotlin-bundle/api/
git commit -m "feat(bundle): createConcurrentModelStore preloadedState forwarding"
```

---

### Task 6: Consistency-model docs + full gate (D)

**Files:**
- Create: `docs/agent/references/store-consistency-model.md`

- [ ] **Step 1: Write the reference doc**

Create `docs/agent/references/store-consistency-model.md`:

```markdown
# Store consistency model (concurrent store + Compose)

The concurrent store (`createConcurrentStore` / `createConcurrentModelStore`, the bundle default)
separates two things:

- **State writes are synchronous.** `dispatch` runs the reducer under a writer lock and publishes the
  state mirror *before it returns*. Immediately after `dispatch`, `getState()` / `getModel<T>()` return
  the new state.
- **Subscriber notifications follow the `NotificationContext`.** With a posting context (e.g. a bare
  `Handler.post` on Android), callbacks run on a *later* loop iteration — eventual consistency.

**Consequence — don't branch on a binding right after dispatch.** `selectorState`/`fieldState` are
driven by the subscription. As of the lag-free rewrite, they read `getState()` synchronously on every
read (the subscription only schedules recomposition), so a binding value is always current at read
time. But if you mix a *synchronously-flipped* flag with a value derived from the store, read the value
via `getState()`/`getModel()` — not a stale local copy.

**Rehydrating on launch.** Prefer seeding restored state at construction via `preloadedState` rather
than dispatching after first composition:

    createConcurrentModelStore(
        preloadedState = ModelState.of(NavModel(restoredStack), FilterModel(restoredQuery)),
    ) { /* model(...) declarations */ }

This makes the first render correct with no post-paint dispatch and no intermediate flash.

**Main-thread notifications.** Wrap your platform main-thread post with
`coalescingNotificationContext { isOnMainThread() } / { handler.post(it) }` so a main-thread dispatch
notifies subscribers inline (no extra frame of latency) while off-main dispatches still marshal.
```

- [ ] **Step 2: Full gate**

Run: `./gradlew detektAll`
Expected: PASS.

Run: `./gradlew apiCheck`
Expected: PASS (all four regenerated dumps match).

Run: `./gradlew :redux-kotlin-compose:jvmTest :redux-kotlin-compose-multimodel:jvmTest :redux-kotlin-concurrent:jvmTest :redux-kotlin-multimodel:jvmTest :redux-kotlin-routing:jvmTest :redux-kotlin-bundle:jvmTest`
Expected: PASS.

If the website docs index requires linking new reference docs, add a link where the other `docs/agent/references/*.md` are indexed (check `docs/agent/references/README.md`). Keep markdown valid.

- [ ] **Step 3: Commit**

```bash
git add docs/agent/references/store-consistency-model.md docs/agent/references/README.md
git commit -m "docs: store consistency model (sync writes, async notify, preload, coalescing)"
```

---

## Self-Review

**Spec coverage:**
- A (lag-free bindings) → Task 1. ✅
- B (coalescing notification) → Task 2. ✅
- C (preloaded state) → Tasks 3 (ModelState overlay), 4 (routing), 5 (bundle). ✅
- D (docs) → Task 6 + KDoc added in every task (explicitApi requires it). ✅
- apiDump for concurrent/multimodel/routing/bundle → Tasks 2/3/4/5; final apiCheck → Task 6. ✅

**Placeholder scan:** Test fixtures in Tasks 3/4/5 are labeled as reuse-or-match-existing (`Counter`/`Label`/`CounterModel`) with an explicit instruction to read the test file first — a real fixture decision, not a code placeholder. Task 1's `DeferredNotifyStore` is fully specified.

**Type consistency:** `preloadedState: ModelState?` identical in Tasks 4 and 5; both overlay via `ModelState.withAll(other: ModelState)` from Task 3. `coalescingNotificationContext(isOnTargetThread, post)` consistent. `selectorState`/`fieldState` keep their `State<F>` return types (no API change → no compose dump). Native/iOS tests host-gated — JVM verification + CI per CLAUDE.md.
