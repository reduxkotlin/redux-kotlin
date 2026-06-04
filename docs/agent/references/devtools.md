---
tier: T1
concern: devtools
derives_from:
  - redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/BridgeOutput.kt → BridgeOutput
  - redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/BridgeConfig.kt → BridgeConfig
  - redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/command/ServeCommand.kt → ServeCommand
  - redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureFormatter.kt → Format
  - redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/command/QueryOptions.kt → QueryOptions
  - redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureStore.kt → StoreRef
  - redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/command/RootCommand.kt → RootCommand
api_files: []
rules: []
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 300b1fbd, date: 2026-06-04 }
---

# DevTools debugging loop

> How an agent observes a running redux-kotlin app's actions, state, and diffs to close a
> write→run→observe→fix loop instead of debugging blind.

## What it is

The DevTools CLI (`rk-devtools`) gives agents and developers a read-only window into a live or
recently-run redux-kotlin app. The app emits a structured event stream over a local bridge; the CLI
receives it, writes per-store JSONL captures, and exposes query subcommands that agents can call at
any point in a debugging session to answer questions like "what actions fired just before the crash?"
or "which field changed between actions 40 and 50?"

Without DevTools the loop is: guess → edit → run → read log. With DevTools:
write → run → `rk-devtools diff --last 10` → targeted fix.

## App opt-in (debug builds only)

Wire the bridge in the app's debug variant. Production builds swap in `:redux-kotlin-devtools-inapp-noop`
which compiles to no-ops; never ship the bridge in release.

```kotlin
// In debug DI / app init (debug source set)
val store = createStore(reducer, initial, devTools(DevToolsConfig(name = "TaskFlow")))
DevToolsHub.session("TaskFlow")?.let {
    BridgeOutput(BridgeConfig(clientLabel = "TaskFlow", startEnabled = true)).start(it)
}
```

Key types:

- `redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/BridgeOutput.kt → BridgeOutput`
  — streams a store's `DevToolsSession` feed to the CLI server. One instance per store.
  The explicit `BridgeOutput(config).start(session)` call connects the output to the running monitor.
  `startEnabled` is a separate hint that an automated binder consults to decide whether to auto-start
  the output at registration; with the explicit `start(...)` above it is moot but harmless to set.
- `redux-kotlin-devtools-bridge/src/commonMain/kotlin/org/reduxkotlin/devtools/bridge/BridgeConfig.kt → BridgeConfig`
  — connection settings: `host` (default `127.0.0.1`), `port` (default 9090), `clientLabel`
  (human display name), optional `token` (required by the server for non-loopback connections).

The bridge is localhost-bound by default. For a device-over-USB session set `host` to the forwarded
address and supply a matching `--token` on both sides.

## The CLI debugging loop

### Step 1 — start the receiver (`serve`)

```
rk-devtools serve
rk-devtools serve --ui          # also open the GUI monitor window
rk-devtools serve --port 9091   # non-default port
```

`rk-devtools serve` (`redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/command/ServeCommand.kt → ServeCommand`)
binds the bridge WebSocket receiver on `127.0.0.1:9090` (configurable) and writes one
`<storeKey>.jsonl` capture file per connected store into `.rk-devtools/` in the working directory.
Run it in a background terminal before launching the app; it persists captures across app restarts.
`--ui` launches the Compose desktop GUI monitor on top of the same ingest layer.

### Step 2 — discover connected stores (`stores`)

```
rk-devtools stores
```

Lists every store key present in `.rk-devtools/` with its human name. A key has the form
`clientId::storeInstanceId` (e.g., `TaskFlow::root`). Pass it to `--store` in subsequent commands
when more than one store is captured.

Store discovery reads the JSONL header via
`redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureStore.kt → StoreRef`.

### Step 3 — query the capture

All query subcommands share a common filter/format layer
(`redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/command/QueryOptions.kt → QueryOptions`):

| Flag | Meaning |
|---|---|
| `--store <key>` | target store (`clientId::storeInstanceId`); auto-resolved if only one |
| `--type '*Card*'` | glob filter on action type (`*` = any substring) |
| `--since <id>` / `--until <id>` | inclusive actionId range |
| `--last <N>` | keep only the final N results after other filters |
| `--format actions\|diff\|full` | output tier (see below) |
| `--pretty` | pretty-print JSON |

Output tiers
(`redux-kotlin-devtools-cli/src/main/kotlin/org/reduxkotlin/devtools/cli/capture/CaptureFormatter.kt → Format`):

- `actions` — actionId, type, store, timestamp. Lightweight; the default.
- `diff` — adds the JSON-diff array for each action.
- `full` — also includes the full state snapshot after each action.

#### `actions` — action log

```
rk-devtools actions --last 20
rk-devtools actions --store taskflow::root --type '*Card*' --last 10
```

Prints the action stream (newest-last) at the `actions` tier. Good for "what happened?"

#### `diff` — state changes

```
rk-devtools diff --store taskflow::root --type '*Card*' --last 10
rk-devtools diff --last 5
```

Same filter options; defaults to the `diff` tier so each line includes the per-field change set.
Good for "what changed and when?"

#### `state` — snapshot at a point in time

```
rk-devtools state --at 42
```

Prints the full state snapshot recorded at actionId 42 (one JSON object). Good for "what was the
store at the moment of the crash?"

#### `tail` — live follow

```
rk-devtools tail --follow
rk-devtools tail --follow --type '*Error*'
```

Prints current captures then polls for new actions. `--follow` keeps polling (300 ms interval) until
interrupted; without it, `tail` is a one-shot snapshot of recent actions. Good for watching a
session live without the GUI.

## Format tiers in detail

Each tier is a strict superset of the previous:

```
actions → { actionId, type, store, ts }
diff    → + diff: [ { path, before, after } … ]
full    → + state: { … full store snapshot … }
```

Use `actions` for agent filtering (low token cost), `diff` for targeted field investigation, `full`
only when you need to reconstruct complete store state at a single point.

## Typical agent workflow

1. `rk-devtools serve` — start receiver in background before test run.
2. Run/reproduce the scenario in the app.
3. `rk-devtools stores` — confirm the store connected.
4. `rk-devtools actions --last 30` — get the tail of the action log.
5. `rk-devtools diff --type '*FailedAction*' --last 5` — isolate the diff around the failure.
6. `rk-devtools state --at <id>` — inspect full state at the suspect action.
7. Apply targeted fix; re-run from step 2 to confirm the diff changes as expected.

## Design reference

Full protocol, wire format, server internals, and module boundaries:
`docs/superpowers/specs/2026-06-04-redux-kotlin-devtools-cli-design.md`

## See also

- [README](./README.md)
