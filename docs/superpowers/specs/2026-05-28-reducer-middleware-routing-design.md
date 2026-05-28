# RFC: Routed reducer/middleware API for redux-kotlin

- **Status:** Draft (comparative RFC, pre-implementation)
- **Date:** 2026-05-28
- **Scope:** Design exploration + recommended path. No code committed yet.
- **Constraints (locked with requester):** opt-in/additive primary (document breaking-2.0 upside separately); must work on **all** current KMP targets without runtime-reflection-only solutions; forward-looking (no hard perf numbers yet — flag where measurement is mandatory); the three goals (ergonomics, routing-perf, lazy-load/memory) were initially co-equal but are **re-weighted below** after expert review.

This RFC was stress-tested by five expert review lenses (correctness, performance, gaps/completeness, Android, Kotlin Multiplatform), all grounded in the repository. Their findings are folded into the design decisions and traced in [§11](#11-review-findings--resolutions).

---

## 1. Context & goals

### Current machinery (verified against the repo)

- `Reducer<State> = (State, action: Any) -> State`. The action type is `Any` (`Definitions.kt:6,12`).
- `combineReducers(vararg)` is a left fold: **every child reducer runs on every action** (`CombineReducers.kt:6-8`).
- `combineModelReducers` (multimodel) iterates **every model reducer for every action**, using `===` referential change-detection to rebuild `ModelState` only with changed models; returns the *same* `ModelState` instance when nothing changed (`CombineModelReducers.kt:54-82`).
- `typedReducer` filters internally via `when(action){ is X -> … else -> state }` — i.e. **subtype** matching (`Definitions.kt:144-151`).
- `createStore.dispatch` runs `currentState = currentReducer(state, action)` then notifies **all** subscribers synchronously; dispatches `ActionTypes.INIT` at creation and `ActionTypes.REPLACE` on `replaceReducer` (`CreateStore.kt:175-205,224,237`). The `isDispatching` re-entry guard inside `dispatch` is **commented out** (`CreateStore.kt:182-191`).
- `ModelState = Map<KClass<*>, Any>` with a **key set fixed at construction**; `get()` throws on a missing model (`ModelState.kt:33-61`).
- Granular `subscribeTo { selector }` memoizes by `===` then `==` (`SubscribeFields.kt:127`).
- `threadsafe` wraps `dispatch`/`getState`/`subscribe` in `synchronized` (`ThreadSafeStore.kt`).
- Eager classload: assembling the root reducer references every feature reducer lambda, so all are loaded at store assembly.

### The two problems this RFC addresses

1. **Broadcast dispatch.** Actions visit every reducer/middleware; relevance is decided by per-handler instance checks (`when`/`is`). Two nested broadcasts in the multimodel case (every model × every action).
2. **Ergonomics.** `when(action){}` / cascading `if` is the only routing idiom.

### Goals, re-weighted after review

| Goal | Original | Revised weight | Why |
|---|---|---|---|
| **Ergonomics** | co-equal | **Primary** | The most defensible, all-target, all-phase win. |
| **Routing precision** | co-equal | **Primary, reframed** | Reframed from "faster dispatch" to "scaling headroom + precise subscriptions." At realistic model counts a hash lookup likely ties/loses to the current `is`-sweep; the real win is not running irrelevant handlers and feeding the granular `===` fast-path. |
| **Lazy-load / memory** | co-equal | **Secondary, scoped** | Achievable only in Mechanism B, only on JVM/Android (Native/wasm are AOT — no runtime classloading), and it is *code-init deferral*, **not** resident-memory reduction (see [§6.2](#62-the-lazy-load-reality)). |

---

## 2. Organizing principle

Routing is a **2-D sparse matrix**: `(ModelType, ActionType) → handler`. Most reducers touch exactly one model → one cell. A model is the natural unit of a **code module** and (where it exists) the unit of **lazy code-loading**. Flat single-state is the **one-model degenerate case** — structurally true, with one behavioral caveat (it loses "every reducer sees every action"; see broadcast actions, [§5.3](#53-broadcast--cross-cutting-actions)).

The new API targets `Store<ModelState>`, built on `redux-kotlin-multimodel`. It produces a plain `Reducer<ModelState>` and therefore drops into the existing `createStore` / `createThreadSafeStore` and composes with `granular` / `compose` unchanged.

---

## 3. Handler taxonomy (one write-discipline)

Decision: a single **return-a-write-set** discipline (no imperative `scope.set`). This avoids the fragmentation footgun where a handler that grows from one model to two must be rewritten into a different paradigm, and the "forgotten `scope.set` silently no-ops" trap.

### Single-model handler — the 90% case

A pure function `(M, A) -> M`:

```kotlin
model<UserModel> {
    on<LoggedIn>  { s, a -> s.copy(user = a.user) }
    on<LoggedOut> { s, _ -> s.copy(user = null) }
}
```

### Multi-model handler — the 10% case

Reads any number of slices (snapshot of the working state), **returns** a typed write-set. It never mutates in place.

```kotlin
onAction<Checkout> { reads, a ->
    val cart = reads.get<CartModel>()
    writeSet {
        set(cart.copy(closed = true))
        set(reads.get<UserModel>().copy(lastOrder = cart.id))
    }
}
```

A single-model handler is exactly the one-entry-write-set specialization: same return-based discipline, same `===` change semantics.

### Action matching rule (locked): exact leaf class only

`table[action::class]` matches the **concrete leaf class** of the action. Registering a handler on a sealed parent / interface is **not** supported (a compile-time error in the codegen path). This is a deliberate narrowing versus today's `typedReducer` `is`-matching; it is documented, codegen-friendly, and avoids a per-dispatch superclass walk (which is reflection-heavier on Native/JS).

- Sealed-hierarchy users who relied on parent fan-in register each leaf, or use a broadcast registration ([§5.3](#53-broadcast--cross-cutting-actions)).
- A test must assert that dispatching a subtype against a parent-registered handler does **not** match, documenting the behavior.

### Immutability is a hard contract

Models must be immutable (`data class` + `copy()`); a handler that intends to change a model must return a **`!==`** instance. A dev-mode assertion flags a handler that claims a change but returns the same instance (it would be silently dropped by the `===` gate). In-place mutation returning the same instance is an error.

---

## 4. Dispatch algorithm (sequence-preserving, zero-alloc on no-op)

```text
dispatch(action):
  handlers = table[action::class]          // exact-leaf lookup; usually 0 or 1 entry
  if handlers is empty:                     // unhandled action
      return currentModelState              // SAME instance — no rebuild, no alloc
  var working = currentModelState           // immutable snapshot
  var changed = null                        // copy-on-first-write accumulator
  for h in handlers (in store-creation order):
      writeSet = h.apply(reads = working, action)   // reads see prior handlers' writes
      for (modelKey, newModel) in writeSet:
          if newModel !== working[modelKey]:        // === gate (matches combineModelReducers)
              changed = changed ?: copyOf(working.models)
              changed[modelKey] = newModel
              working = ModelState(changed)         // view for subsequent reads
              onWrite?.fire(action, modelKey, prevForKey, newModel, h.source)  // only when !==
  return if (changed == null) currentModelState else working   // ≤1 effective rebuild
```

Key invariants (all required, all testable):

- **Fold semantics preserved.** A later handler reads earlier handlers' writes (the `reads` view reflects accumulated changes). Only *relevant* cells run.
- **Zero allocation on no-op dispatch** — mirror `combineModelReducers`' `changedModels == null` fast path (`CombineModelReducers.kt:65,80`). An unhandled action returns the *same* `ModelState` instance.
- **`===` identity of untouched models preserved through commit.** This is load-bearing for the granular `===` fast-path and for Compose recomposition skipping. Test: an unchanged model's slot is `===` across dispatch and its granular subscribers fire zero times.
- **Subscriber contract unchanged.** Raw `store.subscribe` listeners still fire on every dispatch (the Redux contract); only the *granular* layer is "precise." This must be stated plainly — the precision claim is a granular-layer property, not a raw-subscribe property.

### Order, conflicts, hook

- **Order is fixed at store creation** via the `install()` sequence (single composition point; deterministic across independently-compiled modules):

  ```kotlin
  val store = createModelStore(initialModels) {
      install(UserModule)        // ← order here defines cross-module handler sequence
      install(CartModule)
      install(CheckoutModule)
  }
  ```

  Within a module, declaration order.
- **Last-write-wins** on same-model writes within one dispatch (sequential, like the current fold). `source` identifies the writing handler.
- **`onWrite` hook** fires **only when `next !== prev`** (skips no-op writes, matching the `===` gate). It is a **pure observer**: no `dispatch`, no `getState`, no store access; it runs synchronously during the fold, before commit, so `prev`/`next` reflect transient working state. For conflict tracing it intentionally sees intermediate (clobbered) values. A separate **per-dispatch** hook is the right shape for devtools/time-travel (which must group by action, not by write); see [§7](#7-effects-middleware--devtools) for why the hook must not be a plain runtime store field.

### Error semantics (locked)

Handlers run against an **immutable working snapshot**. A throw in handler *k* aborts the whole dispatch **before commit** — the store observes the same all-or-nothing guarantee the core already provides (`CreateStore.kt` never reassigns `currentState` if the reducer throws). No partial commit. Because changes are *returned* (not mutated via a scope), there is no half-mutated working bag to leak.

---

## 5. The unavoidable edges (must be specified, not glossed)

### 5.1 INIT / REPLACE

There is **no INIT fan-out**. Initial model instances come from `install()` / `ModelState.of(...)` at store creation — initialization is **structural**, not action-driven. `ActionTypes.INIT` and `ActionTypes.REPLACE` have no registered cell and are therefore no-ops over the routing table; models keep their current instances. This is documented as intentional and is the migration sharp-edge for code that relied on `(undefined, INIT) -> defaultState`.

### 5.2 Reentrancy

`onWrite` and handlers may not call `dispatch`/`getState`. Routed effects ([§7](#7-effects-middleware--devtools)) dispatch **after** the reducer returns, via the dispatch funnel, never inside a handler. The RFC notes the core's commented-out `isDispatching` guard (`CreateStore.kt:182-191`) as a pre-existing hazard the routed layer must not depend on.

### 5.3 Broadcast / cross-cutting actions

Because matching is exact-leaf, a global `RESET` / `LOGOUT` / `REHYDRATE` that every model must handle is **not** implicitly broadcast (unlike `combineReducers`' fold). A first-class registration handles this:

```kotlin
onBroadcast<Reset> { /* per-model reset, run for every installed model */ }
```

Cost is `O(handlers registered for the broadcast)` — this is the routing model's worst case and a realistic one; it must be benchmarked (the LOGOUT-clears-all scenario).

### 5.4 Dynamic registration

v1 fixes the table at store creation. Post-creation registration (the `replaceReducer` code-splitting use case, and Android dynamic-feature splits) is supported only via an explicit `registry.installInto(store)` + `replaceReducer` path — see [§6.4](#64-android-classloader-topology). The tension between "fixed install order" and "add features later" is called out, not hidden.

---

## 6. Three mechanisms

### 6.1 Mechanism A — runtime DSL (Phase 1, the product)

`createModelStore { model<X>{ on<Y>{…} }; onAction<Z>{…}; onBroadcast<R>{…} }` → a plain `Reducer<ModelState>`.

- ✅ Ergonomics (kills the `when` cascade), routing precision, granular synergy.
- ✅ **Unconditionally portable**: zero new expect/actual, no runtime reflection, builds on proven multimodel + KClass-key machinery already in CI on every target.
- ❌ **No lazy-load.** Building the DSL instantiates every handler lambda at config time (each lambda is a synthetic class). On Android this verifies those classes on the **main thread at `createStore`** — the same eager-classload it claims to fix. A is for ergonomics + routing precision, and has today's classload profile. Do not market lazy-load for A.

### 6.2 The lazy-load reality

- Real laziness requires Mechanism B (function-reference / stable-key registration that defers a handler's containing facade classload until first matching dispatch).
- It is **JVM/Android-only** — Kotlin/Native and wasmJs are AOT-linked with dead-code elimination; "lazy classloading" has no runtime meaning there.
- It is **code-init deferral, not memory reduction.** `ModelState`'s fixed key set means every model *instance* exists for the store's lifetime; only *code* can be deferred, and only until a feature is first used. True lazy model *data* would require relaxing the fixed-key-set invariant — a **2.0 item** ([§10](#10-breaking-20-appendix)).
- Measure with Android time-to-interactive / class-init counts and a JVM `-verbose:class` probe; do not claim resident-heap savings without a heap-dump diff, and quantify against typical screen memory to show it is small.

### 6.3 Mechanism B — KSP codegen (Phase 2, gated)

`@Reduce`-annotated top-level functions grouped by their model parameter type; KSP generates a per-feature **registrar object**.

- **Cross-module contract is explicit `install(FeatureModule)`** — *not* automatic discovery. KSP's `Resolver` cannot enumerate annotated symbols sitting in binary **klib** dependencies on Native/wasm, and there is no ServiceLoader / classpath scan off-JVM. The app references each feature's generated registrar by name at the single `install()` point (the app→feature dependency already exists; sibling deps are not needed).
- **No Int-ID array dispatch.** Independent per-module KSP passes have no shared counter → ID collisions → silent wrong-handler corruption and unstable serialization keys. Keep KClass-keyed (or per-module-namespaced **string**-key) `HashMap` routing — already O(1), collision-free, and the string keys are serialization-stable. Array dispatch is a closed-world property and belongs only to Mechanism C.
- **Build-system fallback:** the same explicit `installInto(registry)` is the contract for Buck / non-Gradle (KSP is Gradle-wired).
- **Testing/gate:** generated source location, detekt/`apiCheck` treatment (generated public API must satisfy `explicitApi()` + KDoc or be excluded), per-`@Reduce` unit-testing, and a runtime "dump the resolved routing table" affordance for debuggability — all must be specified before B ships. **Phase 2 is gated on a KSP2 spike** validating `getClassDeclarationByName`/`getDeclarationsFromPackage` against `iosArm64` + `wasmJs` klib dependencies. If the spike fails, B degrades to "codegen the DSL boilerplate behind explicit `install()`" — still useful, but a smaller win than advertised.

### 6.4 Android classloader topology

KClass identity is per-classloader. Standard Gradle `:feature` library modules share one classloader → routing is correct and O(1) (KClass `hashCode`/`equals` are stable per-classloader on ART). **Play Feature Delivery on-demand / dynamic-feature splits** load through a separate classloader → KClass equality silently fails. Documented support: standard library modules only; runtime splits must call `registry.installInto(store)` after `SplitInstallManager` confirms install (backed by `replaceReducer`), or opt into string-key codegen (B) which sidesteps classloader identity.

### 6.5 Mechanism C — compiler plugin (documented, recommend-against)

FIR + IR plugin: synthesize the `ModelState` bag, a dense 2-D jump table, compile-time exhaustiveness.

- Recommend-against, and the rationale is *stronger* than first stated: **perpetual per-Kotlin-version, per-target IR maintenance** against explicitly unstable FIR/IR APIs, for a small OSS library, with the same (worse) Gradle-wiring problem as KSP. Its one unique upside — the array jump-table — needs measured hot-path pressure that does not exist. Documented as the ceiling and as a 2.0 input.

### Mechanism scorecard

| | A (DSL) | B (KSP) | C (compiler plugin) |
|---|---|---|---|
| Ergonomics | ✅ kills `when` | ✅ + less boilerplate | ✅✅ zero-boilerplate |
| Routing precision | ✅ | ✅ | ✅ |
| Lazy-load | ❌ none | ⚠️ JVM/Android only, gated | ⚠️ closed-world |
| All KMP targets | ✅ proven | ⚠️ cross-module spike needed | ⚠️ per-target IR |
| Runtime reflection | none | none | none |
| Build systems | any | Gradle (+ `installInto` fallback) | Gradle, fragile |
| Cost / risk | low | moderate, gated | high, perpetual |

---

## 7. Effects, middleware & devtools

### Effects — a separate `redux-kotlin-routing-effects` module (locked)

Core routing stays pure reducers with **no `kotlinx.coroutines` dependency** (true to the minimal ethos). A companion `redux-kotlin-routing-effects` module owns the coroutine effect API, isolating the dependency for opt-in users:

```kotlin
onEffect<LoggedIn> { reads, a ->         // suspend; store-lifetime CoroutineScope
    val profile = api.fetchProfile(a.userId)
    dispatch(ProfileLoaded(profile))      // dispatch AFTER reducer commit, via the funnel
}
```

Contract: effects run **after** the routed reducer commits; they dispatch follow-up actions through the normal dispatch funnel (re-entering the routing table cleanly, never inside a handler); cancellation is scoped to a store-lifetime `CoroutineScope`. A worked `LoggedIn → fetchUser → LoggedInSuccess` loop is part of the module's docs.

### Middleware pipeline (must be drawn, not asserted)

The routed reducer is the **single** `Dispatcher` that `applyMiddleware` and `ThreadSafeStore` wrap. The full pipeline:

```text
action → linear middleware chain (compose(chain)) → routed reducer (fold over cells) → commit → effects → subscriber notify
```

Cross-cutting middleware (logging, crash reporting) stays the existing linear chain and sees the action before the reducer. Because the routed reducer is one synchronous `Reducer<ModelState>` inside `dispatch`, `ThreadSafeStore`'s single lock covers the whole fold + commit — thread-safety is preserved by construction. Effects sit after commit and dispatch new actions (which re-enter the funnel and are themselves wrapped).

### Devtools / serialization

- The `onWrite` hook is **not** a plain runtime store field (R8 cannot prove it null → cannot strip it; a time-travel buffer behind it is unbounded memory + a PII surface in release). Devtools is wired via a **separate enhancer/module** that release builds simply do not depend on — structurally absent and R8-strippable.
- Persistence requires a **stable string model-key registry** (not KClass, which is process-local; not Int, which collides across modules) + per-model `kotlinx.serialization` + a documented restore that reconstructs the fixed `ModelState` key set. Time-travel replay is **reducer-only** (effects suppressed) to avoid re-firing network calls. Designed in the persistence module, out of scope for Phase 1 beyond reserving the `onDispatch` hook shape.

---

## 8. Integration with existing modules

| Module | Adaptation |
|---|---|
| `createStore` / `createSameThreadEnforcedStore` | unchanged — routed reducer is a plain `Reducer<ModelState>` |
| `threadsafe` | unchanged — single lock wraps the whole fold + commit |
| `granular` | unchanged — `subscribeTo { it.get<UserModel>() }` fires precisely **because** commit preserves `===` of untouched models |
| `compose` / `compose-multimodel` | unchanged — `selectorState`/`fieldState` ride the granular `===` path; `StableStore` reference stays `@Stable` |
| `registry` | the multi-store container; incremental adoption (one routed store alongside flat stores) is supported via separate stores |
| `multimodel` | the substrate; `combineModelReducers` coexists and is **not** deprecated in v1 |

An **interop matrix** + an **incremental-adoption** statement (one routed feature beside flat state) are required doc deliverables.

---

## 9. Recommendation & phased roadmap

1. **Phase 1 — ship Mechanism A** as `redux-kotlin-routing` (opt-in companion module). Delivers ergonomics + routing precision + granular synergy on **all** targets, no tooling, no new runtime dependency. This is the product.
2. **Phase 1.5 — ship `redux-kotlin-routing-effects`** (opt-in, owns the coroutine effect API).
3. **Phase 2 — Mechanism B (KSP)**, *gated* on the cross-module KSP2 spike ([§6.3](#63-mechanism-b--ksp-codegen-phase-2-gated)). Codegen emits exactly what A's DSL produces by hand, so **A remains the escape hatch and the test oracle** (generated output must match hand-written). Adds lazy registration (JVM/Android) + string-key serialization stability.
4. **Phase 3 — document Mechanism C**, recommend-against; capture it as a 2.0 input.

New modules: `redux-kotlin-routing`, `redux-kotlin-routing-effects`, later `redux-kotlin-routing-codegen`. Each follows the repo's module conventions (`convention.library-mpp-loved` + `convention.publishing-mpp`, package `org.reduxkotlin.routing`, mirror `redux-kotlin-registry` as template).

---

## 10. Breaking-2.0 appendix

What an additive design cannot reach, captured for a future major:

- **Typed action union at the core contract** (replace `action: Any`), enabling compile-time exhaustiveness and removing the `isPlainObject` runtime check.
- **Relaxed `ModelState` key set** to allow true lazy model *data* (not just code) — the only path to actual resident-memory reduction.
- **Routed dispatch as the core default**, retiring the broadcast fold.
- **Mechanism C** compiler plugin enabling dense array dispatch + zero-boilerplate handlers + compile-time conflict detection.

---

## 11. Review findings → resolutions

| # | Finding (severity) | Resolution |
|---|---|---|
| 1 | INIT/REPLACE reach an empty table; models never init (blocker) | [§5.1](#51-init--replace): init is structural via `install()`; no INIT fan-out, documented |
| 2 | `action::class` narrows vs `is`-matching (blocker) | [§3](#3-handler-taxonomy-one-write-discipline): **exact-leaf only**, sealed-parent registration disallowed, documented + tested |
| 3 | Effects/async undesigned (blocker) | [§7](#7-effects-middleware--devtools): separate `-effects` module, coroutine contract specified |
| 4 | Mid-fold error: partial commit? (blocker) | [§4](#4-dispatch-algorithm-sequence-preserving-zero-alloc-on-no-op): all-or-nothing, return-based (no half-mutated bag) |
| 5 | Routed vs linear middleware composition (blocker) | [§7](#7-effects-middleware--devtools): pipeline drawn; routed reducer is the single wrapped `Dispatcher` |
| 6 | Lazy-load overclaimed (major ×4) | [§1](#1-context--goals)/[§6.2](#62-the-lazy-load-reality): demoted, scoped JVM/Android-only, code-deferral not memory |
| 7 | Routing perf likely a wash at small N; alloc regression (major) | [§1](#1-context--goals)/[§4](#4-dispatch-algorithm-sequence-preserving-zero-alloc-on-no-op): reframed; copy-on-first-write, zero-alloc no-op; benchmark mandatory |
| 8 | Int-ID array dispatch collides cross-module (major ×3) | [§6.3](#63-mechanism-b--ksp-codegen-phase-2-gated): dropped from B; string keys; array → C only |
| 9 | KSP cross-module auto-discovery fails on klibs (major) | [§6.3](#63-mechanism-b--ksp-codegen-phase-2-gated): explicit `install()` is the contract; Phase 2 gated on spike |
| 10 | KClass breaks across classloaders (Android) (major) | [§6.4](#64-android-classloader-topology): supported topology documented; `installInto` for splits |
| 11 | `onWrite` leak / not strippable (major) | [§4](#4-dispatch-algorithm-sequence-preserving-zero-alloc-on-no-op)/[§7](#7-effects-middleware--devtools): devtools via separate strippable module; hook is pure, `!==`-gated |
| 12 | Single vs multi idiom fragments mental model (major) | [§3](#3-handler-taxonomy-one-write-discipline): **return-a-write-set** single discipline |
| 13 | No migration path (major) | [§12](#12-migration): migration section with the no-cell-vs-fold semantic diff |
| 14 | Commit must preserve `===` / immutability contract (major+) | [§3](#3-handler-taxonomy-one-write-discipline)/[§4](#4-dispatch-algorithm-sequence-preserving-zero-alloc-on-no-op): hard contract + dev assertion + tests |
| — | KClass-as-key correct on all targets; zero expect/actual; new MM safe (confirmations) | [§6.1](#61-mechanism-a--runtime-dsl-phase-1-the-product)/[§8](#8-integration-with-existing-modules): kept as stated strengths |

---

## 12. Migration

Concrete before/after for each current entry point (`combineReducers`, `combineModelReducers`, `typedReducer`), and the load-bearing semantic difference: **a routed table with no cell for an action does nothing**, whereas a `combineReducers` fold runs every child — including any `else -> state` catch-all logic that observed all actions. `combineModelReducers` is the natural migration source and **coexists** (not deprecated) in v1. Global broadcast actions (`RESET`/`LOGOUT`) migrate to `onBroadcast` ([§5.3](#53-broadcast--cross-cutting-actions)).

---

## 13. Open items / where measurement is mandatory

- **Dispatch micro-benchmark** across `M ∈ {1,4,8,16,32,64}` and across jvm/js/wasmJs/native: publish the crossover N where KClass-routing beats the `is`-sweep. If crossover > ~16, reframe routing-perf as large-app headroom only. (Reuse the `redux-kotlin-granular` JMH harness.)
- **Allocation test:** zero B/op on no-op dispatch; ≤1 map copy per changed dispatch (`-prof gc`).
- **Broadcast worst case:** benchmark LOGOUT-clears-all.
- **Lazy-load:** Android time-to-interactive + class-init counts; JVM `-verbose:class` first-dispatch probe. No resident-heap claim without a heap-dump diff.
- **KSP2 cross-module spike** on `iosArm64` + `wasmJs` klibs before committing Phase 2 array/IDs (already a Phase-2 gate).
- **All-target tests from day one:** `wasmJsTest` + an iOS `commonTest` asserting distinct-slot routing and re-dispatch stability; a `jvmTest` routed-dispatch-under-threadsafe concurrency stress.
