# Concurrent Store Consistency Hardening Plan (C1–C7)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan stage-by-stage (5 stages, each its own PR).

**Goal:** Fix the seven verified threading/consistency concerns from the 2026-06-10 multi-agent concurrent-store review — headline: the posting-context lost-wakeup race (C1) that can leave a diff-based subscriber/binding stale until the next dispatch — plus close test gaps T1–T6, with zero public-signature changes.

**Origin:** 46-agent adversarially-verified review (docs corrections landed separately in PR #335); this plan synthesized from 3 competing solution designs + a test-strategy design, scored by a 3-judge panel (correctness / disruption / coherence), all of which converged on the architecture below.

All file paths relative to repo root. Verified against `master` at `20d0b5ca`.

## Summary

**Unifying mechanism #1 — publish-then-signal.** In `CallerSerializedStore` (`redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/ConcurrentStore.kt`), move the mirror publish to the **first line of the `inner.subscribe` fan-out hook** (line 56), ahead of the `notificationContext.post` calls. Inner-store listeners run only after the core reducer has committed and `isDispatching` is cleared (`CreateStore.kt:193–202`), so this publishes the post-reducer state with a happens-before edge to every posted callback. The existing `finally { mirror.value = inner.getState() }` in `sequenced()` (line 77) and `replaceReducer` (line 107) stays as an idempotent backstop for reducer-throw and middleware-swallowed-action paths. This one line-move kills the C1 lost-wakeup class and is the precondition that makes C2's re-sample a complete synchronization point. The fan-out **stays inside the writer lock and inside `DispatchContext`** — on-context routing, nested-dispatch semantics, per-dispatch delivery counts, and granular's for-free serial Inline delivery are all preserved (this is why C4 and C7 can be document-only).

**Unifying mechanism #2 — registration wrapper.** Replace `listeners: atomic<List<StoreSubscriber>>` with `atomic<List<Registration>>` (`private class Registration(val subscriber: StoreSubscriber) { val active = atomic(true) }`). The posted lambda checks `active` at execution time (C5), and the same lambda wraps the `onError` call in its own try/catch (C6). One edit site fixes both.

**Contract changes (deliberate, documented):**
1. The old "mirror published after listeners / no mid-listener tear" KDoc claim is **retired**. It was already false for posting contexts (that IS bug C1). No test pins it (audited: `CallerSerializedStoreTest` line 102 pins publish-after-`replaceReducer`-returns, which still holds; stress tests pin counts/values only). New invariant: *reducer commits → mirror published → listeners signaled through the context → lock releases; a callback always observes state at least as new as its triggering dispatch; callbacks pull state, notification is a signal.*
2. `dispatch` from inside a reducer **throws `IllegalStateException`** (core guard restored — C3).
3. After `unsubscribe()` returns, **no new callback invocation begins** (C5; deliberate divergence from JS-Redux snapshot delivery, confined to the wrapper).
4. `NotificationContext` implementations **must deliver serially** with happens-before between consecutive blocks (C4, documented constraint).
5. `Inline` runs callbacks while the writer lock is held — slow subscribers delay other dispatchers, never readers (C7, documented constraint, now test-evidenced).

Zero public-signature changes anywhere: `apiDump` is expected to be a no-op (Registration is private; KDoc edits don't appear in klib dumps). Downstream consumers verified safe: granular diffing gets strictly-fresher signals; FieldState pulls `getState()` at read time; compose-saveable relies only on writes-synchronous (unchanged); taskflow's `mainNotificationContext` inline path is unaffected and its posted path becomes strictly more correct.

---

## Per-concern solutions

### C1 [HIGH] — Lost-wakeup race (posted callback reads old mirror)

**Decision:** Fix. Publish-before-signal via the one-line move; reject B-unified's write-section restructure.

**Mechanism** (`ConcurrentStore.kt`, `init` block):

```kotlin
init {
    inner.subscribe {
        // Publish the mirror BEFORE fan-out: inner listeners run only after the
        // reducer has committed (core dispatch clears isDispatching first), so this
        // is the post-reducer state. The atomic write happens-before every post()
        // below, so a posted callback can never observe a mirror older than the
        // dispatch that triggered it (no lost wakeups for diff-based consumers).
        mirror.value = inner.getState()
        val snapshot = listeners.value
        snapshot.forEach { registration ->
            notificationContext.post {
                // (C5/C6 changes here — see below)
            }
        }
    }
    ...
}
```

Keep `mirror.value = inner.getState()` in the `finally` of `sequenced()` and `replaceReducer` — idempotent, same-thread monotonic; covers reducer-throw (no inner listener fires) and middleware-swallowed actions.

Memory-ordering argument (goes in KDoc): atomic mirror write → same-thread `post()` invocation → the post handoff's happens-before edge to the target thread → callback's `getState()` read observes state ≥ triggering dispatch. Racing dispatchers' publishes are serialized by the writer lock, so a late-running callback observes equal-or-newer state — already the documented "signal, not payload" rule.

**Files:** `redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/ConcurrentStore.kt` (code + class KDoc), `NotificationContext.kt` (KDoc), `docs/agent/references/store-consistency-model.md`, `examples/taskflow/composeApp/src/jvmMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/Notification.jvm.kt` (line 12 KDoc — states the retired "publish-after-notify ordering"; verified by grep). Taskflow `ARCHITECTURE.md:740` already says "published after the reducer completes", which matches the NEW model — verify, likely no change needed.

**Disruption:** behavior-change (ordering contract inversion). With Inline, off-context threads now see the new mirror *while* listeners run (previously after). No test pins the old order.

**Risks:** Third-party code treating the mirror as a "listeners finished" barrier — never documented as supported; now explicitly disclaimed. All in-repo consumers pull state, so earlier publish only helps. Pinned tests verified green: all of `CallerSerializedStoreTest` (value assertions only; line 102/106 assertions hold — fan-out still runs on-context), `ConcurrentStoreStressTest`, `ForeignThreadDispatchTest`.

**Tests:** T3 (deterministic race pin — must fail pre-fix), T1's during-listener freshness assertion, T2's drain assertions. See Test plan.

### C2 — FieldState B3 re-sample races subscription install

**Decision:** Fix in two layers. (a) FieldState reorder (required). (b) Granular `activate()` post-install re-diff (adopted from plan B — fixes the same window class for *all* `subscribeTo`/`subscribeFields` consumers, not just Compose; lands as its own commit).

**Mechanism (a)** — `redux-kotlin-compose/src/commonMain/kotlin/org/reduxkotlin/compose/FieldState.kt`, both `selectorState` (lines 57–69) and `fieldState` (lines 107–118): swap the two statements inside `DisposableEffect` — install first, re-sample second:

```kotlin
DisposableEffect(store, rememberedSelector) {
    val sub = store.subscribeTo(selector = rememberedSelector, triggerOnSubscribe = false) { _, _ ->
        tick.intValue++
    }
    // B3 re-sample AFTER install: a change before install is caught here (compared
    // against the first-composition `initial`); a change after install is caught by
    // the subscription (guaranteed-fresh post-C1). Overlap = one redundant tick bump.
    if (rememberedSelector(store.state) != initial) tick.intValue++
    onDispose { sub() }
}
```

Window analysis (verified against `SubscribeFields.kt`): a dispatch landing inside `subscribeTo` itself (between the `Entry.last` sample in `on()` at lines 90–96 and `store.subscribe` in `activate()` at line 110) leaves `entry.last` holding the new value, but the re-sample still differs from the first-composition `initial` → tick bumps. Worst case both fire → one extra same-value recomposition (harmless; getter pulls live state).

**Mechanism (b)** — `redux-kotlin-granular/src/commonMain/kotlin/org/reduxkotlin/granular/SubscribeFields.kt`, `activate()`: after `store.subscribe` (line 110+) and **before** the existing `triggerOnSubscribe` loop (line 143), run one re-diff pass with the same body shape as the subscriber loop: for each entry, evaluate `selector(store.state)`; if it differs from `entry.last` (`!==` then `!=`), update `entry.last` and fire `listener(prev, next)`, with the same `onSelectorError` isolation. This closes the registration window at the layer that owns it. Order matters: the re-diff updates `entry.last` first so a subsequent `triggerOnSubscribe=true` fire delivers `(new, new)`. Semantics note: `triggerOnSubscribe=false` gains one edge — a change landing in the `on()`→`activate()` window now fires at activate (arguably the correct reading of "waits for first change"); the pinned `triggerOnSubscribe_false_waits_for_first_change` test has no dispatch in that window and stays green. On a posting context the re-diff can produce a benign duplicate fire (double-fire-over-drop, the trade the existing `activate()` comment at lines 138–142 already endorses) — state this in the KDoc.

**Files:** `FieldState.kt`, `SubscribeFields.kt`, plus tests (see T6 and the new deterministic pins).

**Disruption:** internal-only.

**Risks:** Near zero. Existing pins verified green: `fieldState_b3_race_window_picks_up_dispatch_between_remember_and_effect` and `bindingCatchesChangeThatLandedBeforeItSubscribed` exercise adjacent windows that survive the reorder. **Sequencing is load-bearing: land C1 before or with C2** — the "change after install is always notified" leg relies on publish-before-post.

### C3 — Core dispatch-during-reduce guard commented out

**Decision:** Fix. Uncomment the guard verbatim.

**Mechanism** — `redux-kotlin/src/commonMain/kotlin/org/reduxkotlin/CreateStore.kt` lines 182–191: remove the `/* */` around `check(!isDispatching) { ... }` (keep the existing message — it already names both causes and routes to `createThreadSafeStore`).

Safety audit (verified against source):
- `isDispatching` is true only around the reducer call (lines 193–198); listeners run after it clears (line 197 precedes the `forEach` at 202) → listener re-dispatch stays legal (pinned by `listener_dispatching_inline_runs_synchronously_and_sees_latest` and granular's `listener_dispatching_from_within_does_not_deadlock`).
- Middleware re-dispatch happens around `next()`, never during reduce (pinned by `ForeignThreadDispatchTest.middleware_redispatch_from_a_foreign_thread_is_serialized`).
- `getState` (line 71), `subscribe` (line 112), and the unsubscribe closure (line 135) **already enforce the identical guard** — this restores internal consistency, not new policy.
- Through `CallerSerializedStore`: a reducer-dispatch ISE propagates out of `pipeline(action)` before the outer `currentState` assignment executes → state uncorrupted → the wrapper's `finally` publishes a consistent mirror.
- `replaceReducer`'s REPLACE dispatch runs outside any reducer; compose-saveable's restore dispatch runs at composition, not reduction.
- Grep confirms no in-repo code (including taskflow — its bot dispatches from middleware) dispatches from a reducer; re-verify the grep across `examples/` at implementation time.

**Files:** `CreateStore.kt` (guard + one KDoc sentence on `dispatch`), `redux-kotlin/src/commonTest/kotlin/org/reduxkotlin/CreateStoreTest.kt`.

**Disruption:** behavior-change. Downstream code that silently corrupted state via reducer-dispatch now throws — headline changelog entry required. Bare-store multithread misuse fails fast instead of corrupting. No `.api` delta.

**Tests:** commonTest: (1) reducer that dispatches → `assertFailsWith<IllegalStateException>` containing "may not dispatch while state is being reduced", state unchanged afterward; (2) `replaceReducer` called from a reducer → throws; (3) listener dispatch still succeeds (boundary pin); (4) wrapper companion in `CallerSerializedStoreTest`: ISE surfaces through `sequenced()`, mirror stays consistent. Existing middleware/listener-redispatch suites as regression gate.

### C4 — Granular `Entry.last` assumes serial delivery

**Decision:** Document the constraint; do not change code. Add an executable tripwire test.

**Argument:** `Entry.last` (`SubscribeFields.kt:126–133`) is a `@Volatile` read-compare-write that is only *meaningful* under serial delivery; even per-entry CAS would not restore meaning under concurrent delivery (the `(old,new)` pairing and ordering are scrambled regardless) and would tax the hot path of every conforming consumer. Every shipped context is serial: Inline runs under the writer lock (the load-bearing reason fan-out stays under the lock — see C7), single-thread posting and `coalescingNotificationContext` serialize on the target loop, JS/wasm are single-threaded.

**Mechanism:**
- `NotificationContext.kt` interface KDoc, new normative sentence: *"Implementations MUST execute posted blocks for a given store one at a time, in post order, with a happens-before edge between consecutive blocks (any single-threaded executor, main-thread post, or inline execution qualifies). Handing blocks to a multi-threaded executor is unsupported: diff-based consumers (redux-kotlin-granular's `Entry.last`, and therefore `selectorState`/`fieldState`) assume serial notification and will lose or duplicate diffs otherwise."* (The happens-before clause is what makes serial-but-cross-thread delivery sound together with the `@Volatile` on `Entry.last` — keep the volatile.)
- Fix the misattributed comment at `SubscribeFields.kt:112–115` ("the store contract guarantees subscribers are invoked serially") to cite the `NotificationContext` serial-delivery requirement.
- New "Notification contexts must be serial" bullet in `store-consistency-model.md`.

**Files:** `NotificationContext.kt`, `SubscribeFields.kt`, `docs/agent/references/store-consistency-model.md`, `redux-kotlin-granular/build.gradle.kts` (jvmTest dep — see tests).

**Disruption:** none (docs + one test-scope build edge).

**Risks:** A user with a thread-pool context stays broken until they read the KDoc — acceptable; runtime detection is not portably implementable without taxing conforming users.

**Tests:** New jvmTest `GranularOnConcurrentStoreStressTest` in redux-kotlin-granular: 100 `subscribeTo` entries over a **real** `createConcurrentStore` (default Inline) under a 4-thread dispatch storm (copy the `ConcurrencyStressTest` harness — note the existing scenario5a/5b run against `createThreadSafeStore` only, so granular-over-ConcurrentStore currently has zero coverage). Requires `implementation(project(":redux-kotlin-concurrent"))` in granular's jvmTest deps (precedented: jvmTest already has `:redux-kotlin-threadsafe`; no cycle — granular's commonMain deps core only). This test is the tripwire that fails loudly if anyone later moves fan-out off the writer lock.

### C5 — Unsubscribed listener receives in-flight posted callback

**Decision:** Fix. Per-subscription active flag checked at callback execution time.

**Mechanism** (`ConcurrentStore.kt`):

```kotlin
private class Registration(val subscriber: StoreSubscriber) {
    val active = atomic(true)
}
private val listeners = atomic<List<Registration>>(emptyList())

override val subscribe: (StoreSubscriber) -> StoreSubscription = { subscriber ->
    val registration = Registration(subscriber)
    listeners.update { it + registration }
    val unsub: StoreSubscription = {
        if (registration.active.compareAndSet(expect = true, update = false)) {
            listeners.update { it - registration }
        }
    }
    unsub
}
```

Fan-out posts `{ if (registration.active.value) { /* guarded invoke, see C6 */ } }`. The existing `subscribed` flag (line 92) is subsumed by `registration.active`. Removal becomes identity-based — a cleanup, **not a bug fix** (verified: `List.minus` removes only the first occurrence and the per-unsub CAS already made double-subscribe/single-unsubscribe work; do not advertise it as a fix in the changelog).

New contract sentence (subscribe KDoc + consistency model): *"After `unsubscribe()` returns, no new callback invocation begins; a callback already executing on another thread may run to completion."* Absolute for the dominant same-thread pattern (Compose `onDispose` on main, delivery posted to main — the flag write precedes any queued block on that thread); best-effort cross-thread (inherent TOCTOU, documented). Deliberate divergence from core-Redux snapshot semantics: with Inline, a peer unsubscribed by an earlier listener in the same fan-out is now **skipped** (core would deliver) — observable and guaranteed on the same thread, not racy; document in the subscribe KDoc and changelog. No test pins the old behavior (verified: `unsubscribe_stops_notifications` only tests between dispatches; churn stress asserts no-throw).

**Files:** `ConcurrentStore.kt`. **Disruption:** behavior-change (tightening). **Risks:** one small allocation per subscribe (cold path); `Registration` is private → no `.api` delta.

**Tests:** T4 matrix (see Test plan).

### C6 — Throwing `onError` aborts delivery / escapes dispatch

**Decision:** Fix. Self-shield the handler inside the single fan-out lambda (composes with C5 — one edit site).

**Mechanism** (verified bug at `ConcurrentStore.kt:60–64` — `onError(t)` is invoked bare; with Inline it propagates out of `dispatch()` and aborts the `forEach`, violating the module's own promise at lines 41–42 "never aborts delivery"):

```kotlin
notificationContext.post {
    if (!registration.active.value) return@post
    try {
        registration.subscriber()
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        try {
            onError(t)
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") suppressed: Throwable) {
            println("redux-kotlin-concurrent: onError handler threw and was suppressed: $suppressed (original listener error: $t)")
        }
    }
}
```

Update `onError` param KDoc on `CallerSerializedStore`, `createConcurrentStore`, `asConcurrent`, and `LogAndContinue`: *"must not throw; a throw from the handler itself is printed and swallowed — dispatch and delivery to remaining subscribers always proceed."*

**Files:** `ConcurrentStore.kt`, `NotificationContext.kt` (LogAndContinue KDoc), `CreateConcurrentStore.kt` (param KDoc). **Disruption:** internal-only (no plausible consumer relied on propagation; the KDoc promised the opposite). **Risks:** double-fault degrades to println — intentional; a store must outlive its observers.

**Tests:** commonTest in `CallerSerializedStoreTest`: `onError = { throw RuntimeException(...) }`; subscriber1 throws, subscriber2 counts; `dispatch(Inc)` returns normally, subscriber2 hit exactly once, state and mirror == 1. Fails on current code. Keep `throwing_listener_is_isolated_and_mirror_stays_consistent` as the well-behaved-handler pin. Add a queueing-context variant asserting `drain()` also survives.

### C7 — Inline context holds writer lock across all callbacks

**Decision:** Document the constraint; do not move fan-out off the lock. Add code evidence (T1) and a deliberate-change tripwire test.

**Argument (code-verified):** With Inline, callbacks run inside `synchronized(lock)` on the dispatching thread — a slow subscriber delays other **dispatchers**, never readers (`getState` at line 87 is mirror-only off-context). This is already the documented contract (`store-consistency-model.md:36–37`). The B-unified restructure was rejected on a factual error: with fan-out moved after lock-release under a `notifyLock`, the dispatching caller still runs its own round synchronously and racing dispatchers' rounds serialize on `notifyLock` — end-to-end dispatch latency stays serialized behind the slow subscriber; the restructure buys only earlier commit visibility, which C1's fix already delivers for free. It would also: forfeit the for-free serial Inline delivery granular relies on (C4), move Inline listeners off-context (`getState` would read the mirror instead of routing to inner, invalidating the rationale of `replaceReducer_publishes_mirror_and_routes_context`), coalesce nested-dispatch notifications (delivery-arithmetic change), and add a second lock + depth/pendingNotify protocol with its own lost-round hazards. The escape hatch is first-class: a posting/coalescing context reduces the lock-held cost to N enqueues.

**Mechanism:** KDoc on `NotificationContext.Inline` and on `createConcurrentStore`'s `notificationContext` param: *"Inline runs every subscriber while the writer lock is held: a slow subscriber delays concurrent dispatchers (and replaceReducer), never readers — getState is lock-free at all times. Supply a posting or coalescing context if subscribers can be slow."* Update the `store-consistency-model.md` bullet for the C1 inversion in the same edit (readers now see the new mirror even while Inline listeners run).

**Files:** `NotificationContext.kt`, `CreateConcurrentStore.kt`, `docs/agent/references/store-consistency-model.md`. **Disruption:** none. **Risks:** the throughput cliff remains for Inline + slow subscribers — mitigation is configuration, now prominently documented.

**Tests:** T1 pins "never readers"; new jvmTest `WriterSerializationOrderingTest` pins the lock-held fan-out deterministically without timeouts: thread A dispatches, its Inline subscriber logs `A-sub-start`, awaits a latch; thread B (confirmed started via its own latch) dispatches and blocks; release; assert event log shows `A-sub-end` strictly before `B-reducer` (reducer logs its own marker). Any future fan-out-off-lock change flips this ordering consciously.

---

## Contract & doc updates

All doc edits land **in the same commit** as the C1 code change (docs must never lie in between).

1. **`redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/ConcurrentStore.kt`** — interface KDoc lines 21–25: delete "The mirror is published after listeners run, so off-context readers never observe a mid-listener tear". Replace with: *"The mirror is published as soon as the reducer commits, before listener callbacks are signaled. A callback therefore always observes state at least as new as the dispatch that triggered it — no lost wakeups for diff-based consumers. Multiple dispatches may coalesce into what one callback observes, so callbacks must pull current state via `getState`; a notification is a signal, never a payload. There is no 'listeners finished' barrier: off-context readers may observe the new state while listeners are still running."* Subscribe KDoc: add the C5 sentence ("after unsubscribe() returns, no new callback invocation begins; one already executing may complete") and the Inline peer-skip divergence note.

2. **`NotificationContext.kt`** — lines 8–19: delete "preserves the store's publish-after-notify read ordering" and the "posted callback can win the race and observe the previous mirror" note (the race no longer exists). Add: the new publish-before-signal guarantee; the C4 serial-delivery MUST (wording in C4 above); the C7 Inline-holds-lock sentence; keep and strengthen "pull state, notification is a signal". `LogAndContinue`/`onError`: "must not throw" sentence.

3. **`CreateConcurrentStore.kt`** — `notificationContext` param KDoc: C7 sentence. `onError` param KDoc (also on `asConcurrent`): C6 sentence.

4. **`docs/agent/references/store-consistency-model.md`** — line 32–33 exact-ordering sentence becomes: *"reducer commits to the inner store → the read mirror is published → listeners are notified through the context → the writer lock releases."* Rewrite both consequence bullets: inline → *"all subscriber callbacks run while the writer lock is held (a slow subscriber delays other dispatchers, never readers), and other threads already see the new mirror while listeners run"*; posting → *"a posted callback always observes state at least as new as its triggering dispatch (the mirror is published before the post); callbacks must still pull current state and treat a notification as a signal, since later dispatches may have landed."* Add bullets: serial-delivery requirement (C4); "dispatch from a reducer throws on every store flavor" (C3); unsubscribe-wins semantics (C5). Bump `last_verified` frontmatter; regenerate the artifacts the frontmatter `assembles_into` lists (`AGENTS.md`, the redux-kotlin claude-skill).

5. **`examples/taskflow/composeApp/src/jvmMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/Notification.jvm.kt`** — line 12: replace "preserves publish-after-notify ordering" with "avoids a needless re-post and keeps subscribers synchronous with the dispatch". Verify `examples/taskflow/ARCHITECTURE.md` ~740–756 — "mirror published after the reducer completes" already matches the new model; touch only if it elaborates the old listener-ordering.

6. **`redux-kotlin-granular/src/commonMain/kotlin/org/reduxkotlin/granular/SubscribeFields.kt`** — fix the comment at 112–115 to cite the NotificationContext serial-delivery requirement; KDoc on `subscribeTo`/`subscribeFields`: registration-window note + the new activate() re-diff behavior (a change landing during registration fires at activate; may double-fire on posting contexts — benign).

7. **`redux-kotlin-compose/src/commonMain/kotlin/org/reduxkotlin/compose/FieldState.kt`** — update both B3 KDoc paragraphs: subscribe-then-resample order is load-bearing; document the harmless double-bump window.

8. **`redux-kotlin/src/commonMain/kotlin/org/reduxkotlin/CreateStore.kt`** — `dispatch` KDoc: dispatch during reduction throws.

9. **Changelog/release notes** — three headlined behavior changes: C1 ordering inversion, C3 guard restoration, C5 unsubscribe-wins.

---

## Test plan

Reusable fixture (commonTest, `internal`, no API impact): `QueueingNotificationContext` in `redux-kotlin-concurrent/src/commonTest/.../QueueingNotificationContext.kt` — `post` appends to a list; `drain()` runs batches until empty. Makes async-notify behavior single-threaded-deterministic on every KMP target including JS/wasm.

| Test | Closes | Location | Content |
|---|---|---|---|
| `MirrorPublishOrderingTest` | **T3 / C1** (must FAIL pre-fix — write first, TDD) | concurrent jvmTest (`concurrency/`) | Deterministic freeze of the race: posting context backed by a single-thread daemon executor; granular `subscribeTo(S::count)` listener records `newValue`. Register a second listener **directly on `concurrent.store` (the inner store)** that parks the dispatching thread on a latch — the wrapper's fan-out hook registers first in `init` (pin this assumption with a comment in `ConcurrentStore.kt:55`), so the park lands after the fan-out post but before the old code's finally-publish. Worker runs the granular callback off-context: old code reads the stale mirror → diff sees no change → `wakeups==0` forever (assert fails); fixed code → `observed==[1]`, exactly once. Companion storm: 2 dispatcher threads × 1000 dispatches, await quiescence, final observed value == final state. Needs `implementation(project(":redux-kotlin-granular"))` in concurrent jvmTest deps (acyclic). |
| `QueuedNotificationStoreTest` | **T2** | concurrent commonTest | Real `CallerSerializedStore` + `QueueingNotificationContext`: (1) dispatch → `getState()` new immediately, zero callbacks before drain, drain → each of 3 subscribers exactly once, each callback's own `getState()` reads new state; (2) 2 dispatches before drain → each subscriber invoked twice, both reads see count==2 (signal-not-payload); (3) nested dispatch from a drained callback enqueues a next round; (4) `replaceReducer` under the posting context publishes the mirror before drain and delivers one REPLACE signal per subscriber. |
| `LockFreeReadGuaranteesTest` | **T1 / C7-"never readers"** | concurrent jvmTest | (1) Inline subscriber parks on latch mid-dispatch (writer lock held); main thread's `getState()` and `subscribe{}` return promptly AND `getState()` already shows the NEW state (pins C1's publish point — fails pre-fix; land with C1); (2) reducer parked mid-reduce → reader returns the PREVIOUS mirror value promptly (pins mid-dispatch mirror semantics). Latch protocol, 30s awaits, no sleeps. |
| `UnsubscribeSemanticsTest` | **T4 / C5** | concurrent commonTest | Queueing context: subscribe A → dispatch → unsubscribe → drain → A invoked 0 times (fails pre-fix). Inline: listener A unsubscribes peer B mid-round → B skipped, with a loud comment marking the deliberate core-Redux divergence. Double-unsubscribe idempotent. Same lambda subscribed twice → one unsubscribe removes exactly one delivery. |
| `CoalescingContextDeliveryTest` | **T5 / C4 evidence** | concurrent commonTest | Real store + `coalescingNotificationContext`: off-target (`{false}`, queue capture) — 3 subscribers × 5 dispatches → exactly 15 queued posts pre-drain, each subscriber exactly 5 invocations post-drain (pins one-callback-per-subscriber-per-dispatch; any future burst-coalescing becomes a deliberate test-visible change); on-target (`{true}`) — callbacks inline, queue empty; flip-mid-burst — drained callback reads latest state. Add KDoc sentence: "coalescing" refers to inline-vs-marshal, not burst coalescing. |
| `ConcurrentStoreBindingTest` | **T6 / C1+C2 end-to-end** | compose jvmTest | Real `createConcurrentStore` + locally re-declared queueing context (test fixtures don't cross modules; say so in a comment): (1) binding updates after drain+`waitForIdle`; (2) binding reads fresh state when recomposed via external `mutableStateOf` before drain (live-getter against the real store); (3) `bindingCatchesChangeThatLandedBeforeItSubscribed` variant — dispatch in a prior `DisposableEffect`, notifications never drained → re-sample catches it. Keep all 8 existing `FieldStateTest` cases and the `DeferredNotifyStore` fakes as fast pins. Build: `implementation(project(":redux-kotlin-concurrent"))` in compose jvmTest deps (test-scope; no published-POM impact). |
| FieldState reorder pin | **C2(a)** | compose jvmTest (`FieldStateTest`) | Evil `Store` wrapper whose `subscribe(l)` first dispatches a field-changing action *without* notifying, then delegates registration — lands exactly in the old re-sample→install gap with no later notification. Old order: stale forever (fails). New order: re-sample bumps. |
| Granular re-diff pin | **C2(b)** | granular commonTest (`FieldSubscriptionTest`) | Delegate store whose `subscribe()` dispatches a field change before delegating; `subscribeTo(triggerOnSubscribe=false)` must fire once with the new value (old code: silent miss). Test tolerates a benign duplicate fire. |
| `GranularOnConcurrentStoreStressTest` | **C4 tripwire** | granular jvmTest | 100 `subscribeTo` over real `createConcurrentStore` (Inline), 4-thread storm — final values correct, no CME, no lost final notification. Fails if fan-out ever leaves the writer lock. |
| `WriterSerializationOrderingTest` | **C7 tripwire** | concurrent jvmTest | Event-log ordering: parked Inline subscriber of dispatcher A completes strictly before dispatcher B's reducer runs. Latch-choreographed, no timing assertions. |
| Core guard tests | **C3** | core commonTest + concurrent commonTest | As listed under C3. |
| onError tests | **C6** | concurrent commonTest | As listed under C6. |

Stress-test rules of engagement: daemon executors, `CountDownLatch` choreography, generous (30s) awaits, assert completion not latency.

---

## Implementation order

Each stage is a separately reviewable PR (stacked off `master` per repo convention); gate per stage: `./gradlew detektAll && ./gradlew apiDump && git diff --exit-code '*.api' && ./gradlew build` (apiDump must be a no-op in every stage).

1. **Stage 1 — C3 core guard** (smallest, fully independent). `CreateStore.kt` uncomment + KDoc; `CreateStoreTest` additions; wrapper companion test; re-grep `examples/` for reducer-dispatch. Verify: full core + threadsafe + concurrent + granular + compose suites.
2. **Stage 2 — concurrent-store change set: C1 + C5 + C6, atomically.** TDD order: write `MirrorPublishOrderingTest` first, confirm it fails; then the `ConcurrentStore.kt` change (mirror publish first line of hook + `Registration` + guarded `onError`); then `QueueingNotificationContext`, `QueuedNotificationStoreTest`, `LockFreeReadGuaranteesTest`, `UnsubscribeSemanticsTest`, `CoalescingContextDeliveryTest`, onError tests. **Same commit:** all KDoc rewrites in `ConcurrentStore.kt`/`NotificationContext.kt`/`CreateConcurrentStore.kt`, `store-consistency-model.md` rewrite (+ regenerate assembled artifacts), taskflow `Notification.jvm.kt:12`. Build edge: concurrent jvmTest → granular. Verify: full concurrent suite incl. stress; granular + compose suites (downstream).
3. **Stage 3 — C2(a) FieldState reorder + T6.** `FieldState.kt` reorder + KDoc; reorder pin test; `ConcurrentStoreBindingTest`; compose jvmTest → concurrent build edge. Verify: all `FieldStateTest` cases green.
4. **Stage 4 — C2(b) granular activate() re-diff.** `SubscribeFields.kt` re-diff + KDoc + comment fix (the C4 comment fix can ride here); granular re-diff pin. Verify: full granular suite incl. `triggerOnSubscribe` pins.
5. **Stage 5 — C4/C7 doc completion + tripwires.** Remaining `NotificationContext`/`createConcurrentStore` constraint KDoc, consistency-model bullets; `GranularOnConcurrentStoreStressTest` (granular jvmTest → concurrent build edge); `WriterSerializationOrderingTest`. Verify: `detektAll` (KDoc gate), full build, website `yarn build` if any website docs were touched.

Hooks note: pre-commit runs `detektAll --auto-correct` — let it fix formatting, re-stage, commit again. Never `--no-verify`.

---

## Explicitly rejected alternatives

- **B-unified's write-section restructure (fan-out after lock release under a `notifyLock`)** — its headline C7 benefit is factually wrong (the dispatching caller still runs its round synchronously and rounds serialize on `notifyLock`, so dispatch latency stays serialized behind a slow subscriber); it also breaks on-context `getState` routing for Inline listeners, coalesces nested-dispatch notifications (delivery-arithmetic change), adds spurious notifications for middleware-swallowed actions, has an underspecified `pendingNotify` clearing protocol with its own lost-round race, and forfeits the for-free serial Inline delivery granular depends on.
- **CAS/lock hardening of granular `Entry.last` for multi-threaded contexts (C4)** — concurrent delivery scrambles `(old,new)` pairing regardless; hot-path cost for every conforming user to support a configuration with no legitimate use case.
- **Runtime serial-delivery detection in `NotificationContext`** — not portably implementable from inside a `fun interface` without per-dispatch overhead.
- **Blocking unsubscribe until in-flight callbacks complete (C5)** — would block the caller on the notification thread; deadlock-prone with Inline; the active-flag execution-time check is the correct portable bound.
- **`devCheck`/debug-only variant of the C3 guard** — Redux JS throws unconditionally; silent state corruption is the worst store failure mode; `getState`/`subscribe` already enforce the same guard unconditionally.
- **Crashing dispatch on a throwing `onError` (C6)** — a store must outlive its observers; println last-resort matches the existing `LogAndContinue` convention.
- **Documenting-only the granular registration window (C2(b), plan C's approach)** — fixes the race for Compose but leaves every raw `subscribeTo` consumer exposed; the ~10-line activate() re-diff fixes it at the layer that owns it with a benign double-fire worst case.
- **Replacing the `DeferredNotifyStore` fakes with real-store tests (T6)** — keep both; the fake deterministically models a pathological store and stays as a fast unit pin.