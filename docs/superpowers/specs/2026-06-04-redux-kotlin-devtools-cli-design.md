# Redux-Kotlin DevTools CLI — Design

**Date:** 2026-06-04
**Status:** Design (approved for implementation planning)
**Scope:** A single-entry-point CLI that bundles the DevTools bridge receiver, an append-only capture,
the bundled GUI monitor, and an agent-facing query surface over the captured action/state/diff log.
JVM-only dev tooling. Read-only inspection.

---

## 1. Why — benefit to AI agents

An agent building a redux-kotlin (Kotlin Multiplatform) app today debugs half-blind: it can compile
and unit-test, but it cannot *see* runtime — which actions fired, in what order, what state changed,
what errored. The DevTools bridge already emits exactly that telemetry (`action + post-state +
leaf diffs + timestamps`) over a WebSocket, but the only consumer is a human GUI. There is no CLI, no
headless launch, and no programmatic/query access.

This project adds the missing agent-readable surface, converting an already-built telemetry stream into
a **write → run → observe → fix** loop. Because the wire protocol, ingest, and recording format already
exist, the cost is concentrated in a thin new module — high leverage per unit of work.

This is **not** the rejected scaffold/bootstrap CLI (see the AI-integration umbrella spec, §9). It is a
runtime-observability tool, drift-proof by construction: it reads a live protocol, it does not generate
code.

## 2. What we build on (existing system)

The DevTools subsystem is a hub-and-outputs design:

- `devTools()` enhancer → process-global `DevToolsHub` → per-store `DevToolsSession` (bounded ring
  buffer of actions + lifted state + `events: SharedFlow<DevToolsEvent>`).
- Pluggable `DevToolsOutput`s: the in-app Compose drawer (`-devtools-inapp`) and `BridgeOutput`
  (`-devtools-bridge`).
- **Bridge wire protocol** (`-devtools-bridge`, `BridgeProtocol.kt`): app dials out over raw WebSocket
  to a Ktor server. `BridgeMessage` sealed type carries `Hello`/`HelloAck` (handshake, protocol
  version, optional token), `Init(state)`, and `Action { actionId, action, state, diff:
  List<DiffEntry>, timestampMillis, isExcess }`, plus pipeline structure/trace messages. Full state on
  the wire; leaf diffs computed app-side.
- **Standalone monitor** (`-devtools-standalone`): Compose Desktop + wasmJs GUI with an embedded Ktor
  server (`127.0.0.1:9090`, `/bridge`), launched via Gradle task / installer. Saves/loads recordings as
  versioned `.jsonl` (header + one JSON record per event).

Gaps this project fills: (a) no single CLI entry point bundling server + UI; (b) no agent-queryable log
API.

## 3. Approach (decision record)

- **A. New CLI module wrapping the existing server.** Reuses protocol/ingest/recording. Good, but
  leaves internal structure unspecified.
- **B. Add a headless entrypoint to `-devtools-standalone`.** No new module, but couples a fast,
  zero-GUI query path to a heavy Compose Multiplatform app — every query invocation drags Compose deps
  and slow startup. Rejected: poor fit for a binary an agent shells out to repeatedly.
- **C. (Chosen) New CLI module, internally three-layered.** As A, but split along clean seams so the
  query path stays lean and the GUI dep is isolated.

**Chosen: C.**

## 4. Architecture

New module **`redux-kotlin-devtools-cli`**, JVM-only, packaged as a runnable jar + native installer
(mirrors `-devtools-standalone`'s app packaging). Package `org.reduxkotlin.devtools.cli`. Three internal
layers, one-way dependencies:

```
cli/      subcommand parser (serve, stores, actions, diff, state, tail)
  ├──→ capture/   PURE query library — no server, no Compose
  │      CaptureReader (.jsonl → records) · CaptureQuery (filter) · CaptureFormatter (tiers)
  └──→ server/    CaptureServer: bridge ingest + Ktor WS, appends each event to the capture file
         └──→ :redux-kotlin-devtools-bridge        (reuse protocol + ingest)
  serve --ui ──→ :redux-kotlin-devtools-standalone (optional, lazy — the only path that pulls Compose)
```

- **`capture/`** depends only on the recording schema + kotlinx.serialization. It never imports the
  server or Ktor, so query subcommands start fast and are unit-testable as pure functions. It is the
  natural seam for a future MCP face.
- **`server/`** reuses the bridge ingest and Ktor server; its only added responsibility is teeing each
  ingested event to the append-only capture file.
- **`cli/`** is a thin subcommand parser binding the two; `serve --ui` lazily launches the standalone
  GUI against the same ingest.

Each unit answers cleanly: *what it does / how you use it / what it depends on* — and the Compose
dependency is confined to a single optional code path.

## 5. Capture format & lifecycle

- **`rk-devtools serve`** runs the daemon: hosts the bridge endpoint (`127.0.0.1:9090/bridge`, same
  defaults as the standalone monitor) and appends one record per ingested event to an append-only
  capture file. Foreground process; the agent backgrounds it.
- **Capture file reuses the existing standalone `.jsonl` recording format** (versioned header + one
  JSON record per event). Consequence: the agent's live capture is also a file the GUI can save/load —
  no new format, full interop in both directions.
- **Default path** `./.rk-devtools/<session>.jsonl` with a stable `latest.jsonl` pointer; `--out`
  overrides. Query subcommands default to `latest.jsonl`.
- **Multi-store / multi-client multiplex into one file**, each record tagged with its store key
  (`clientId + storeInstanceId`); queries filter by `--store`. App-side `isExcess` markers are preserved
  so the agent can see when the app's ring buffer evicted history.
- **Query subcommands are stateless file readers** — they tail the append-only file while `serve` runs;
  no IPC, no dependency on the daemon being reachable, only on the file existing.
- v1: one file per serve session, **no rotation** (the seam for `--max-bytes` truncation is noted, not
  built).

## 6. CLI surface

```
rk-devtools serve   [--port 9090] [--host 127.0.0.1] [--token T] [--out FILE] [--ui]
rk-devtools stores  [--out FILE]                      # list store keys seen in the capture
rk-devtools actions [filters] [--format ...]          # the read core
rk-devtools diff    [filters] [--format ...]          # convenience: --format diff
rk-devtools state   [--store K] [--at <actionId>]     # full snapshot, latest or after an action
rk-devtools tail    [filters] [--follow] [--format]   # recent / live-follow
```

**Shared filter flags** (compose freely): `--store <key>`, `--type <glob>` (e.g. `'*Card*'`),
`--since <id|ts>`, `--until <id|ts>`, `--last <N>`.

**Output:** JSON-lines by default (one object per line → pipes to `jq`/`grep`); `--pretty` for human
reading. Typical agent call: `rk-devtools diff --store account:ann --type '*Card*' --last 10`.

## 7. The `--format` output contract (concise-logging core)

Three nested tiers; the agent picks per call to control token cost. Each tier is a strict superset of
the one above:

| `--format` | Per-record payload | Answers | Cost |
|---|---|---|---|
| `actions` *(default)* | `{actionId, type, store, ts}` | "what fired, in what order" | minimal |
| `diff` | `+ diff: [{path, before, after}]` (the bridge's leaf diffs) | "what changed" | medium |
| `full` | `+ state: <full post-state JSON>` | "everything" | large |

`state --at <actionId>` is the point-read companion — a full snapshot after a specific action. Because
full post-state already ships on the wire per action, this is a read from the capture, not a replay.

Error/rejection actions are not special-cased in v1; the agent surfaces them via `--type '*Failed*'`
and similar. A dedicated triage/`digest` view is deferred (§10).

## 8. App opt-in & single entry point

The CLI is purely the receiver + query half and changes no app API. An app opts in with the existing
bridge output:

```kotlin
val store = createStore(reducer, initial, devTools(DevToolsConfig(name = "TaskFlow")))
DevToolsHub.session("TaskFlow")?.let {
    BridgeOutput(BridgeConfig(clientLabel = "TaskFlow", startEnabled = true)).start(it)  // → 127.0.0.1:9090
}
```

"Debugging by default" = integrators add that block in debug builds (release builds swap in
`-devtools-inapp-noop`); the recommended wiring is documented in the agent reference set. The **single
entry point** is `rk-devtools serve --ui`: one command stands up the bridge receiver, the append-only
capture, and the bundled GUI monitor — a human watches live while the agent queries the same stream.

## 9. Error handling & testing

- **Fail fast, legibly:** capture path unwritable or port in use → clear one-line error + nonzero exit.
  Protocol-version mismatch → the existing `HelloAck(accepted=false, reason=...)` path; a malformed
  frame is logged and skipped, never fatal.
- **Reader tolerance:** a query running against a file being appended skips a trailing partial line; an
  empty/missing capture yields an empty result + a stderr note, exit 0.
- **Testing (JVM):** `capture/` pure library against golden `.jsonl` fixtures (filtering + each format
  tier asserted); `server/` driven by a fake bridge client emitting `BridgeMessage`s, asserting capture
  file contents; CLI arg-parsing smoke tests. Mirrors repo test conventions (`kotlin-test`, source-set
  layout).

## 10. Explicitly deferred / out of scope

- `digest` / triage view (error surfacing, action counts, anomalies).
- Pipeline-timing (`PipelineTraced`) in query output.
- Dispatch-back / time-travel drive — keeps the read-only model the existing specs hold intact; would
  need a reverse channel + safety.
- An MCP face — the pure `capture/` library is the intended seam for it.
- Capture-file rotation / `--max-bytes` truncation.
- Auth hardening beyond the existing shared-token (e.g. TLS, per-client keys).

## 11. Open questions (resolve in planning)

1. **Arg-parsing library** — clikt vs kotlinx-cli vs hand-rolled; weigh dep weight against UX.
2. **`latest.jsonl` pointer mechanism** — symlink (portability on Windows?) vs a small pointer file.
3. **Store-key format** — confirm the exact `clientId + storeInstanceId` rendering used in `--store`
   filters matches what the GUI displays, for human/agent parity.
4. **Distribution** — published artifact, Gradle `run` task, and/or `installDist`; how an agent obtains
   the binary in a fresh checkout.
