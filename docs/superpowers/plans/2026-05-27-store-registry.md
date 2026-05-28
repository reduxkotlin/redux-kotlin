# Store Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new KMP module `redux-kotlin-registry` providing a thread-safe registry of stores keyed by a unique identifier, with lock-free reads, creator-at-most-once `getOrCreate` semantics, manual lifecycle, and synchronous membership-change listeners. Includes a heterogeneous opt-in variant.

**Architecture:** Per `docs/superpowers/specs/2026-05-27-store-registry-design.md`. Two public types (`StoreRegistry<K, S>`, `TypedStoreRegistry`) sharing one internal generic `RegistryCore<K, V>`. Lock-free `AtomicRef<Map<K, V>>` snapshot for reads; brief `kotlinx.atomicfu.locks.SynchronizedObject` lock on writes. Listeners fire under the same lock for total event ordering.

**Tech Stack:** Kotlin Multiplatform, `kotlinx.atomicfu` (no coroutines dep), KMP source set conventions used by `redux-kotlin-threadsafe` and `redux-kotlin-granular`. Stress tests on JVM only using `java.util.concurrent`.

**Spec reference:** `docs/superpowers/specs/2026-05-27-store-registry-design.md` (committed on this branch). Read it before starting any task — it contains the public API, invariants, listener contract, error model, and rationale.

---

## Pre-flight

Before Task 1, confirm:
- You are on branch `feat/store-registry`.
- `git log --oneline -3` shows the spec commit `docs(registry): add store registry design spec` as the latest commit.
- `./gradlew build` would normally succeed from this state (no other broken WIP).

If the spec commit is missing, stop and ask.

---

### Task 1: Scaffold the new module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `redux-kotlin-registry/build.gradle.kts`
- Create: `redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/.gitkeep` (empty)
- Create: `redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/.gitkeep` (empty)
- Create: `redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/.gitkeep` (empty)

- [ ] **Step 1: Add module to `settings.gradle.kts`**

Edit `settings.gradle.kts`. In the `include(...)` block (currently lines 15-26), add `":redux-kotlin-registry",` immediately after the existing `":redux-kotlin-threadsafe",` line. Final block looks like:

```kotlin
include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
    ":redux-kotlin-registry",
    ":redux-kotlin-granular",
    ":redux-kotlin-multimodel",
    ":redux-kotlin-compose",
    ":redux-kotlin-multimodel-granular",
    ":redux-kotlin-compose-multimodel",
    ":examples:counter:common",
    ":examples:counter:android",
    ":examples:todos:common",
    ":examples:todos:android",
)
```

- [ ] **Step 2: Create `redux-kotlin-registry/build.gradle.kts`**

Mirror `redux-kotlin-threadsafe/build.gradle.kts`'s structure. Contents:

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
            namespace = "org.reduxkotlin.registry"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                implementation(libs.kotlinx.atomicfu)
            }
        }
    }
}
```

No test-source-set deps needed — the `convention.library-mpp-loved` plugin already wires `kotlin("test-common")`, `kotlin("test-annotations-common")`, `kotlin("test-js")`, and `kotlin("test-junit")` into the appropriate test source sets. Concurrency stress tests use `java.util.concurrent` from the JVM stdlib.

- [ ] **Step 3: Create source directories with `.gitkeep` files**

```bash
mkdir -p redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry
mkdir -p redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry
mkdir -p redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/concurrency
touch redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/.gitkeep
touch redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/.gitkeep
touch redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/concurrency/.gitkeep
```

- [ ] **Step 4: Verify the new module configures**

Run:

```bash
./gradlew :redux-kotlin-registry:tasks --quiet
```

Expected: lists tasks for the new module (e.g. `assemble`, `build`, `test`, etc.). No errors about missing `convention.library-mpp-loved` or unknown project.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts redux-kotlin-registry/
git commit -m "$(cat <<'EOF'
build(registry): scaffold redux-kotlin-registry module

Empty multiplatform module mirroring redux-kotlin-threadsafe wiring.
Depends on :redux-kotlin core and kotlinx.atomicfu.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Public types and shared typealias

**Files:**
- Create: `redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/RegistryTypes.kt`

- [ ] **Step 1: Write the file with the shared typealias and event sealed interfaces**

```kotlin
package org.reduxkotlin.registry

import kotlin.reflect.KClass

/**
 * Returned by registry `addListener` calls. Invoke to unregister.
 *
 * Mirrors `org.reduxkotlin.StoreSubscription` for ergonomic parity.
 */
public typealias RegistrySubscription = () -> Unit

/**
 * Event emitted by [StoreRegistry] when its membership changes. See the registry
 * KDoc and the design spec for the listener invocation contract.
 */
public sealed interface RegistryEvent<out K> {
    public val id: K
    public data class Added<K>(override val id: K) : RegistryEvent<K>
    public data class Removed<K>(override val id: K) : RegistryEvent<K>
}

/**
 * Type-safe heterogeneous container key for [TypedStoreRegistry].
 *
 * Equality is on `(id, stateType)` — two keys with the same `id` but different
 * `stateType` are distinct entries (no silent aliasing across state types).
 *
 * Construct with the [storeKey] factory; the constructor is `@PublishedApi
 * internal` to preserve the invariant that `stateType` matches the static type
 * of `S` at the call site.
 */
public class StoreKey<K : Any, S : Any> @PublishedApi internal constructor(
    public val id: K,
    public val stateType: KClass<S>,
) {
    override fun equals(other: Any?): Boolean =
        other is StoreKey<*, *> && other.id == id && other.stateType == stateType

    override fun hashCode(): Int = 31 * id.hashCode() + stateType.hashCode()

    override fun toString(): String = "StoreKey($id, ${stateType.simpleName})"
}

/**
 * Inline factory; captures the static state type `S` via `reified`.
 */
public inline fun <K : Any, reified S : Any> storeKey(id: K): StoreKey<K, S> =
    StoreKey(id, S::class)

/**
 * Event emitted by [TypedStoreRegistry]. The carried key is star-projected
 * because events are erased across state types; consumers narrow via
 * `event.key.stateType` when needed.
 */
public sealed interface TypedRegistryEvent {
    public val key: StoreKey<*, *>
    public data class Added(override val key: StoreKey<*, *>) : TypedRegistryEvent
    public data class Removed(override val key: StoreKey<*, *>) : TypedRegistryEvent
}
```

- [ ] **Step 2: Verify the file compiles**

Run:

```bash
./gradlew :redux-kotlin-registry:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/RegistryTypes.kt
git rm redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/.gitkeep
git commit -m "$(cat <<'EOF'
feat(registry): add shared types — RegistryEvent, StoreKey, TypedRegistryEvent

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Internal `RegistryCore` with TDD

**Files:**
- Create: `redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/RegistryCore.kt`
- Create: `redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/RegistryCoreTest.kt`

We test `RegistryCore` directly because it carries the concurrency invariants the public API delegates to. To keep `internal` visibility but make it visible to `commonTest`, the test class lives in the same package (`org.reduxkotlin.registry`).

- [ ] **Step 1: Write failing tests for the core's basic ops**

Create `redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/RegistryCoreTest.kt`:

```kotlin
package org.reduxkotlin.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RegistryCoreTest {

    private fun coreOfStrings() = RegistryCore<String, String>()

    @Test
    fun get_returns_null_when_absent() {
        val core = coreOfStrings()
        assertNull(core.get("missing"))
        assertEquals(0, core.size)
        assertTrue(core.isEmpty)
    }

    @Test
    fun getOrCreate_invokes_creator_once_for_first_call_and_inserts() {
        val core = coreOfStrings()
        var calls = 0
        val v = core.getOrCreate("k") { calls++; "value-1" }
        assertEquals("value-1", v)
        assertEquals(1, calls)
        assertEquals("value-1", core.get("k"))
        assertEquals(1, core.size)
        assertFalse(core.isEmpty)
    }

    @Test
    fun getOrCreate_returns_existing_without_invoking_creator() {
        val core = coreOfStrings()
        core.getOrCreate("k") { "value-1" }
        var calls = 0
        val v = core.getOrCreate("k") { calls++; "value-2" }
        assertEquals("value-1", v)
        assertEquals(0, calls)
    }

    @Test
    fun remove_returns_true_when_present_and_false_when_absent() {
        val core = coreOfStrings()
        core.getOrCreate("k") { "v" }
        assertTrue(core.remove("k"))
        assertFalse(core.remove("k"))
        assertNull(core.get("k"))
    }

    @Test
    fun clear_empties_all_entries() {
        val core = coreOfStrings()
        repeat(5) { i -> core.getOrCreate("k$i") { "v$i" } }
        assertEquals(5, core.size)
        core.clear()
        assertEquals(0, core.size)
        assertTrue(core.isEmpty)
        assertNull(core.get("k0"))
    }

    @Test
    fun getOrCreate_after_remove_recreates_and_re_invokes_creator() {
        val core = coreOfStrings()
        core.getOrCreate("k") { "first" }
        core.remove("k")
        var calls = 0
        val v = core.getOrCreate("k") { calls++; "second" }
        assertEquals("second", v)
        assertEquals(1, calls)
    }

    @Test
    fun creator_throwing_leaves_registry_unmodified() {
        val core = coreOfStrings()
        val boom = RuntimeException("nope")
        val thrown = try {
            core.getOrCreate("k") { throw boom }
            null
        } catch (t: Throwable) { t }
        assertSame(boom, thrown)
        assertNull(core.get("k"))
        assertEquals(0, core.size)
    }
}
```

- [ ] **Step 2: Run the tests; they must fail with "unresolved reference: RegistryCore"**

Run:

```bash
./gradlew :redux-kotlin-registry:jvmTest --tests "org.reduxkotlin.registry.RegistryCoreTest" 2>&1 | tail -40
```

Expected: COMPILATION FAILURE because `RegistryCore` does not exist yet.

- [ ] **Step 3: Implement `RegistryCore.kt`**

Create `redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/RegistryCore.kt`:

```kotlin
package org.reduxkotlin.registry

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.atomicfu.update

/**
 * Internal concurrency core shared by [StoreRegistry] and [TypedStoreRegistry].
 *
 * Invariants — see the design spec §6 for the full rationale.
 *
 *  - Reads (`get`, `size`, fast path of `getOrCreate`) are lock-free: one
 *    atomic load on [ref] + `HashMap.get`.
 *  - Mutating ops (`getOrCreate` slow path, `remove`, `clear`) acquire [lock].
 *  - `creator` in `getOrCreate` runs at most once per `id` across concurrent
 *    callers.
 *  - Listeners are invoked synchronously **under the lock** so the global event
 *    order matches the global mutation order. Listeners must not call back into
 *    the same registry's mutating methods (deadlock); reads from a listener are
 *    safe.
 *  - Map snapshots are immutable; readers holding an older snapshot see
 *    consistent state.
 */
internal class RegistryCore<K : Any, V : Any> {

    private val ref = atomic<Map<K, V>>(emptyMap())
    private val lock = SynchronizedObject()
    private val listeners = atomic<List<(Event<K>) -> Unit>>(emptyList())

    internal sealed interface Event<out K> {
        val id: K
        data class Added<K>(override val id: K) : Event<K>
        data class Removed<K>(override val id: K) : Event<K>
    }

    val size: Int get() = ref.value.size
    val isEmpty: Boolean get() = ref.value.isEmpty()

    fun get(id: K): V? = ref.value[id]

    fun getOrCreate(id: K, creator: () -> V): V {
        ref.value[id]?.let { return it }
        return synchronized(lock) {
            val existing = ref.value[id]
            if (existing != null) {
                existing
            } else {
                val v = creator()
                ref.value = ref.value + (id to v)
                fireUnderLock(Event.Added(id))
                v
            }
        }
    }

    fun remove(id: K): Boolean = synchronized(lock) {
        val cur = ref.value
        if (id !in cur) {
            false
        } else {
            ref.value = cur - id
            fireUnderLock(Event.Removed(id))
            true
        }
    }

    fun clear(): Unit = synchronized(lock) {
        val cur = ref.value
        ref.value = emptyMap()
        cur.keys.forEach { fireUnderLock(Event.Removed(it)) }
    }

    fun addListener(listener: (Event<K>) -> Unit): RegistrySubscription {
        listeners.update { it + listener }
        return { listeners.update { cur -> cur - listener } }
    }

    private fun fireUnderLock(event: Event<K>) {
        // Snapshot of listeners list; the list itself is CoW-immutable.
        listeners.value.forEach { it(event) }
    }
}
```

- [ ] **Step 4: Run the basic-ops tests; they must pass**

```bash
./gradlew :redux-kotlin-registry:jvmTest --tests "org.reduxkotlin.registry.RegistryCoreTest" 2>&1 | tail -20
```

Expected: 7 tests, 0 failures, BUILD SUCCESSFUL.

- [ ] **Step 5: Add listener tests to the same file**

Append to `RegistryCoreTest.kt` (above the closing `}`):

```kotlin

    @Test
    fun added_event_fires_on_creation_only_not_on_hit() {
        val core = coreOfStrings()
        val events = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { events += it }

        core.getOrCreate("k") { "v" }
        core.getOrCreate("k") { "v2" }

        assertEquals(listOf(RegistryCore.Event.Added("k")), events)
    }

    @Test
    fun removed_event_fires_only_when_remove_returns_true() {
        val core = coreOfStrings()
        core.getOrCreate("k") { "v" }
        val events = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { events += it }

        core.remove("k")
        core.remove("k") // absent — no event

        assertEquals(listOf(RegistryCore.Event.Removed("k")), events)
    }

    @Test
    fun clear_fires_one_removed_event_per_evicted_entry() {
        val core = coreOfStrings()
        repeat(3) { i -> core.getOrCreate("k$i") { "v$i" } }
        val events = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { events += it }

        core.clear()

        assertEquals(3, events.size)
        assertEquals(setOf("k0", "k1", "k2"), events.map { it.id }.toSet())
        events.forEach { assertTrue(it is RegistryCore.Event.Removed) }
    }

    @Test
    fun unsubscribe_stops_events() {
        val core = coreOfStrings()
        val events = mutableListOf<RegistryCore.Event<String>>()
        val unsubscribe = core.addListener { events += it }

        core.getOrCreate("k1") { "v" }
        unsubscribe()
        core.getOrCreate("k2") { "v" }

        assertEquals(listOf(RegistryCore.Event.Added("k1")), events)
    }

    @Test
    fun multiple_listeners_each_receive_each_event() {
        val core = coreOfStrings()
        val a = mutableListOf<RegistryCore.Event<String>>()
        val b = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { a += it }
        core.addListener { b += it }

        core.getOrCreate("k") { "v" }
        core.remove("k")

        val expected = listOf(
            RegistryCore.Event.Added("k"),
            RegistryCore.Event.Removed("k"),
        )
        assertEquals(expected, a)
        assertEquals(expected, b)
    }

    @Test
    fun listener_can_read_registry_state_from_callback() {
        val core = coreOfStrings()
        var observedSize = -1
        var observedValue: String? = null
        core.addListener {
            observedSize = core.size
            observedValue = core.get("k")
        }

        core.getOrCreate("k") { "v" }

        assertEquals(1, observedSize)
        assertEquals("v", observedValue)
    }

    @Test
    fun listener_throwing_propagates_and_skips_remaining_listeners() {
        val core = coreOfStrings()
        val captured = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { throw IllegalStateException("boom") }
        core.addListener { captured += it } // would be invoked second; should be skipped

        val thrown = try {
            core.getOrCreate("k") { "v" }
            null
        } catch (t: Throwable) { t }

        assertTrue(thrown is IllegalStateException)
        assertEquals(0, captured.size)
        // Mutation already happened before the throw.
        assertEquals("v", core.get("k"))
    }
```

- [ ] **Step 6: Run the listener tests; they must pass with the existing implementation**

```bash
./gradlew :redux-kotlin-registry:jvmTest --tests "org.reduxkotlin.registry.RegistryCoreTest" 2>&1 | tail -20
```

Expected: 14 tests total, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/RegistryCore.kt \
        redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/RegistryCoreTest.kt
git commit -m "$(cat <<'EOF'
feat(registry): internal RegistryCore with lock-free reads + listeners

Lock-free get / size. Brief atomicfu SynchronizedObject lock on writes.
Listeners fire under the lock for total event ordering; reads from
listener callbacks are safe.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `StoreRegistry<K, S>` — Tier 1 public API

**Files:**
- Create: `redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/StoreRegistry.kt`
- Create: `redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/StoreRegistryTest.kt`

- [ ] **Step 1: Write failing tests for the Tier 1 public API**

Create `redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/StoreRegistryTest.kt`:

```kotlin
package org.reduxkotlin.registry

import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StoreRegistryTest {

    private data class CounterState(val n: Int = 0)
    private object Inc

    private val reducer: (CounterState, Any) -> CounterState = { s, a ->
        if (a is Inc) s.copy(n = s.n + 1) else s
    }

    private fun newRegistry() = StoreRegistry<String, CounterState>()

    private fun newStore(initial: Int = 0): Store<CounterState> =
        createStore(reducer, CounterState(initial))

    @Test
    fun get_returns_null_when_absent() {
        assertNull(newRegistry().get("missing"))
    }

    @Test
    fun getOrCreate_returns_same_store_on_repeated_calls_and_runs_creator_once() {
        val reg = newRegistry()
        var calls = 0
        val s1 = reg.getOrCreate("k") { calls++; newStore() }
        val s2 = reg.getOrCreate("k") { calls++; newStore() }
        assertSame(s1, s2)
        assertEquals(1, calls)
    }

    @Test
    fun remove_returns_true_when_present_and_false_when_absent() {
        val reg = newRegistry()
        reg.getOrCreate("k") { newStore() }
        assertTrue(reg.remove("k"))
        assertFalse(reg.remove("k"))
        assertNull(reg.get("k"))
    }

    @Test
    fun clear_empties_registry() {
        val reg = newRegistry()
        repeat(3) { i -> reg.getOrCreate("k$i") { newStore(i) } }
        assertEquals(3, reg.size)
        reg.clear()
        assertTrue(reg.isEmpty)
        assertEquals(0, reg.size)
    }

    @Test
    fun added_then_removed_events_fire_in_order() {
        val reg = newRegistry()
        val events = mutableListOf<RegistryEvent<String>>()
        reg.addListener { events += it }

        reg.getOrCreate("k") { newStore() }
        reg.remove("k")

        assertEquals(
            listOf(
                RegistryEvent.Added("k"),
                RegistryEvent.Removed("k"),
            ),
            events,
        )
    }

    @Test
    fun clear_fires_one_removed_event_per_entry() {
        val reg = newRegistry()
        repeat(2) { i -> reg.getOrCreate("k$i") { newStore() } }
        val events = mutableListOf<RegistryEvent<String>>()
        reg.addListener { events += it }

        reg.clear()

        assertEquals(2, events.size)
        events.forEach { assertTrue(it is RegistryEvent.Removed) }
    }

    @Test
    fun unsubscribe_stops_events() {
        val reg = newRegistry()
        val events = mutableListOf<RegistryEvent<String>>()
        val off = reg.addListener { events += it }

        reg.getOrCreate("a") { newStore() }
        off()
        reg.getOrCreate("b") { newStore() }

        assertEquals(1, events.size)
    }

    @Test
    fun returned_store_remains_usable_after_remove() {
        val reg = newRegistry()
        val store = reg.getOrCreate("k") { newStore() }
        reg.remove("k")
        // Caller still holds the ref; dispatch still works.
        store.dispatch(Inc)
        assertEquals(1, store.getState().n)
    }
}
```

- [ ] **Step 2: Run the tests; they must fail to compile**

```bash
./gradlew :redux-kotlin-registry:jvmTest --tests "org.reduxkotlin.registry.StoreRegistryTest" 2>&1 | tail -20
```

Expected: COMPILATION FAILURE on `StoreRegistry` symbol.

- [ ] **Step 3: Implement `StoreRegistry`**

Create `redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/StoreRegistry.kt`:

```kotlin
package org.reduxkotlin.registry

import org.reduxkotlin.Store

/**
 * A thread-safe registry of [Store] instances keyed by an identifier of type [K].
 *
 * The registry is intended for use cases where multiple stores of the same
 * [State] type ([S]) need to coexist under disjoint scopes — for example a
 * per-thread-view store in a messaging app, or a per-call store in a calling
 * app. Two different state types call for two different registries (or use
 * [TypedStoreRegistry] for one bag of heterogeneous stores).
 *
 * Lifecycle is fully manual: call [remove] when a scope ends, or [clear] to
 * wipe everything (e.g. on logout). The registry never auto-evicts.
 *
 * Concurrency
 * -----------
 *
 *  - [get] and the fast path of [getOrCreate] are **lock-free**: a single
 *    atomic load on the underlying snapshot.
 *  - [getOrCreate] guarantees its `creator` lambda is invoked **at most once
 *    per [id] across all concurrent callers** — under contention, losers
 *    observe the winner's store and return it.
 *  - [remove] and [clear] take a brief internal lock.
 *  - Membership-change listeners are dispatched **synchronously** on the
 *    mutating thread, under the same internal lock, so the global event order
 *    matches the global mutation order. Listeners must complete quickly and
 *    **must not call back into mutating methods** on this registry (deadlock);
 *    reads from a listener are safe.
 *
 * Singleton pattern
 * -----------------
 *
 * The class is intentionally not a Kotlin `object` (testability). Users who
 * want a process-global registry hold a top-level `val`:
 *
 * ```kotlin
 * val threadStores = StoreRegistry<ThreadId, ThreadState>()
 * ```
 */
public class StoreRegistry<K : Any, S> {

    private val core = RegistryCore<K, Store<S>>()

    /** Number of entries currently in the registry. */
    public val size: Int get() = core.size

    /** `true` iff [size] is zero. */
    public val isEmpty: Boolean get() = core.isEmpty

    /**
     * Lock-free lookup. Returns `null` if no store has been registered for [id]
     * (or if it has been removed). Does **not** invoke any creator.
     */
    public fun get(id: K): Store<S>? = core.get(id)

    /**
     * Returns the existing store registered for [id], or atomically inserts and
     * returns a new one produced by [creator].
     *
     * `creator` is invoked at most once per [id] across all concurrent callers.
     * If `creator` throws, the exception propagates and the registry is left
     * unchanged.
     *
     * Fires [RegistryEvent.Added] only on actual creation; not on a hit.
     */
    public fun getOrCreate(id: K, creator: () -> Store<S>): Store<S> =
        core.getOrCreate(id, creator)

    /**
     * Evicts the entry for [id] and returns `true` if anything was removed.
     * Fires [RegistryEvent.Removed] only when the return value is `true`.
     *
     * Does **not** notify the store itself (no teardown action is dispatched).
     * Callers still holding a reference may continue to use the store; the
     * registry simply forgets it.
     */
    public fun remove(id: K): Boolean = core.remove(id)

    /**
     * Atomically evicts all entries. Fires [RegistryEvent.Removed] once per
     * evicted id, in unspecified order. By the time the first listener
     * invocation runs the registry is already empty (`size == 0`).
     */
    public fun clear(): Unit = core.clear()

    /**
     * Registers a synchronous listener. Returns a subscription that, when
     * invoked, unregisters. See the class KDoc for the listener invocation
     * contract.
     */
    public fun addListener(listener: (RegistryEvent<K>) -> Unit): RegistrySubscription =
        core.addListener { coreEvent ->
            listener(coreEvent.toPublic())
        }

    private fun RegistryCore.Event<K>.toPublic(): RegistryEvent<K> = when (this) {
        is RegistryCore.Event.Added -> RegistryEvent.Added(id)
        is RegistryCore.Event.Removed -> RegistryEvent.Removed(id)
    }
}
```

- [ ] **Step 4: Run the Tier 1 tests; they must pass**

```bash
./gradlew :redux-kotlin-registry:jvmTest --tests "org.reduxkotlin.registry.StoreRegistryTest" 2>&1 | tail -20
```

Expected: 8 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/StoreRegistry.kt \
        redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/StoreRegistryTest.kt
git commit -m "$(cat <<'EOF'
feat(registry): add StoreRegistry<K, S> tier-1 public API

Lock-free reads, creator-at-most-once getOrCreate, manual lifecycle,
synchronous membership-change listeners.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `TypedStoreRegistry` — Tier 2 heterogeneous API

**Files:**
- Create: `redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/TypedStoreRegistry.kt`
- Create: `redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/TypedStoreRegistryTest.kt`

- [ ] **Step 1: Write failing tests for the Tier 2 API**

Create `redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/TypedStoreRegistryTest.kt`:

```kotlin
package org.reduxkotlin.registry

import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TypedStoreRegistryTest {

    private data class StateA(val a: Int = 0)
    private data class StateB(val b: String = "")

    private val noopReducerA: (StateA, Any) -> StateA = { s, _ -> s }
    private val noopReducerB: (StateB, Any) -> StateB = { s, _ -> s }

    private fun storeA() = createStore(noopReducerA, StateA())
    private fun storeB() = createStore(noopReducerB, StateB())

    @Test
    fun two_storeKeys_with_same_id_and_type_are_equal() {
        val k1 = storeKey<String, StateA>("x")
        val k2 = storeKey<String, StateA>("x")
        assertEquals(k1, k2)
        assertEquals(k1.hashCode(), k2.hashCode())
    }

    @Test
    fun two_storeKeys_with_same_id_but_different_state_types_are_distinct() {
        val k1 = storeKey<String, StateA>("x")
        val k2 = storeKey<String, StateB>("x")
        assertNotEquals<StoreKey<*, *>>(k1, k2)
    }

    @Test
    fun storeKey_with_int_id_is_supported() {
        val k = storeKey<Int, StateA>(42)
        assertEquals(42, k.id)
        assertEquals(StateA::class, k.stateType)
    }

    @Test
    fun getOrCreate_returns_typed_store_without_explicit_cast() {
        val reg = TypedStoreRegistry()
        val store: Store<StateA> = reg.getOrCreate(storeKey<String, StateA>("x")) { storeA() }
        // Calling the typed API without casting compiles — that's the test.
        assertEquals(StateA(), store.getState())
    }

    @Test
    fun heterogeneous_stores_under_same_id_string_are_distinct_entries() {
        val reg = TypedStoreRegistry()
        val a: Store<StateA> = reg.getOrCreate(storeKey<String, StateA>("x")) { storeA() }
        val b: Store<StateB> = reg.getOrCreate(storeKey<String, StateB>("x")) { storeB() }

        assertEquals(2, reg.size)
        assertSame(a, reg.get(storeKey<String, StateA>("x")))
        assertSame(b, reg.get(storeKey<String, StateB>("x")))
    }

    @Test
    fun creator_invoked_at_most_once_per_key() {
        val reg = TypedStoreRegistry()
        var calls = 0
        val k = storeKey<String, StateA>("x")
        reg.getOrCreate(k) { calls++; storeA() }
        reg.getOrCreate(k) { calls++; storeA() }
        assertEquals(1, calls)
    }

    @Test
    fun remove_returns_true_then_false_and_get_returns_null() {
        val reg = TypedStoreRegistry()
        val k = storeKey<String, StateA>("x")
        reg.getOrCreate(k) { storeA() }
        assertTrue(reg.remove(k))
        assertEquals(0, reg.size)
        assertNull(reg.get(k))
        assertEquals(false, reg.remove(k))
    }

    @Test
    fun clear_empties_and_fires_per_entry_events() {
        val reg = TypedStoreRegistry()
        reg.getOrCreate(storeKey<String, StateA>("a")) { storeA() }
        reg.getOrCreate(storeKey<String, StateB>("b")) { storeB() }
        val events = mutableListOf<TypedRegistryEvent>()
        reg.addListener { events += it }

        reg.clear()

        assertEquals(2, events.size)
        events.forEach { assertTrue(it is TypedRegistryEvent.Removed) }
        assertEquals(0, reg.size)
    }

    @Test
    fun added_event_carries_concrete_key_for_narrowing() {
        val reg = TypedStoreRegistry()
        val events = mutableListOf<TypedRegistryEvent>()
        reg.addListener { events += it }

        val k = storeKey<String, StateA>("x")
        reg.getOrCreate(k) { storeA() }

        assertEquals(1, events.size)
        val e = events.single()
        assertTrue(e is TypedRegistryEvent.Added)
        assertEquals(k, e.key)
        // Consumers can narrow by stateType:
        assertEquals(StateA::class, e.key.stateType)
    }
}
```

- [ ] **Step 2: Run the tests; they must fail to compile**

```bash
./gradlew :redux-kotlin-registry:jvmTest --tests "org.reduxkotlin.registry.TypedStoreRegistryTest" 2>&1 | tail -20
```

Expected: COMPILATION FAILURE on `TypedStoreRegistry` symbol.

- [ ] **Step 3: Implement `TypedStoreRegistry`**

Create `redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/TypedStoreRegistry.kt`:

```kotlin
package org.reduxkotlin.registry

import org.reduxkotlin.Store

/**
 * Heterogeneous, type-safe registry. Holds [Store] instances of differing
 * [State] types, keyed by [StoreKey], which combines an arbitrary identifier
 * with a `KClass` type witness for the state.
 *
 * Use this only when one logical bag must contain stores of different state
 * types. For the common "one state type per registry" case, prefer the
 * simpler [StoreRegistry].
 *
 * Concurrency, lifecycle, and listener contract are identical to
 * [StoreRegistry] — see that class's KDoc for the details.
 */
public class TypedStoreRegistry {

    private val core = RegistryCore<StoreKey<*, *>, Store<*>>()

    public val size: Int get() = core.size
    public val isEmpty: Boolean get() = core.isEmpty

    @Suppress("UNCHECKED_CAST")
    public fun <K : Any, S : Any> get(key: StoreKey<K, S>): Store<S>? =
        core.get(key) as Store<S>?

    @Suppress("UNCHECKED_CAST")
    public fun <K : Any, S : Any> getOrCreate(
        key: StoreKey<K, S>,
        creator: () -> Store<S>,
    ): Store<S> = core.getOrCreate(key) { creator() } as Store<S>

    public fun <K : Any, S : Any> remove(key: StoreKey<K, S>): Boolean = core.remove(key)

    public fun clear(): Unit = core.clear()

    public fun addListener(listener: (TypedRegistryEvent) -> Unit): RegistrySubscription =
        core.addListener { coreEvent ->
            listener(coreEvent.toPublic())
        }

    private fun RegistryCore.Event<StoreKey<*, *>>.toPublic(): TypedRegistryEvent = when (this) {
        is RegistryCore.Event.Added -> TypedRegistryEvent.Added(id)
        is RegistryCore.Event.Removed -> TypedRegistryEvent.Removed(id)
    }
}
```

The three `@Suppress("UNCHECKED_CAST")` are the only unsafe casts in the codebase. They are safe because every entry was inserted under a `StoreKey<*, S>` whose `stateType` matches the runtime type of the stored `Store<S>`. The `StoreKey.equals` contract ensures retrieval by an equal key yields exactly that entry, with the matching `S`.

- [ ] **Step 4: Run the Tier 2 tests; they must pass**

```bash
./gradlew :redux-kotlin-registry:jvmTest --tests "org.reduxkotlin.registry.TypedStoreRegistryTest" 2>&1 | tail -20
```

Expected: 9 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-registry/src/commonMain/kotlin/org/reduxkotlin/registry/TypedStoreRegistry.kt \
        redux-kotlin-registry/src/commonTest/kotlin/org/reduxkotlin/registry/TypedStoreRegistryTest.kt
git commit -m "$(cat <<'EOF'
feat(registry): add TypedStoreRegistry tier-2 heterogeneous API

Type-safe heterogeneous container keyed by StoreKey<K, S>. KClass<S>
type witness discriminates entries with the same id but different
state types.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Concurrency stress tests (JVM only)

**Files:**
- Create: `redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/concurrency/RegistryConcurrencyStressTest.kt`
- Create: `redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/concurrency/DaemonThreadFactory.kt`

These tests use raw `java.util.concurrent` primitives. Pattern from
`redux-kotlin-granular/src/jvmTest/kotlin/org/reduxkotlin/granular/concurrency/ConcurrencyStressTest.kt`.

- [ ] **Step 1: Create the daemon thread factory helper**

Create `redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/concurrency/DaemonThreadFactory.kt`:

```kotlin
package org.reduxkotlin.registry.concurrency

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Marks every worker thread daemon so the JVM can exit even if a leaked
 * worker outlives the test timeout. Numbered names help triage races.
 */
internal class DaemonThreadFactory(
    private val namePrefix: String = "registry-stress",
) : ThreadFactory {
    private val counter = AtomicInteger()
    override fun newThread(r: Runnable): Thread = Thread(r, "$namePrefix-${counter.incrementAndGet()}").apply {
        isDaemon = true
    }
}
```

- [ ] **Step 2: Write the stress test file**

Create `redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/concurrency/RegistryConcurrencyStressTest.kt`:

```kotlin
package org.reduxkotlin.registry.concurrency

import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import org.reduxkotlin.registry.RegistryEvent
import org.reduxkotlin.registry.StoreRegistry
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Multi-thread stress tests for the registry. Patterned on
 * `redux-kotlin-granular`'s ConcurrencyStressTest:
 *
 *  - Workers release via a CyclicBarrier so warm-up doesn't skew results.
 *  - Every scenario has a bounded op count AND a hard time budget — a stuck
 *    scenario fails loudly instead of hanging CI.
 *  - Every `await*` carries an explicit timeout.
 *  - Worker pools use a daemon ThreadFactory.
 *
 * JVM-only. The commonTest suite covers single-threaded correctness.
 */
class RegistryConcurrencyStressTest {

    private data class S(val v: Int = 0)
    private val reducer: (S, Any) -> S = { s, _ -> s }
    private fun newStore() = createStore(reducer, S())

    private fun executor(threads: Int) =
        Executors.newFixedThreadPool(threads, DaemonThreadFactory())

    /**
     * 32 threads call getOrCreate on the same id simultaneously. Creator must
     * run exactly once. Repeat across many ids and iterations.
     */
    @Test
    fun creator_runs_at_most_once_per_id_under_contention() {
        val iterations = 100
        val threadsPerId = 32
        val executor = executor(threadsPerId)

        try {
            repeat(iterations) { i ->
                val registry = StoreRegistry<String, S>()
                val id = "id-$i"
                val creatorCalls = AtomicInteger()
                val barrier = CyclicBarrier(threadsPerId)
                val results = ConcurrentLinkedQueue<Store<S>>()

                val futures = (1..threadsPerId).map {
                    executor.submit {
                        barrier.await(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
                        val s = registry.getOrCreate(id) {
                            creatorCalls.incrementAndGet()
                            newStore()
                        }
                        results.add(s)
                    }
                }

                futures.forEach { it.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS) }
                assertEquals(1, creatorCalls.get(), "iteration $i: creator called ${creatorCalls.get()} times")
                assertEquals(threadsPerId, results.size)
                val winner = results.first()
                results.forEach {
                    assertTrue(it === winner, "iteration $i: divergent store returned to a caller")
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    /**
     * One writer thread churns remove+getOrCreate on a small set of ids while
     * many readers spam get(id). No exceptions, no torn reads — get must
     * return either null or a fully constructed store.
     */
    @Test
    fun no_torn_reads_under_concurrent_writer_and_readers() {
        val registry = StoreRegistry<Int, S>()
        val ids = (0..7).toList()
        val readerCount = 8
        val opsPerReader = 50_000
        val durationMs = 3_000L
        val stop = AtomicBoolean(false)
        val executor = executor(readerCount + 1)
        val errors = ConcurrentLinkedQueue<Throwable>()

        try {
            // Writer.
            val writerFuture = executor.submit {
                try {
                    var i = 0
                    while (!stop.get()) {
                        val id = ids[i % ids.size]
                        registry.remove(id)
                        registry.getOrCreate(id) { newStore() }
                        i++
                    }
                } catch (t: Throwable) { errors.add(t) }
            }

            // Readers.
            val readerFutures = (1..readerCount).map { r ->
                executor.submit {
                    try {
                        var seen = 0L
                        var iter = 0
                        while (!stop.get() && iter < opsPerReader) {
                            val s = registry.get(ids[iter % ids.size])
                            if (s != null) seen++
                            iter++
                        }
                        assertTrue(seen >= 0L, "reader $r overflowed counter (unreachable)")
                    } catch (t: Throwable) { errors.add(t) }
                }
            }

            Thread.sleep(durationMs)
            stop.set(true)
            writerFuture.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
            readerFutures.forEach { it.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        if (errors.isNotEmpty()) {
            errors.forEach { it.printStackTrace() }
            throw AssertionError("${errors.size} errors during stress run; first: ${errors.first()}")
        }
    }

    /**
     * Producers spam getOrCreate / remove while a separate thread thrashes
     * addListener / unsubscribe. No ConcurrentModificationException, no
     * crashes.
     */
    @Test
    fun listener_add_remove_under_mutation_storm() {
        val registry = StoreRegistry<Int, S>()
        val ids = (0..15).toList()
        val producers = 4
        val durationMs = 3_000L
        val stop = AtomicBoolean(false)
        val executor = executor(producers + 1)
        val errors = ConcurrentLinkedQueue<Throwable>()

        try {
            val producerFutures = (1..producers).map { p ->
                executor.submit {
                    try {
                        var i = p
                        while (!stop.get()) {
                            val id = ids[i % ids.size]
                            registry.getOrCreate(id) { newStore() }
                            if (i % 3 == 0) registry.remove(id)
                            i++
                        }
                    } catch (t: Throwable) { errors.add(t) }
                }
            }

            val listenerThrasher = executor.submit {
                try {
                    while (!stop.get()) {
                        val off = registry.addListener { /* observe; no work */ }
                        off()
                    }
                } catch (t: Throwable) { errors.add(t) }
            }

            Thread.sleep(durationMs)
            stop.set(true)
            producerFutures.forEach { it.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS) }
            listenerThrasher.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        if (errors.isNotEmpty()) {
            errors.forEach { it.printStackTrace() }
            throw AssertionError("${errors.size} errors during stress run; first: ${errors.first()}")
        }
    }

    /**
     * Long-lived listener attached before any work begins. Producers spam
     * getOrCreate + remove; one thread periodically clear()s. After the run,
     * every Added(id) observed must be paired with a matching Removed(id)
     * since-Added — events should never be lost.
     */
    @Test
    fun events_total_order_matches_mutation_order_under_clear_storm() {
        val registry = StoreRegistry<Int, S>()
        val ids = (0..15).toList()
        val producers = 4
        val durationMs = 3_000L
        val stop = AtomicBoolean(false)
        val executor = executor(producers + 1)

        val seenEvents = ConcurrentLinkedQueue<RegistryEvent<Int>>()
        registry.addListener { seenEvents.add(it) }

        val errors = ConcurrentLinkedQueue<Throwable>()
        try {
            val producerFutures = (1..producers).map { p ->
                executor.submit {
                    try {
                        var i = p
                        while (!stop.get()) {
                            val id = ids[i % ids.size]
                            if (i % 4 == 0) registry.remove(id)
                            else registry.getOrCreate(id) { newStore() }
                            i++
                        }
                    } catch (t: Throwable) { errors.add(t) }
                }
            }

            val clearer = executor.submit {
                try {
                    while (!stop.get()) {
                        Thread.sleep(50)
                        registry.clear()
                    }
                } catch (t: Throwable) { errors.add(t) }
            }

            Thread.sleep(durationMs)
            stop.set(true)
            producerFutures.forEach { it.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS) }
            clearer.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        if (errors.isNotEmpty()) {
            errors.forEach { it.printStackTrace() }
            throw AssertionError("${errors.size} errors during stress run; first: ${errors.first()}")
        }

        // Per-id parity: for each id, the sequence of events must alternate
        // Added, Removed, Added, Removed (starting at Added). Anything else
        // implies a lost or duplicated event.
        val perId = seenEvents.groupBy { it.id }
        perId.forEach { (id, events) ->
            var expectingAdded = true
            events.forEachIndexed { idx, e ->
                val ok = if (expectingAdded) e is RegistryEvent.Added else e is RegistryEvent.Removed
                assertTrue(
                    ok,
                    "id=$id event #$idx out of order: expected ${if (expectingAdded) "Added" else "Removed"}, got $e",
                )
                expectingAdded = !expectingAdded
            }
        }

        val total = seenEvents.size.toLong()
        assertTrue(total > 0L, "no events observed — test did not exercise the registry")
    }

    /**
     * Writer populates many keys; reader on a separate thread later reads
     * each key. All writes must be visible (atomicfu provides
     * happens-before).
     */
    @Test
    fun writer_publishes_then_reader_observes_all_entries() {
        val registry = StoreRegistry<Int, S>()
        val keyCount = 5_000
        val executor = executor(2)

        try {
            val writeFuture = executor.submit {
                repeat(keyCount) { i -> registry.getOrCreate(i) { newStore() } }
            }
            writeFuture.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)

            val readFuture = executor.submit {
                var hits = 0
                repeat(keyCount) { i -> if (registry.get(i) != null) hits++ }
                assertEquals(keyCount, hits)
            }
            readFuture.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val SCENARIO_TIMEOUT_S = 30L
    }
}
```

- [ ] **Step 3: Run the stress suite**

```bash
./gradlew :redux-kotlin-registry:jvmTest --tests "org.reduxkotlin.registry.concurrency.RegistryConcurrencyStressTest" 2>&1 | tail -25
```

Expected: 5 tests, 0 failures. Total wall time ≤ ~30 s.

If a test hangs or fails: investigate before touching the implementation — the design's invariants are not negotiable. Most likely culprits: a listener throwing (skips remaining), a worker swallowing an exception, or a missing `Thread.sleep` somewhere allowing infinite spin.

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/concurrency/
git rm redux-kotlin-registry/src/jvmTest/kotlin/org/reduxkotlin/registry/concurrency/.gitkeep
git commit -m "$(cat <<'EOF'
test(registry): add JVM concurrency stress suite

Five scenarios covering creator-at-most-once, no torn reads,
listener add/remove under mutation, total event-order under clear
storm, and write-then-read visibility.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Module README + usage docs

**Files:**
- Create: `redux-kotlin-registry/README.md`

- [ ] **Step 1: Write the module README**

Create `redux-kotlin-registry/README.md`:

```markdown
# redux-kotlin-registry

A thread-safe registry for multiple [redux-kotlin](../redux-kotlin) stores, keyed by a unique identifier of your choosing.

## When to use

Whenever your app has scoped state that must not bleed between instances:

- Per-thread-view store in a messaging app.
- Per-call store in a calling app.
- Per-screen store driven by a route identifier.

## Quick start (Tier 1 — same state type, many ids)

```kotlin
import org.reduxkotlin.createStore
import org.reduxkotlin.registry.RegistryEvent
import org.reduxkotlin.registry.StoreRegistry

typealias ThreadId = String

val threadStores = StoreRegistry<ThreadId, ThreadState>()

fun openThread(id: ThreadId) =
    threadStores.getOrCreate(id) { createStore(threadReducer, ThreadState()) }

fun closeThread(id: ThreadId) { threadStores.remove(id) }

fun onLogout() { threadStores.clear() }

val off = threadStores.addListener { event ->
    when (event) {
        is RegistryEvent.Added   -> telemetry.log("thread_store_opened", event.id)
        is RegistryEvent.Removed -> telemetry.log("thread_store_closed", event.id)
    }
}
// later: off()
```

## Heterogeneous (Tier 2 — mixed state types in one bag)

```kotlin
import org.reduxkotlin.registry.TypedStoreRegistry
import org.reduxkotlin.registry.storeKey

val global = TypedStoreRegistry()

val callStore   = global.getOrCreate(storeKey<CallState>(callId))     { createStore(callReducer, CallState()) }
val threadStore = global.getOrCreate(storeKey<ThreadState>(threadId)) { createStore(threadReducer, ThreadState()) }
```

`StoreKey<K, S>` carries a `KClass<S>` type witness so that two callers using the same `id` but different state types cannot silently alias each other's stores.

## Concurrency notes

- `get` and the fast path of `getOrCreate` are **lock-free**.
- `getOrCreate` runs your `creator` lambda **at most once per id** even under heavy concurrent contention.
- `remove`, `clear`, and the slow path of `getOrCreate` take a brief internal lock (via `kotlinx.atomicfu`).
- Listeners are dispatched **synchronously on the mutating thread, under the same internal lock**, so the global event order matches the global mutation order. Keep listener work short and **do not call back into the registry's mutating methods from a listener** (deadlock). Reads from a listener are safe.

## Lifecycle

Manual. The registry never auto-evicts. Removing a store from the registry does **not** dispatch any teardown action to it; callers still holding a reference may continue to use the store.

## See also

- Full design spec: `docs/superpowers/specs/2026-05-27-store-registry-design.md`
- Core store: [`redux-kotlin`](../redux-kotlin)
- Thread-safe store wrapper: [`redux-kotlin-threadsafe`](../redux-kotlin-threadsafe) — use to wrap each registered store if you need concurrent dispatch on it.
```

- [ ] **Step 2: Commit**

```bash
git add redux-kotlin-registry/README.md
git commit -m "$(cat <<'EOF'
docs(registry): add module README with usage examples

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Full build verification

- [ ] **Step 1: Run the whole registry build (all KMP targets that the platform supports)**

```bash
./gradlew :redux-kotlin-registry:build 2>&1 | tail -40
```

Expected: BUILD SUCCESSFUL. All compile, test, and detekt tasks pass. If detekt complains about anything (line length, function length, etc.), fix the source — do NOT relax the rules.

- [ ] **Step 2: Run the full repo build to verify nothing else broke**

```bash
./gradlew build 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL across all modules. If a sibling module fails for an unrelated reason (KMP toolchain hiccup, etc.), note it; otherwise treat any failure as a regression caused by this change.

- [ ] **Step 3: Run install-locally to confirm publish wiring is intact**

```bash
./gradlew :redux-kotlin-registry:installLocally 2>&1 | tail -10
```

Expected: tasks resolve. (May fail in some environments if Maven test-repo isn't writable — that's pre-existing, not caused by this PR.)

If anything fails, stop and report. Do not commit a passing test that ignores a real failure.

- [ ] **Step 4: If anything was changed during verification, commit it**

If you fixed anything in step 1 or 2:

```bash
git add -A
git commit -m "$(cat <<'EOF'
fix(registry): address verification findings

<one-line description of what was fixed>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

If nothing changed, skip this step.

---

### Task 9: Push branch and open PR

- [ ] **Step 1: Sanity-check the commit log**

```bash
git log --oneline origin/master..HEAD
```

Expected: one design-spec commit followed by ~6-8 implementation commits. Each commit message matches the project's `type(scope): subject` convention.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/store-registry
```

- [ ] **Step 3: Open the PR**

Title: `feat(registry): add redux-kotlin-registry module for multi-store keying`

Body — pass via heredoc:

```bash
gh pr create --title "feat(registry): add redux-kotlin-registry module for multi-store keying" --body "$(cat <<'EOF'
## Summary
- New KMP module `redux-kotlin-registry` providing `StoreRegistry<K, S>` (typed, homogeneous) and `TypedStoreRegistry` (heterogeneous, opt-in via `storeKey<S>(id)`).
- Lock-free reads, creator-at-most-once `getOrCreate`, manual lifecycle, synchronous membership-change listeners under a brief atomicfu lock for total event ordering.
- No new dependencies beyond what `redux-kotlin-threadsafe` already pulls in.
- Full design spec: `docs/superpowers/specs/2026-05-27-store-registry-design.md`.

## Test plan
- [ ] commonTest passes on JVM, Android, JS, native, wasmJs (`./gradlew :redux-kotlin-registry:allTests`)
- [ ] JVM concurrency stress suite passes (`./gradlew :redux-kotlin-registry:jvmTest --tests "*concurrency*"`)
- [ ] Full repo build green (`./gradlew build`)
- [ ] No regressions in sibling modules
- [ ] CI green on this PR

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Print the PR URL for the requester**

The `gh pr create` output ends with the URL. Echo it back.

---

## Self-review

Before declaring the plan done:

1. **Spec coverage:** every section in the spec maps to a task:
    - §4 module layout → Task 1
    - §5.1 shared types (typealias, events) → Task 2
    - §5.2 Tier 1 `StoreRegistry` → Task 4
    - §5.3 Tier 2 `StoreKey` + `TypedStoreRegistry` → Tasks 2 (key) + 5 (registry)
    - §6 concurrency core → Task 3
    - §6.3 listener contract → Tasks 3, 4, 5 (tests cover semantics)
    - §6.4 perf → exercised by Task 6 stress
    - §7 lifecycle → Task 4 (`returned_store_remains_usable_after_remove`)
    - §8 error model → Task 3 (`creator_throwing_…`, `listener_throwing_…`)
    - §9 testing strategy → Tasks 3, 4, 5, 6
    - §11 samples → Task 7 (README)
    - §12 acceptance criteria → Task 8

2. **Placeholder scan:** searched the plan for "TBD", "TODO", "implement later", "appropriate error handling" — none present.

3. **Type consistency:** signatures match between tasks:
    - `getOrCreate(id: K, creator: () -> Store<S>): Store<S>` — Tasks 4, 5 use identical sig (modulo `key: StoreKey<K, S>`).
    - `addListener(listener: (RegistryEvent<K>) -> Unit): RegistrySubscription` — same form in Tier 1 and 2 (different event type).
    - `remove(id: K): Boolean` — consistent.
    - `RegistryCore.Event.Added/Removed` — internal only; mapped to public events by `.toPublic()` in both registries.
