# In-App Redux DevTools for redux-kotlin — Design

**Date:** 2026-06-01
**Status:** Approved (design); pending implementation plan
**Branch:** `feat/redux-kotlin-inapp-devtools`
**Related:**
- `2026-06-01-redux-devtools-integration-design.md` — the WebSocket remote transport.
- `ReduxKotlin Design System/` — brand foundations (`colors_and_type.css`) + the high-fidelity
  **In-App DevTools UI kit** (`ui_kits/devtools/`), the canonical visual source of truth for `-inapp`.

## Summary

Add **in-app** developer tools for redux-kotlin: an integrator adds one debug-only
dependency, wires the `devTools()` store enhancer, and wraps the app root in a single
`ReduxDevToolsHost { }` composable. They then get an in-app drawer — opened by edge-swipe or a
floating bubble — that shows the action log, a state-tree inspector, per-action diffs, and a
live pipeline view (middleware + reducers in processing order, lit up per selected action).

**One integration powers both transports.** The same enhancer feeds a process-global hub.
In-app rendering is the default output; streaming to the external Redux DevTools monitor over
WebSocket is an **optional output of the same hub**, off by default (it carries WS overhead) and
turned on either by a config parameter or by a **toggle inside the in-app drawer**. There are no
existing users, no adoption, and therefore no migration paths or compatibility shims to maintain —
the API is shaped freely for the unified model.

The tool runs **inside the host app's process and Compose tree** on every Compose Multiplatform
target (Android, iOS, Desktop, Web/Wasm). It is read-only and must have **zero production impact**.

## Goals

- One debug dependency + one enhancer + one composable = working in-app devtools.
- The **same integration** also enables remote streaming, as an optional output toggled by config
  or from the drawer.
- Read-only inspector: action log, state inspector, per-action diff, pipeline overview.
- Pipeline view = a static structural map of `dispatch → [middleware…] → reducer{slices}` that
  lights up the nodes a selected action traversed, with per-node timing.
- Cross-platform via one Compose Multiplatform UI (Material 3 Expressive).
- **Zero production impact by construction** and integration that makes shipping-to-prod the
  hard path, not the easy one.

## Non-goals (v1, YAGNI)

- Time travel (jump-to / skip / recompute), manual action dispatch, import/export.
- Shake-to-open (deferred; edge-swipe + bubble + programmatic cover v1).
- Release-build runtime safety beyond the no-op artifact.
- A Gradle plugin that auto-wires debug/release variants (documented manual recipe for v1).

## Architecture

### Module layout (extract core + 3 artifacts)

```
redux-kotlin-devtools-core        (KMP, no UI, no Ktor)
  - LiftedStateRecorder, ValueSerializer + platform tiers, Clock, JSON diff
  - DevToolsHub          : process-global, debug-only registry of per-store sessions + outputs
  - devTools()           : the ONE enhancer — transport-agnostic, records + publishes only
  - devToolsMiddleware(), devToolsCombineReducers(), named()  : pipeline-capturing combinators
  - PipelineModel        : static structure + per-action trace
  - DevToolsOutput       : interface for a toggleable consumer of the hub feed

redux-kotlin-devtools-remote      (deps: core, Ktor)
  - WS / SocketCluster code as a DevToolsOutput that self-registers with the hub
  - off by default; enabled via config flag or the in-app toggle

redux-kotlin-devtools-inapp       (deps: core, Compose Multiplatform)
  - ReduxDevToolsHost { app } + drawer UI + triggers
  - renders the hub feed; lists available outputs (e.g. remote) with on/off toggles

redux-kotlin-devtools-inapp-noop  (no deps)
  - identical public API to -inapp + the core app-facing symbols, all empty bodies
```

### The hub-as-rendezvous

The single `devTools()` enhancer owns no transport. It records action + state into
`DevToolsHub.session[instanceId]` and publishes an event stream (`Flow`). Consumers are
**`DevToolsOutput`s** that subscribe to the hub:

- The `-inapp` UI is an output that collects the session `Flow` as Compose state. It is the
  default surface and also renders a control panel listing every other registered output with an
  on/off toggle.
- The `-remote` WS sink is an output that **self-registers** with the hub when the `-remote`
  artifact is on the (debug) classpath. It is **off by default** because of its connection
  overhead; it starts when (a) a config parameter requests it at startup, or (b) the user flips
  its toggle in the drawer. If `-remote` is absent, no remote toggle appears.

Consequences:

- One integration (`devTools()` + `ReduxDevToolsHost`) yields in-app tools, and remote streaming
  whenever the `-remote` artifact is present and enabled.
- Both outputs can run **at the same time** (external monitor *and* in-app drawer).
- **Multi-store** falls out for free — each enhanced store is a session; the drawer gets a
  store-picker shown only when more than one session exists.
- New output types (file log, custom sink) can be added later by implementing `DevToolsOutput`
  without touching the enhancer or the UI.

### Data flow

```
store.dispatch(action)
   |  devTools() enhancer: capture (action, immutable state ref)   <-- cheap, on dispatch thread
   v
DevToolsHub.session[instanceId]
   |  serialize + diff + build lifted state                         <-- on hub background coroutine
   v
 publish Flow  ----> inapp output  (collect -> drawer; hosts output toggles)
              ----> remote output (optional; enqueue -> WS -> external monitor)

devToolsMiddleware / devToolsCombineReducers
   publish pipeline structure (once) + per-action node timings into the same session.
```

## Pipeline capture

The redux-kotlin middleware/reducer composition collapses into opaque lambdas before any enhancer
runs, so an enhancer alone cannot see individual middleware. v1 captures structure with
**devtools-owned drop-in combinators** (a core SPI that lets a plain enhancer do this is a
documented future path):

- **`devToolsMiddleware(vararg NamedMiddleware): StoreEnhancer`** — drop-in for `applyMiddleware`.
  Because it builds the chain itself it owns the ordered list. Each middleware is wrapped to record
  enter/exit + duration; the wrapper calls the real middleware and returns its result untouched.
  `named("logger", mw)` supplies a label; bare middleware fall back to `mw[i]` or a JVM reflection
  name.
- **`devToolsCombineReducers(...): Reducer`** — drop-in for core's combine. Captures slice names
  (free, from the keys) and wraps each slice reducer for per-slice timing and a "which slices
  changed" signal per action.
- **Timing:** `kotlin.time.TimeSource.Monotonic` — multiplatform, no expect/actual.
- **`PipelineModel`:** static structure (ordered nodes `dispatch → [mw…] → reducer{slices}`)
  registered once; per-action **trace** = list of `(nodeId, duration, forwarded/changed)`. The UI
  draws the static map and lights nodes from the selected action's trace.
- **Graceful degradation:** if the integrator keeps plain `applyMiddleware`/`combineReducers`,
  the Pipeline tab shows aggregate boundary timing and generic labels instead of per-node detail.
  Forgetting to swap **never breaks** the store.

**Open implementation detail (non-blocking):** core's `combineReducers` may key by type rather
than string in its typed variant; that affects how slice names are derived. Verify at
implementation time.

## UI (`-inapp`)

The **In-App DevTools UI kit** (`ReduxKotlin Design System/ui_kits/devtools/`) is the canonical
visual spec — a high-fidelity, animated M3 Expressive realization of everything below. Build the
Compose UI to match it; see **Design system & theming**.

- **`@Composable ReduxDevToolsHost(config: InAppConfig = InAppConfig()) { content }`** wraps the
  app root. Renders `content` plus an overlay layer (drawer + bubble + edge-swipe detector),
  **inside the app's own Compose tree** (no system overlay window — see Security).
- **Compose Multiplatform `material3`, Material 3 Expressive**, following current guidelines.
  Adaptive layout via `WindowSizeClass`: `ModalBottomSheet` on compact widths (phone), a persistent
  **right-docked panel** on expanded widths (tablet/desktop/web) showing the action list and the
  inspector at once — same data, same components, same one integration.

- **Drawer chrome (bottom sheet):** drag-handle grabber; header = ReduxKotlin logo + "Redux DevTools"
  title + a **store chip** (the session name) + a pulsing **LIVE** indicator + close button. Tab bar
  has a **gradient indicator that slides** between tabs (`--rk-gradient`); content cross-fades on
  switch. The Actions tab carries an **action-count badge**. Default scheme is **dark**
  (sheet surface `#0e1726`); honor host light/dark via `InAppConfig.theme`.

- **Five tabs** (icons = Material Symbols Rounded): **Actions** `list_alt` · **State** `account_tree`
  · **Diff** `difference` · **Pipeline** `lan` · **Outputs** `tune`. Selecting an action in **Actions**
  drives State, Diff and Pipeline — all reflect that action. A **ContextBar** (`#id · type · duration`)
  heads the State/Diff/Pipeline tabs.
  - **Actions** — scrolling log: id, type (mono, accent), payload preview, timestamp; text filter;
    tap to select. (The kit's **Replay** control re-streams a recorded session for the showcase;
    v1 is **live-follow** — true time-travel/replay is a non-goal.)
  - **State** — recursive `JsonElement` tree, expandable nodes; leaves colored by type
    (boolean = magenta, number = orange, string = green).
  - **Diff** — core-computed added / changed / removed paths vs the previous state, for the selected
    action; summary count chips; rows accented green `+` / amber `~` / red `−` with an inset left bar
    (changed shows before strck-through → after).
  - **Pipeline** — the static map (`dispatch → [middleware…] → rootReducer{slices}`) drawn vertically
    with connectors; nodes the selected action traversed **light up with a glow** + per-node ms;
    slices that produced new state get a **"changed"** chip; legend = traversed / produced new state / skipped.
  - **Outputs** — lists registered `DevToolsOutput`s with M3 switches: **in-app** locked-on; **remote**
    shows a "connected" pulse when enabled (off by default — it leaves the device over WS); a **file-log**
    output is depicted as the example third sink. Toggling here starts/stops that output live.
- **Store-picker** appears when the hub has more than one session (the header store chip becomes a picker).
- **Triggers** (in `InAppConfig`, all configurable): edge-swipe (`pointerInput` drag detector, a gradient
  edge tab) and a **floating draggable bubble** (ReduxKotlin logo, pulse, unread-action badge) are
  **default-on**; `ReduxDevTools.open()/close()` and a desktop/web keyboard shortcut are available for
  custom triggers. Shake is deferred.

## Design system & theming

The `-inapp` module renders against a Compose `MaterialTheme` built from the ReduxKotlin brand tokens
(`ReduxKotlin Design System/colors_and_type.css`), so the tool looks on-brand regardless of the host's theme:

- **Color → `ColorScheme`:** primary = blue `#137AF9`, secondary = magenta family, tertiary = orange
  family, error `#BA1A1A`, success `#1F8A4C`; full M3 surface/container/outline tonal sets, light + dark.
  The **signature gradient** `#C858BC → #F98909` is used sparingly — tab indicator, bubble, edge tab,
  Replay/CTA accents — never as a wash.
- **Type → `Typography`:** **Roboto Flex** for UI/headlines; **JetBrains Mono** for *all* log, JSON,
  diff paths and timing text (code is first-class) and tracked-uppercase eyebrows. Full M3 type scale.
- **Shape / elevation / motion:** M3 expressive corner scale (sheet top corners = `xl` 28px), 5-step
  soft elevation, emphasized easing `cubic-bezier(0.3,0,0.1,1)` + gentle spatial overshoot
  `cubic-bezier(0.34,1.36,0.64,1)`; durations 150/250/400ms. Sheet springs up with overshoot; action/diff
  rows stagger in; pipeline nodes light in sequence with a traveling pulse along connectors.
- **Iconography:** Material Symbols Rounded (the canonical M3 set); the ReduxKotlin gradient logo for the
  bubble + sheet header. No emoji. Voice: calm, precise, developer-to-developer.
- Tokens ship as Compose constants in `-inapp` (a `ReduxKotlinDevToolsTheme`) so the kit's CSS variables
  have a 1:1 Kotlin counterpart.

## Configuration

- core `DevToolsConfig` (shared): `name`, `instanceId`, `maxAge`, `allowlist`, `denylist`,
  `serializer`, `logger`.
- `-remote` `RemoteConfig`: `host`, `port`, `secure`, plus `startEnabled` (default `false`) — when
  `true`, the remote output connects at startup instead of waiting for the drawer toggle.
- `-inapp` `InAppConfig`: enabled triggers, start tab, theme.

## Public API surface

- **core:** `devTools(config): StoreEnhancer`, `devToolsMiddleware(vararg NamedMiddleware): StoreEnhancer`,
  `named(label, mw)`, `devToolsCombineReducers(...): Reducer`, `object DevToolsHub`,
  `interface DevToolsOutput`.
- **inapp:** `@Composable ReduxDevToolsHost(config) { content }`, `object ReduxDevTools { open(); close() }`.
- **remote:** `RemoteOutput(config)` that registers itself with the hub (presence on the classpath
  surfaces the remote toggle); `RemoteConfig.startEnabled` controls auto-connect.

## Production-safety & footgun review

### Build size / accidental release shipping (the #1 footgun)

`ReduxDevToolsHost { }` and `devTools()` are called from the app's **main** source set, so a
`debugImplementation`-only dependency would not compile in release — tempting integrators to use
`implementation(...)` and ship the whole tool to production.

Mitigation (LeakCanary's proven pattern): a **no-op sibling** for every app-facing artifact with an
**identical public API** but empty bodies and **zero transitive deps** (no Compose, no Ktor, no core):

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp:<version>")
releaseImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp-noop:<version>")
```

Release compiles, links ~nothing, runs nothing. Wrong wiring degrades to safe.

**Rough edge:** `debug/releaseImplementation` are Android build-variant features. iOS/Desktop/Web
KMP need dependency-substitution-by-build-type or a compile flag; v1 documents the manual per-target
recipe. A Gradle plugin to auto-wire real/no-op is future work.

### Performance

- **Production:** with the no-op, zero — no enhancer, no hub, no allocations.
- **Debug:** redux state is immutable by contract, so the dispatch path only **captures the state
  reference + action** (cheap) and enqueues; **serialization + diff run on the hub's background
  coroutine**, off the dispatch thread. The reflection-heavy work never blocks dispatch. Allow/deny
  filters skip noisy actions; `maxAge` ring buffer + conflated `Flow` bound memory.
- Remote streaming is **opt-in**, so its WS overhead is never paid unless requested.
- Pipeline timing is monotonic-clock nanos — negligible.

### Security

- In-app data stays **in-process** — never leaves the app. Remote streaming (which does leave the
  process over WS) is off by default and clearly toggled, so emitting data off-device is always a
  deliberate act.
- **No system-overlay window.** The bubble and drawer live inside the app's own Compose tree via
  `ReduxDevToolsHost`, so **no `SYSTEM_ALERT_WINDOW` or other dangerous permission** is requested.
  This is a hard design constraint.
- Sensitive state (tokens/PII) is visible in debug; redaction is available via a custom
  `ValueSerializer`, and action allow/deny filters apply. Documented.
- Reflection reads private fields on JVM debug builds only; the no-op strips it from release.

### Integration ease (anti-footgun)

- Combinators are **exact drop-ins** for `applyMiddleware`/`combineReducers`; forgetting to swap
  them degrades the Pipeline tab, never breaks the store.
- `devTools()` / `ReduxDevToolsHost` are null-safe: misconfiguration degrades to no-op, never crashes.
- The 3-artifact split means core-only users never pull Compose or Ktor; remote is pulled only when
  wanted.
- The hub is a debug-only process singleton; the no-op has no hub, so no static state leaks into release.
- Copy-paste setup blocks per platform live in the README.

## Threading

Record + reference-capture happen synchronously on the dispatch thread (cheap). The hub publishes
to a `MutableSharedFlow`; the Compose UI `collectAsState` on the UI dispatcher. The recorder keeps a
`@Volatile` one-behind snapshot — acceptable for a debug tool. The hub's per-store session map,
output registry, and their writers/readers must be thread-safe (dispatch thread writes, UI thread
reads, multiple stores run concurrent sessions).

## Error handling — read-only safety contract

Inherits the existing "a debug tool must never break the host store" rule:

- Every instrumentation path (`record`, `publish`, combinator wrappers, output delivery) runs in
  `try/catch`, logs via `config.logger`, and never rethrows into the host.
- Combinator wrappers are transparent: they call the real middleware/reducer and return its exact
  result; timing/recording happens only around the boundary.
- The serializer never throws (`ToStringValueSerializer` is already hardened).
- Buffers are bounded (`maxAge` ring buffer; conflated/replay-limited `Flow`).

## Testing strategy

- **core:** keep existing recorder + serialization tests. Add:
  - combinator tests — order and names captured; per-node timing recorded; **transparency**
    (wrapped result is identical to unwrapped); instrumentation failure is swallowed, never thrown.
  - hub publish/subscribe tests (multi-session, conflation, thread-safety, output register/toggle).
  - JSON diff tests; `PipelineModel` structure + trace tests.
- **inapp:** UI state / view-model logic unit-tested in commonTest (state-tree expansion, diff
  rendering, filter, store-picker, output-toggle list); Android instrumented smoke test for the host
  + drawer; trigger detector tests (edge-swipe threshold, bubble drag, programmatic open).
- **remote:** the WS output behaves correctly as a hub `DevToolsOutput` — registers, connects only
  when enabled (config or toggle), streams the same payloads, and tears down cleanly when toggled off.
- **no-op:** a test asserting the no-op artifact's public API matches `-inapp` and core's app-facing
  surface (so release builds always compile against it).

## Rollout / sequencing (for the implementation plan)

1. Extract `redux-kotlin-devtools-core` from the WS module; reshape the WS code into a
   `DevToolsOutput` (`-remote`) that subscribes to the hub. No compatibility constraints — the API
   is reshaped to the unified model.
2. Add `DevToolsHub` (sessions + output registry) + transport-agnostic `devTools()` + the
   in-process feed; wire `-remote` as a self-registering, default-off output with `startEnabled`.
3. Add pipeline combinators + `PipelineModel`.
4. Build `-inapp`: `ReduxKotlinDevToolsTheme` (brand tokens → M3 `ColorScheme`/`Typography`/shapes),
   then the Compose MP UI matching the **devtools UI kit** — adaptive sheet/panel chrome, the five
   tabs (Actions·State·Diff·Pipeline·Outputs), triggers (bubble + edge tab) — plus `-inapp-noop`.
5. Per-platform integration docs + samples (the design system's `sample-app` UI kit is the reference host).
