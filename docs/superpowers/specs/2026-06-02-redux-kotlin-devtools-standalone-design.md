# Standalone Redux DevTools (Desktop + Web) for redux-kotlin — Design

**Date:** 2026-06-02
**Status:** Approved (design); pending implementation plan
**Branch:** `feat/redux-kotlin-inapp-devtools`
**Related:**
- `2026-06-01-redux-kotlin-inapp-devtools-design.md` — the in-app drawer + the `-core`/`-remote`/`-inapp` modules this builds on.
- `ReduxKotlin Design System/` — brand foundations + the in-app UI kit (visual source of truth, reused here).

## Summary

A **standalone Redux DevTools monitor** that runs *outside* the app being debugged — a **native desktop app** (Compose Desktop / JVM, packaged as an installer) and a **web app** (Compose wasmJs/JS) — both reusing the **same Compose tab UI** built for the in-app drawer. The debugged app streams its `DevToolsEvent`s to the standalone over a WebSocket bridge; the standalone reconstructs each store's session into the same `InAppModel` the drawer uses and renders it in a desktop-class, multi-pane "IDE dock" layout with time-travel, global search, and session recording.

**Why it matters across platforms:** the in-app drawer needs Compose-material3, so it cannot exist on `linuxX64`/`mingwX64` or in headless/server-side Kotlin apps. The bridge needs only a Ktor WS client, so it reaches **every** target `redux-kotlin` supports. The standalone monitor is therefore the **only** devtools option for headless/native/server redux-kotlin apps, and a far better experience than the JS monitor for iOS/Native.

## Goals

- A standalone monitor (native desktop **and** web) that reuses the in-app Compose UI with **no UI re-implementation**.
- Stream actions, state, per-action **diffs**, and the **pipeline trace** from a debugged app on any platform to the monitor.
- Desktop-class IA: all inspector panels visible at once, scrub through history (time-travel display), search, and save/replay sessions.
- **Zero production impact on the debugged app** — the bridge is a debug-only, off-by-default `DevToolsOutput`, localhost-bound by default.
- Reach **every** redux-kotlin platform via the bridge, including those the in-app drawer cannot serve.

## Non-goals / scope tiers

- **P0:** native event-stream transport (transport A); shared `-ui` module; the standalone app (Desktop + Web) with the IDE-dock layout; **multi-store rail** (select store · filter to a subset · view-all, with store-name badges — see Identity model); **time-travel timeline (read-only scrubbing)**; **global search**; **save/load recording**; **kotlinx.serialization `ValueSerializer` tier**; security baseline (localhost + off-by-default + token for non-loopback); wire-protocol handshake + versioning.
- **P1:** timing/frequency charts; multi-session **side-by-side**; command palette; full hosted (native-less) web deployment.
- **Later:** **bidirectional dispatch** — driving the debugged app's state from the monitor (custom dispatch, edit-and-resend, true time-travel *reset*). Breaks the read-only guarantee; opt-in, separate design.
- **Not in scope:** a mobile (iOS/Android) build *of the monitor* — the in-app drawer already covers on-device; the monitor targets desktop + web only.

## Cross-platform reach

The bridge (`BridgeOutput`) is a Ktor WS **client** — no Compose. It compiles on every target the existing `-remote` module does (verified: `-remote` ships compiled CIO-native klibs for `iosArm64`, `iosSimulatorArm64`, `linuxX64`, `macosArm64`, `mingwX64`, plus JVM/Android/JS/wasmJs).

| Debugged target | In-app drawer | Bridge → standalone |
|---|---|---|
| JVM / Android | ✅ | ✅ (CIO) |
| iosArm64 / iosSimulatorArm64 / iosX64 | ✅ (no iosX64) | ✅ (CIO-native) |
| macosArm64 / macosX64 | ✅ (no macosX64) | ✅ |
| linuxX64 / linuxArm64 | ❌ | ✅ |
| mingwX64 | ❌ | ✅ |
| JS (browser/node) | ✅ | ✅ (js engine) |
| wasmJs | ✅ | ⚠️ best-effort (P1; browser WS/CORS caveats) |

- **Target expansion:** `-core` and `-bridge` adopt the broadest native set — add `linuxArm64`, `iosX64`, `macosX64` to the convention/targets — **gated on the base `redux-kotlin` module and the Ktor client both supporting them**. The plan must verify this cascade (adding a native target to `-core`/`-bridge` requires the same target on `redux-kotlin`); where the base library does not offer a target, the bridge cannot either, and that target is simply omitted with a note.
- **The opportunity, stated for marketing/docs:** headless and `linuxX64`/`mingwX64` redux-kotlin apps have *no* in-app option; the standalone monitor is their only devtools — a genuine differentiator.

## Architecture

### Modules

```
redux-kotlin-devtools-ui          (NEW; extracted from -inapp)
  - InAppModel, InAppState, OutputRow, actionType   (already public, moved here)
  - the five tab composables (Actions/State/Diff/Pipeline/Outputs)  (internal → public)
  - RkTokens, ReduxKotlinDevToolsTheme              (already public, moved here)
  - StoreRegistryModel (NEW): aggregates Map<StoreId, InAppModel> + a selection
      (All / Client / Store / Subset) + a merged-by-timestamp action view, each
      row tagged with its store identity. Shared by the in-app drawer + standalone.
  - package stays org.reduxkotlin.devtools.inapp.* (no FQN changes → non-breaking)

redux-kotlin-devtools-inapp       (now depends on -ui)
  - ReduxDevToolsHost, Drawer, Triggers, DevToolsController  (the drawer host only)

redux-kotlin-devtools-bridge      (NEW; deps: core, Ktor client)
  - BridgeOutput : DevToolsOutput  (WS client, off by default, localhost)
  - BridgeConfig (host, port, startEnabled=false, token?)
  - wire schema (@Serializable envelopes for DevToolsEvent + handshake)

redux-kotlin-devtools-standalone  (NEW; an APPLICATION, not a published library)
  - Compose Multiplatform: Desktop (JVM) + Web (wasmJs/JS)
  - JVM: embedded Ktor WS server (accepts BridgeOutput clients) + web host (serves the
    wasmJs bundle + a browser-facing WS feed)
  - the IDE-dock shell (built from -ui composables) + P0 features
  - deps: -ui, -core, kotlinx-serialization, Ktor server (JVM), Compose
```

`-inapp`'s committed ABI dump changes (tab composables become public, model/theme move modules) — regenerate `apiDump`. `InAppModel` is already pure and Compose-free, `DevToolsEvent`-stream driven (`seed()` + `submit()`), so it is reused verbatim by the standalone's ingestion **and** by save/load replay.

### Data flow

```
DEBUGGED APP (any platform)
  devTools() → DevToolsHub → BridgeOutput (Ktor WS client, dials out)
        | JSON-serialized DevToolsEvent stream (+ handshake)
        v
STANDALONE (native/JVM): embedded Ktor WS server
  decode → per-STORE InAppModel  (store key = clientId + storeInstanceId; from handshake)
         → grouped under their Client; aggregated by StoreRegistryModel
        | StateFlow<InAppState> per store
        v
  Desktop dock UI  (Compose Desktop)        Web UI (browser)
                                              ^  WS feed + served wasmJs bundle
                                              |  (same server, same InAppModel feed)
```

The standalone is the **server**; the debugged app dials out (mirrors `RemoteOutput`, and suits apps behind NAT/emulators). The web app is a **client** of the same server (option A: native app doubles as server + web host).

## Identity model & vocabulary

"Session" was conflating three things with different lifetimes. The standalone uses four precise concepts; **"session" is retired as a user-facing term** (core keeps the `DevToolsSession` type, documented as "one store's recording session" — see below):

| Concept | What it is | Lifetime / key |
|---|---|---|
| **Connection** | one WS link from a debugged process | ephemeral; reconnect = new connection (transport plumbing only) |
| **Client (app instance)** | the debugged app/device | stable `clientId` from the handshake; survives reconnects; display label (e.g. "TaskFlow · Pixel 7") |
| **Store** | a redux store within a client — *the unit you inspect* | key = `clientId + storeInstanceId`; backed by one core `DevToolsSession` (app side) → one `InAppModel` (monitor side) |
| **Recording** | a saved capture of a client's stores | frozen, read-only, disconnected; replayable |

- **Client → has → Stores.** A Store is what `-core` calls a `DevToolsSession`; the name is kept (it accurately means "one store's recording session") and documented. UI/spec speak **Clients**, **Stores**, **Recordings** — never "session."
- **One connection per store.** Because `DevToolsOutput.start(session)` is per-store, the app registers one `BridgeOutput` per store; each opens its own connection whose **handshake** carries the store's identity. `clientId` is shared across an app's connections, so the monitor groups its stores under one Client and keeps them stable across reconnects (no rail fragmentation).
- **In-app drawer** reuses the same `StoreRegistryModel`, keyed only by `storeInstanceId` (one client = this app; `clientId`/Connection don't apply in-process). This also delivers the in-app store-picker that Plan 3 deferred.
- Store identity therefore rides the **handshake** (per connection), not a per-event field — explicit, minimal overhead. Merged "view all" interleaves stores by `timestampMillis` (cross-*device* merge has clock skew — acceptable, noted).

## Transport & wire protocol

- **Transport A (P0):** `BridgeOutput` serializes each `DevToolsEvent` to a versioned JSON envelope and streams it over a raw WebSocket (not SocketCluster). On connect it performs a **handshake** (`Hello`) carrying: `protocolVersion`, `clientId` (stable per app instance), `clientLabel`, `storeInstanceId`, `storeName`, `serializerTier`, and `token` (when non-loopback). The standalone replies (`HelloAck`) with its accepted protocol version (capability negotiation; refuse/upgrade on mismatch).
- **Wire schema:** `@Serializable` envelopes — `Hello`/`HelloAck` (handshake, fields above), and one variant per `DevToolsEvent` (`Initialized`, `ActionRecorded`, `PipelineRegistered`, `PipelineTraced`) plus `PipelineStructure`/`PipelineTrace`/`DiffEntry`. `JsonElement` fields serialize natively. A top-level `protocolVersion` gates forward/backward compatibility; unknown variants are ignored by older peers.
- **Transport B (kept, reduced functionality):** the existing `-remote` SocketCluster path to the JS monitor is untouched. It drops pipeline events (the JS monitor can't render them); document exactly which `DevToolsEvent` variants B omits.
- **Backpressure / ordering / reconnect:** the bridge feeds from the session's multicast `SharedFlow`; a bounded outbound buffer with a drop-oldest policy protects a slow socket (mirrors the recorder's `isExcess` notion, logged). On reconnect the bridge **reseeds** from `DevToolsSession.liftedState()`/`history()` (full snapshot), then resumes live — ordering across reconnect is snapshot-then-stream, not a fragile cursor.

## Security baseline (P0)

- **Off by default** (like `RemoteOutput`): the bridge does nothing until enabled (`BridgeConfig.startEnabled` or an in-app/drawer toggle).
- **Localhost-bind by default:** both the standalone server and the bridge default to `127.0.0.1`. Streaming app state off the loopback interface is a deliberate act.
- **Token for non-loopback:** if `host` is non-loopback, a shared `token` is required in the handshake; the server rejects unauthenticated non-loopback clients. TLS (`wss`) is optional/configurable.
- **Debug-only on the debugged app:** the bridge is wired as `debugImplementation` and is never present in release (same recipe as `-remote`/`-inapp`).

## Serialization (kotlinx.serialization tier — P0)

`platformDefaultSerializer()` is JVM-reflection-only; iOS/native/JS fall back to `ToStringValueSerializer`, which would make the State/Diff/search/time-travel panels opaque string-blobs **exactly** on the platforms where the bridge is the only devtools option. Therefore P0 adds a **kotlinx.serialization-based `ValueSerializer` tier**: the integrator supplies their state's `KSerializer<S>` (and optionally per-action serializers) via `DevToolsConfig.serializer`, yielding structured JSON state on every platform. Documented as the recommended setup for non-JVM apps. (`kotlinx-serialization-json` is already a `-core` dependency.)

## Standalone UX

**Layout — IDE dock (all panels visible):** narrow **store rail** (Clients → Stores; see Identity model) · **action log** (chronological, searchable, tap to select) · **State + Diff** stacked center · **Pipeline** docked right · **time-travel timeline** along the bottom. Splitters are resizable; panels dockable. Selecting an action updates every panel together. Material 3 Expressive, dark default, brand tokens — identical look to the drawer.

**P0 features:**
- **Multi-store rail + view-all.** Stores listed (grouped under their Client when more than one client is connected; flat for the common single-app case). Selection modes: **one store**, a **subset** (filter-by-store), or **All** — All/Subset show a merged action log interleaved by timestamp, each row carrying a **store-name badge** (mono chip); the badge is hidden in single-store mode to avoid clutter. State/Diff/Pipeline follow the selected row's store. Each store shows connection status; a disconnected store **freezes read-only** (kept for inspection, not evicted) until dismissed.
- **Time-travel timeline (read-only).** Scrub/click to select any recorded action; the State/Diff/Pipeline panels show that action's recorded snapshot. The debugged app is **not** reset — P0 is inspection only; state-*reset* is the deferred bidirectional feature. The read-only model has no dispatch path, so the P0/Later boundary cannot leak. The standalone retains more history than the in-app `maxAge` (desktop memory), bounded by a configurable cap.
- **Global search.** Substring/regex over action type + payload + serialized state; jump-to-match. Operates on the session's accumulated `InAppState`.
- **Save / load recording.** Capture the received event stream (with a **versioned file header**: protocol version, serializer tier, session metadata) and replay it into a fresh `InAppModel`. Desktop writes/reads a `.jsonl` file via the filesystem; **web** uses Blob download / File-input upload (no filesystem) — same schema, platform-split I/O.

**P1:** timing/frequency charts (per-node pipeline timing over time, action frequency, state-size growth); multi-session **side-by-side** (two rails' inspectors at once); command palette + full keyboard nav.

## Native vs web specifics

- **Native desktop = JVM Compose Desktop** (Skia/JVM), packaged as a native installer with a bundled JRE — there is no Kotlin/Native Compose-desktop path; do not imply a native binary. Embedded `ktor-server-cio` runs inside the app (already used in `-remote` jvmTest). Watch: bind to `127.0.0.1`, handle port-in-use on relaunch, expect a firewall prompt.
- **Web = Compose wasmJs/JS** in the browser sandbox: a WS **client** only (browsers can't listen). It discovers the WS endpoint via **same-origin** (`location.host`) since the native app serves both the bundle and the feed. Save/load via Blob/File APIs.

## Error handling & safety

- The bridge inherits the "instrumentation must never break the host store" contract: every send path is wrapped (`runCatching`), failures are logged via `config.logger`, never rethrown into dispatch; a dead socket degrades to buffering/dropping, never blocks dispatch.
- The standalone treats every inbound message defensively: malformed/unknown envelopes are logged and skipped; a misbehaving client cannot crash the monitor or other sessions.
- Read-only by construction in P0 — no path from the monitor back to the debugged app.

## Testing strategy

- **-ui (extraction):** the moved `InAppModel` tests still pass; add a test that the tab composables are public and render given an `InAppState`. **`StoreRegistryModel`:** selection modes (one/subset/All), merged action ordering by `timestampMillis` across stores, per-row store identity, and that single-store mode hides the badge.
- **-bridge:** `BridgeOutput` lifecycle (off by default, start/stop, reconnect-reseed); wire round-trip — a `DevToolsEvent` serialized by the bridge decodes to an equal event on the standalone side (shared schema); handshake carries `clientId`/`storeInstanceId` and a reconnect re-attaches to the *same* store entry (stable `clientId`, no rail fragmentation); version negotiation; token gating on non-loopback; backpressure drop under a stalled sink.
- **standalone (headless-testable core):** the ingestion layer (decode stream → `InAppModel`) unit-tested without UI; save→load round-trip reproduces the same `InAppState`; search/filter logic; session-key collision handling; a Compose smoke test (desktop) that renders the dock from a seeded model and scrubs the timeline.
- **xplat compile:** the bridge compiles on every claimed target (CI host-gates native); the kotlinx.serialization tier produces structured JSON on a non-JVM target test.

## Production-safety summary

The only artifact that ever touches the debugged app is `-bridge`, wired `debugImplementation`, off by default, localhost-bound, never in release (mirrors `-remote`/`-inapp-noop` guidance in `docs/devtools.md`). The standalone is a separate application — it cannot affect production.

## Rollout / sequencing (for the implementation plan)

1. Extract `redux-kotlin-devtools-ui` from `-inapp` (move model/theme, promote tab composables to public, repoint `-inapp`); add `StoreRegistryModel` (multi-store aggregation: selection All/Client/Store/Subset + merged-by-timestamp action view with per-row store identity); regenerate ABI dumps. Retro-fit the in-app drawer's store-picker on top of `StoreRegistryModel`.
2. Add the kotlinx.serialization `ValueSerializer` tier to `-core`.
3. Build `-bridge` (`BridgeOutput`/`BridgeConfig`, wire schema + handshake + versioning, security, reconnect-reseed); expand `-core`/`-bridge` native targets (verify the `redux-kotlin` cascade).
4. Build `redux-kotlin-devtools-standalone`: ingestion (server + handshake → per-store `InAppModel` keyed `clientId + storeInstanceId`, grouped via `StoreRegistryModel`), then the desktop dock shell + P0 features (multi-store rail with select/filter/All + store-name badges, timeline scrub, search, save/load).
5. Wire the web variant: serve the wasmJs bundle + browser WS feed from the JVM app; same-origin discovery; Blob save/load.
6. Docs + a sample: point taskflow's bridge at the standalone; integration recipe in `docs/devtools.md`.

## Open items (resolve at plan time)

- Exact achievable native target set after verifying the `redux-kotlin` base + Ktor client support for `iosX64`/`macosX64`/`linuxArm64`.
- Whether `-standalone` is published as a runnable distributable or lives under `tools/`/`examples/`.
- wasmJs Ktor-client WS maturity for the *debugged* web app (P1 best-effort).

---

## Appendix — Hi-fi mockup prompt (hand to the design-system skill)

> Use the **ReduxKotlin Design System** skill (`docs/superpowers/specs/ReduxKotlin Design System/`) to produce high-fidelity HTML mockups of the **Standalone Redux DevTools — desktop monitor**, per `docs/superpowers/specs/2026-06-02-redux-kotlin-devtools-standalone-design.md`. Brand: Material 3 Expressive, **dark default** (sheet/surface `#0E1726`), primary blue `#137AF9`, secondary magenta `#C858BC`, tertiary orange `#F98909`, success green `#5FD39A`, diff amber `#F9B357`, error red `#FF7A8A`; **JetBrains Mono** for all log/JSON/diff/timing text, **Roboto Flex** for UI; the magenta→orange gradient used sparingly (active tab/indicator, logo, accents). Reuse the visual language of the existing in-app DevTools UI kit (`ui_kits/devtools/`) — same row styles, JSON-tree leaf colors (string=green, number=orange, bool=magenta), diff +/~/− rows, lit pipeline nodes — but recomposed for a **wide desktop window**, not a phone sheet.
>
> Produce a wide (≥1440px) desktop window mockup of the **IDE-dock layout**: a top bar (app/store picker, global search field, connection status "● N clients", capture controls ⏸/⟲/💾/🗑); a narrow left **store rail** grouped by Client → Stores (e.g. Client "TaskFlow · desktop" containing stores "TaskFlow-root" and "Account-2", each store with a live dot, plus an **"All stores"** entry at top and multi-select for filtering); an **action log** column (id · type in mono · payload preview · timestamp, selected row highlighted with a left gradient bar + count badge; in **All/multi mode** each row also shows a small **store-name chip**); a center area with **State** (recursive JSON tree) above **Diff** (added/changed/removed rows) split by a resizable divider; a right-docked **Pipeline** panel (vertical `dispatch → logger → thunk → effects → rootReducer{todos✓ filter·}` with lit nodes, per-node µs timing, "changed" chips, and a legend); and a full-width **time-travel timeline** along the bottom (scrubber with action ticks, selected marker, ◀/▶, "#40 / #42"). Use the redux-kotlin Todo/TaskFlow domain for realistic content (AddCard, MoveCard, SetFilter, @@INIT). Render light and dark, dark as the hero.
>
> Also produce: (2) a **session side-by-side** variant (two inspector columns for two stores, P1); (3) the **command palette** overlay (P1); (4) the **web** variant note (identical UI in a browser chrome frame). Keep it to layout + brand fidelity, not pixel-perfect polish; output static HTML files I can open, copying assets from the design system. No emoji.
