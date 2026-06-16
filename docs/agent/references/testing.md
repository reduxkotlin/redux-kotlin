---
tier: T1
concern: testing
derives_from:
  - examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardReducersTest.kt → BoardReducersTest
  - examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/AppStoreTest.kt → AppStoreTest
  - examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/feature/board/RenderIsolationTest.kt → RenderIsolationTest
  - examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/feature/account/AccountSwitchTest.kt → AccountSwitchTest
  - examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/infra/data/OfflineSyncE2ETest.kt → OfflineSyncE2ETest
  - examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/app/AccountRegistryTest.kt → AccountRegistryTest
assembles_into: [AGENTS.md, claude-skill]
rules: [C]
last_verified: { commit: ab2fb5b, date: 2026-06-11 }
---

# Testing & the verify loop

> Where each kind of test lives, what to assert at each layer, and the fast→slow command sequence an
> agent runs after writing code.

## Source-set layout — where a test goes

- **`commonTest`** (default): platform-uniform correctness that runs on every target. Pure reducers,
  selectors, action shape, codecs, the fake backend. A new feature's reducer test starts here:
  `examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardReducersTest.kt → BoardReducersTest`,
  and store-level routing tests too:
  `examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/app/AppStoreTest.kt → AppStoreTest`.
- **`jvmTest`**: JVM-only because it needs the JVM SQLite driver, coroutine virtual time, or the Compose
  UI test harness — none of which run uniformly across native/JS. The Compose render and account-switch
  tests, the offline-sync E2E, the SQLDelight round-trip, and the registry lifecycle test live here:
  `examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/feature/board/RenderIsolationTest.kt → RenderIsolationTest`,
  `examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/infra/data/OfflineSyncE2ETest.kt → OfflineSyncE2ETest`,
  `examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/app/AccountRegistryTest.kt → AccountRegistryTest`.
- **`jvmCommonTest`**: shared parent feeding both `jvmTest` and `androidUnitTest`; put shared JVM-side
  test deps there. taskflow does not need it.

`kotlin-test` is wired by the convention plugins — never add a test framework dependency by hand.

## What to assert at each layer

- **Pure reducer / selector — no store.** Call the function with a known model + action and assert the
  next model. `BoardReducersTest` checks the board integrity invariant (every card id appears in exactly
  one column) and that an unhandled action returns the *same* instance. Reducers and selectors are pure,
  so this is fast and deterministic — the bulk of your tests.
- **Store-level — full dispatch.** Wire a real store with an inline `NotificationContext` (synchronous
  on the caller thread), dispatch, and read a model back via `getModel`. `AppStoreTest` asserts a
  dispatched action routes to the right slot.
- **Effects + sync — virtual time.** Wire the real store + middleware + `FakeRemoteApi` + SQLDelight and
  drive coroutines under virtual time; assert queue draining, per-op revert, and in-flight transitions
  (`OfflineSyncE2ETest`). See [effects-sync.md](./effects-sync.md).
- **Render isolation — recomposition counts.** `RenderIsolationTest` (Rule C) counts per-column
  recompositions in a plain `remember`-held map and asserts an untouched column does not recompose on a
  card move.
- **Account switching — store binding.**
  `examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/feature/account/AccountSwitchTest.kt → AccountSwitchTest`
  drives two per-account stores and asserts each account restores its own remembered screen.
- **Snapshot / golden UI — the rendered frame.** Render a screen headlessly from a known dispatched
  state to a PNG and diff it against a committed golden, asserting the *visual* output a state
  produces (not just the model). The TaskFlow harness wires this via `redux-kotlin-snapshot`; see
  [snapshot.md](./snapshot.md).

## The verify loop (fast → slow)

1. **Compile + unit/reducer tests:** `./gradlew :examples:taskflow:composeApp:jvmTest` — compiles the
   jvm target and runs `commonTest` + `jvmTest`. This is the tight inner loop.
2. **All host-runnable targets:** `./gradlew :examples:taskflow:composeApp:allTests` — every target this
   host can run (jvm/js/wasmJs; native is host-gated).
3. **Lint gate:** `./gradlew detektAll` — `explicitApi()` is on, so every public declaration needs an
   explicit modifier **and** a KDoc (including nested `data class`es and their properties). Formatting
   auto-corrects; missing KDoc does not. Never `--no-verify`.
4. **API surface:** `./gradlew apiCheck` (and `./gradlew apiDump` to regenerate) — only relevant when you
   change a library module's public API; taskflow itself has none.

**Host-gating:** iOS-simulator tests need a Mac with the Xcode iOS SDK; if `iosSimulatorArm64Test` fails
locally with an SDK error that is environmental — trust CI for cross-platform. jvm/js/android compile on
every host.

## Pitfalls

- Putting a SQLDelight or coroutine-virtual-time test in `commonTest` — it will fail to run on
  native/JS. Keep it in `jvmTest`.
- Launching effect coroutines on `TestScope.backgroundScope` and driving the test with
  `advanceUntilIdle()` — `advanceUntilIdle` only drains until no FOREGROUND tasks remain, so
  background-only work is silently skipped: the effect never runs and the test "proves" a bug that
  doesn't exist. Give effects a foreground scope —
  `CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))` — or drive with
  `runCurrent()`, which runs current-time tasks regardless of the foreground/background flag.
- Testing process-death restore by dispatching the restore action by hand only — that skips the
  registry/decode path. Prime a real registry instead:
  `SaveableStateRegistry(restoredValues = saved) { true }` provided via `LocalSaveableStateRegistry`
  inside `runComposeUiTest` exercises the exact mechanism the anchor uses (see
  `redux-kotlin-compose-saveable/src/jvmTest/kotlin/org/reduxkotlin/compose/saveable/RestoreRetriggersEffectsTest.kt → RestoreRetriggersEffectsTest`
  and [state-persistence.md](./state-persistence.md)).
- Asserting on a board snapshot after a rejected op instead of asserting the single reverted op — masks
  whether the per-op inverse is correct.
- Writing recomposition-count state into a snapshot-backed `mutableStateOf` — it perturbs the very
  recomposition you are measuring; use a plain map.

## See also

- [feature-slice.md](./feature-slice.md) — the reducer/selector tests each slice ships.
- [effects-sync.md](./effects-sync.md) — the virtual-time sync E2E.
- [compose-binding.md](./compose-binding.md) — the render-isolation proof.
- [state-persistence.md](./state-persistence.md) — restore-path testing (registry-primed restore).
- [snapshot.md](./snapshot.md) — golden UI snapshots (`rk-snapshot`, `assertGolden`, the `snapshotUi` batch).
- [README](./README.md)
