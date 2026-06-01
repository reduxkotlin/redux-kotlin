# redux-kotlin-devtools — Redux DevTools Integration (Design)

**Date:** 2026-06-01
**Status:** Approved design, pending implementation plan
**Module:** `redux-kotlin-devtools` (new companion module)

## 1. Goal

Let a redux-kotlin `Store<S>` connect to the standard Redux DevTools so a
developer can watch dispatched actions and inspect state from the official
monitor UI, across every Kotlin Multiplatform target the library supports.

**v1 scope = monitoring-first MVP**: stream actions + state to DevTools
(action log, structured state/action inspector, diff). Time travel and the
other interactive controls are explicitly deferred to phase 2, with a design
that does not need an API change to add them.

The driving constraint, from the start: **zero developer work to get
connected.** Burden (annotations, config) is always opt-in, never required.

## 2. Background: how the DevTools integration actually works

There are two transports. We target the **Remote** one, because it is the only
transport reachable from a non-browser Kotlin runtime (JVM, iOS, native).

- **Browser extension** (`window.__REDUX_DEVTOOLS_EXTENSION__`): `postMessage`
  between page and content script. Extension-internal; only reachable from a
  JS/Wasm *browser* target via JS interop. **Out of scope.**
- **Remote** (`@redux-devtools/remote` ↔ `@redux-devtools/cli`): a WebSocket to
  a SocketCluster server (default `ws://localhost:8000/socketcluster/`) that the
  developer runs on their machine; the same process serves the monitor UI. The
  app is a WebSocket *client* of it. **This is what we implement.**

### 2.1 The monitor is dumb; the app owns all the state

This is the single most important fact. All stateful behaviour — the action
history, `computedStates`, time travel, skip/commit/rollback — lives **app-side**
in a "lifted reducer" (`@redux-devtools/instrument`'s `instrument.ts`). The
monitor only **sends commands and renders** the lifted state it receives. So a
Kotlin port must reproduce the lifted-state bookkeeping locally; the DevTools UI
computes nothing for us.

`LiftedState` shape (the wire model for time travel):

```
{ monitorState, nextActionId, actionsById: { id -> PerformAction },
  stagedActionIds: [id], skippedActionIds: [id], committedState,
  currentStateIndex, computedStates: [{ state, error? }], isLocked, isPaused }
```

`PerformAction = { type: "PERFORM_ACTION", action, timestamp, stack }`.

The monitor-command vocabulary (lifted action `type`s, all handled app-side):
`PERFORM_ACTION, RESET, ROLLBACK, COMMIT, SWEEP, TOGGLE_ACTION,
SET_ACTIONS_ACTIVE, JUMP_TO_STATE, JUMP_TO_ACTION, REORDER_ACTION,
IMPORT_STATE, LOCK_CHANGES, PAUSE_RECORDING`.

### 2.2 Transport protocol (verified against current source)

`@redux-devtools/cli` 5.x → **SocketCluster v20 → protocol v2**. (Older docs
describing SC v14 / protocol v1 are obsolete and led to a bug in an earlier
draft of this design — see §10.)

SocketCluster v2 wire facts a minimal client must honour:

- **Handshake first**: client sends `{"event":"#handshake","data":{},"cid":1}`;
  server replies `{"rid":1,"data":{"id":"...","pingTimeout":...}}`.
- **Heartbeat (v2)**: server sends a **bare empty-string text frame** `""`; the
  client must reply with an empty-string frame `""`. (NOT `"#1"`/`"#2"` — that
  is v1.) Getting this wrong = connect, then silent hang.
- **RPC invoke**: `{"event":name,"data":...,"cid":N}` → `{"rid":N,"data":...}`
  or `{"rid":N,"error":{...}}`. Correlate with a `cid` counter + pending-callback
  map.
- **transmit** (fire-and-forget custom event): `{"event":name,"data":...}` (no
  `cid`).
- **subscribe**: `{"event":"#subscribe","data":{"channel":name},"cid":N}`;
  inbound channel messages arrive as
  `{"event":"#publish","data":{"channel":name,"data":...}}`.
- Auth (`#authenticate` etc.) exists but **redux-devtools does not use it** — skip.
- Codec is plain JSON text — ignore the optional binary codec.

redux-devtools app layer on top of SC:

1. Connect, `#handshake`.
2. `invoke("login", "master")` → returns a **channel name** string.
3. `#subscribe` to that channel → inbound monitor commands arrive here.
4. `transmit("log", msg)` if we have a socket id, else `transmit("log-noid", msg)`
   for every app→monitor message.

### 2.3 Wire messages

App → monitor (`relay`), where `payload`/`action` are **double-encoded**: the
outer object is JSON, and `payload`/`action` are themselves a JSON **string**
(serializer output `.toString()`), which the monitor `parse()`s:

```jsonc
// per dispatched action
{ "type":"ACTION", "id":<socketId>, "name":<storeName>, "instanceId":<id>,
  "action":"{\"action\":{...},\"timestamp\":..,\"stack\":null,\"type\":\"PERFORM_ACTION\"}",
  "isExcess": false, "nextActionId": 2 }

// full lifted state, sent on monitor START / UPDATE
{ "type":"STATE", "id":..,"name":..,"instanceId":..,
  "payload":"<jsan-stringified LiftedState>" }

{ "type":"START" }   { "type":"STOP" }   { "type":"ERROR", "payload":"msg" }
```

There is **no literal `INIT`** over the Remote transport; "store created" maps to
relaying the initial `STATE`. `isExcess` flips true once
`stagedActionIds.length >= maxAge` (default 50).

Monitor → app (inbound on the subscribed channel): `START`/`STOP`/`UPDATE`
(handshake + "send me current state"), `DISPATCH` (a lifted/monitor command),
`ACTION` (dispatch a remote action — JS `eval`s it), `IMPORT`/`SYNC`.

## 3. Decisions (locked)

| Decision | Choice | Rationale |
|---|---|---|
| v1 ambition | Monitoring-first MVP | Proves transport; defers lifted-reducer + typing-wall fights |
| Platforms | All loved targets | KMP selling point; uniform Remote transport everywhere |
| Transport | Remote / SocketCluster v2 over Ktor WS, **CIO engine** | CIO covers all 12 targets incl. wasmJs/linux/mingw with WS — single engine, no branching |
| Serialization | **Tiered, zero-config default** | Burden is opt-in, never required (see §5) |
| Attach API | **Store enhancer** `devTools(config)` | Future-proof: phase-2 time travel slots in with no API change |
| Module | `redux-kotlin-devtools`, `convention.library-mpp-loved` | Mirrors `redux-kotlin-registry` template |

## 4. Architecture

`devTools(config)` returns a `StoreEnhancer<State>`. It wraps the inner store,
leaving `getState`/state semantics **untouched** (monitoring is read-only), and
wraps `dispatch` to record the action and relay it.

```
createStore(reducer, initial, devTools(DevToolsConfig(name = "MyStore")))
```

Components:

- **`SocketClusterClient`** — thin SC **v2** subset over a Ktor `WebSocketSession`:
  `#handshake`, empty-string heartbeat, `invoke` (cid/rid map), `transmit`,
  `#subscribe` + inbound channel demux. The single largest build cost
  (~days-scale, low-hundreds LOC; no auth, no client middleware).
- **`DevToolsSession`** — owns a `CoroutineScope` (`Dispatchers.Default`/IO), a
  buffered outbound `Channel`, reconnect-with-backoff, and the inbound command
  dispatcher. Lifecycle: connect → `login` → subscribe → `relay(START)`; on
  inbound monitor `START`/`UPDATE` → `relay(STATE, fullLifted)`.
- **`LiftedStateRecorder`** — bookkeeping only in MVP (no recompute): maintains
  `nextActionId`, `stagedActionIds`, `actionsById` (ring buffer capped at
  `maxAge`), `computedStates` (append current state per dispatch),
  `currentStateIndex`. Emits the `STATE` payload and per-action `ACTION`
  messages with `nextActionId`/`isExcess`. Structured so the phase-2 lifted
  reducer replaces the "no recompute" part without touching the wire layer.
- **Tiered serializer** (`expect`/`actual`) — see §5. Produces a
  `kotlinx.serialization` `JsonElement`; the wire layer `.toString()`s it for the
  double-encoded `payload`/`action` strings.
- **`DevToolsConfig`** — `name`, `host`/`port`/`secure` (default
  `localhost`/`8000`/`false`), `maxAge` (50), `instanceId`, action
  `allowlist`/`denylist` (regex on action class/`type` name), optional
  `serializer` override, `logger`. `features` flags are auto-set to advertise
  **only** controls we honour (jump/skip/dispatch/import/export = `false` in MVP)
  so the monitor hides what we can't service — honest capability advertising.

## 5. Serialization — the static-typing wall

JS auto-serializes any action/state via `jsan` (dynamic). Kotlin cannot reflect
arbitrary objects on native/JS/Wasm, and actions are typed `Any`. Resolution is a
tiered serializer with a **zero-config default and an optional upgrade**:

| Tier | Developer work | Result | Platforms |
|---|---|---|---|
| **Reflection** (default) | none | full structured JSON | JVM / Android |
| **`toString`** (default fallback) | none | text log, no diff | native / JS / Wasm |
| **`@Serializable`** (opt-in) | annotate + supply `KSerializer`/mapper | full structured JSON | **all** |

- JVM/Android reflection uses `kotlin-reflect` (a JVM-only, debug-only dependency)
  to walk data-class properties into a `JsonElement`. This is the real analogue of
  JS's dynamic behaviour and gives the rich inspector for free where it's cheap.
- Native/JS/Wasm have no usable member reflection (Kotlin/JS exposes only
  `simpleName`-level data), so `toString()` → `JsonPrimitive` is the only
  zero-config option there. The action log still works; the structured
  inspector/diff degrades to text.
- A developer who wants the rich inspector on iOS/Wasm opts into
  `@Serializable` + passes the serializer through config. Never required to
  connect.

`kotlinx.serialization` cannot auto-derive without the `@Serializable` annotation
(it is a compile-time plugin keyed on it); a custom compiler plugin to inject it
is out of scope.

## 6. Data flow

```
dispatch(action)
  → inner reducer runs (state updated as normal)
  → recorder.record(action, newState)            // bookkeeping + ring buffer
  → build ACTION message
  → enqueue on buffered outbound channel          // fire-and-forget, never blocks
       → DevToolsSession coroutine drains
           → serialize (tiered) → JsonElement.toString()
           → SocketClusterClient.transmit("log"|"log-noid", msg)

on connect              → relay(START)
on inbound START/UPDATE → relay(STATE, recorder.liftedState())
on inbound DISPATCH/ACTION/IMPORT (MVP) → logged + ignored (documented)
```

## 7. Error handling — a debug tool must never break the app

- The dispatch path **only enqueues**; it never blocks and never throws.
- Disconnected or full buffer → drop (oldest), no-op.
- Connection failure → reconnect with backoff (SC has no client-side
  auto-reconnect we rely on; we implement it).
- Per-action serialization failure → fall back to `toString` → if that also
  throws, skip that action and log once.
- All failures degrade silently through a configurable `logger`.

## 8. Threading

The session runs on its own scope; the dispatch thread only does a cheap enqueue,
so the enhancer is safe with `createConcurrentStore` / `createThreadSafeStore`.
The enhancer composes with `applyMiddleware` (relative ordering documented:
devTools should observe the *post-middleware* action stream).

## 9. Android setup (documented, manual in MVP)

The DevTools monitor for a non-browser app is the standalone server+UI, not the
browser extension. The Android app's `localhost` is the device, not the dev
machine — this is the whole difficulty.

| Scenario | Network bridge | App host |
|---|---|---|
| Emulator | `10.0.2.2` = host loopback (Genymotion: `10.0.3.2`) | `10.0.2.2:8000` |
| Emulator or USB device | `adb reverse tcp:8000 tcp:8000` (adb/USB tunnel) | `localhost:8000` |
| Device over WiFi/LAN | host LAN IP; server binds `0.0.0.0`; firewall open | `<lan-ip>:8000` |

Requirements:

1. **Dev machine**: Node + `@redux-devtools/cli` (`npx @redux-devtools/cli --open`)
   → SC server + monitor UI on `:8000`.
2. **App manifest**: `INTERNET` permission **+ cleartext allowed**. `ws://` is
   cleartext, blocked by default on `targetSdk` ≥ 28 → a debug
   `network_security_config.xml` permitting cleartext to the devtools host is
   required. **This is the silent-failure trap** (connect hangs, no error). Must
   be documented as step 0, loud.
3. **adb** (platform-tools) for the `adb reverse` / USB path.

Minimal developer steps:

```
1. debugImplementation("org.reduxkotlin:redux-kotlin-devtools:<v>")   // stripped from release
2. npx @redux-devtools/cli --open                                      // server + UI :8000
3. createStore(r, s, devTools())                                       // debug-only wiring
   emulator: works as-is (host 10.0.2.2) once cleartext is allowed
   USB device: + adb reverse tcp:8000 tcp:8000
```

## 10. Parity with JS redux-devtools

| Feature | JS | MVP | Phase 2 | Verdict |
|---|---|---|---|---|
| Action log stream | ✅ | ✅ | — | Full |
| Structured state/action inspect + diff | ✅ | ✅ JVM/Android · ⚠️ text elsewhere | ✅ via opt-in `@Serializable` | Full where JSON; text otherwise |
| Action filters / sanitizers | ✅ | ✅ | — | Full |
| Time travel (jump) | ✅ | ❌ | ✅ lifted reducer | Phase 2 |
| Skip/toggle, commit, rollback, reset, sweep | ✅ | ❌ | ✅ same lifted reducer | Phase 2 |
| Pause / lock recording | ✅ | ❌ | ✅ cheap | Phase 2 |
| Import / Export state | ✅ | ❌ | ✅ export easy; import needs lifted infra | Phase 2 |
| Reorder actions | ✅ | ❌ | ✅ low priority | Phase 2 |
| Test-case generation | ✅ | ✅ auto (monitor-side, needs JSON) | — | Free where JSON |
| Trace (stack capture) | ✅ | ❌ | ⚠️ JVM only (`Throwable`); native/js poor | Partial, low value |
| **Dispatch arbitrary action (Dispatcher `eval`)** | ✅ | ❌ | ⚠️ registered action-creators by index only | **Never full — the static-typing wall** |

**Summary:** Monitoring parity is *full* on JVM/Android, text-degraded elsewhere
(opt-in `@Serializable` fixes it). Time travel is fully reachable in phase 2 as a
mechanical port of `instrument.ts` into an enhancer-owned lifted reducer. The one
**permanent gap** is arbitrary remote-action `eval`: JS runs dynamic code, Kotlin
cannot; the ceiling is dispatching pre-registered action-creators by index.

## 11. Module setup (per CLAUDE.md)

- Add `:redux-kotlin-devtools` to `settings.gradle.kts`.
- `build.gradle.kts`: `convention.library-mpp-loved` + `convention.publishing-mpp`.
- `commonMain` deps: `api(project(":redux-kotlin"))`, Ktor client core +
  websockets (CIO engine), `kotlinx-coroutines-core`,
  `kotlinx-serialization-json`. `jvmMain`: `kotlin-reflect`.
- Package `org.reduxkotlin.devtools`.
- `explicitApi()` is on: every public declaration needs an explicit modifier +
  KDoc (incl. nested data classes and their properties). Run `./gradlew apiDump`
  after the public surface settles and commit the `*.api` dump.

## 12. Testing

- **commonTest** — recorder bookkeeping (id sequence, `maxAge` ring-buffer
  eviction, `isExcess` flip at the boundary), `ACTION`/`STATE` message envelope
  shape (incl. double-encoding), `toString` serializer tier, allow/deny filters.
- **jvmTest** — reflection-serializer fidelity (nested data classes, collections,
  enums, nulls); SocketCluster **v2** framing encode/decode against fixtures
  (handshake, empty-string heartbeat, cid/rid, `#subscribe`, `transmit`);
  integration against a Ktor fake WS server asserting the
  `handshake → login → subscribe → log` sequence. A real `@redux-devtools/cli`
  smoke test is CI-gated / manual.

## 13. Out of scope (v1) / future work

- **Phase 2 — time travel**: port `instrument.ts` as an enhancer-owned lifted
  reducer; honour `JUMP_*`, `TOGGLE_ACTION`, `COMMIT`, `ROLLBACK`, `RESET`,
  `SWEEP`, `REORDER_ACTION`, `PAUSE_RECORDING`, `LOCK_CHANGES`, `IMPORT_STATE`;
  flip the corresponding `features` flags on.
- **Phase 2 — Android ergonomics** (deferred, to revisit): emulator host
  auto-detect (`10.0.2.2`), loud connection logging, a Gradle `adb reverse`
  helper task, and a documented debug `network_security_config.xml` (auto-merge
  from the AAR considered but risky).
- Browser-extension transport (JS/Wasm `connect()` via JS interop).
- Arbitrary remote-action `eval` (permanently blocked; registered action-creators
  only).
- Action batching by `latency` window (MVP sends immediately).
- Trace stack capture.

## 14. Open implementation risks

1. **SC v2 heartbeat / handshake** must be exact — the highest-value thing to test
   first against a real server.
2. **Ktor CIO** version pin (≥ 3.5) for broadest WS coverage; verify each native
   target links.
3. **`kotlin-reflect`** is JVM-only — keep it in `jvmMain` and behind the
   debug-only dependency recommendation so it never reaches release.
