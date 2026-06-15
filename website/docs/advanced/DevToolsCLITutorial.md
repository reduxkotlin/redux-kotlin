---
id: devtools-cli-tutorial
title: "How-to: Debug with the DevTools CLI"
sidebar_label: DevTools CLI (How-to)
---

# How-to: Debug a running app with `rk-devtools`

This walkthrough takes you end to end with the [DevTools CLI](./DevTools.md#the-rk-devtools-cli):
build the tool, point a running app at it, reproduce a bug, and pin down the
exact action and state change that caused it — all from the terminal, no
browser extension, no IDE debugger.

The CLI is **headless and scriptable**, which makes it the right tool for:

- debugging on devices/targets without a browser DevTools extension (iOS,
  Desktop, Wasm, CI),
- capturing a reproducible `.jsonl` trace to attach to a bug report,
- letting an AI agent or script answer "what fired before the crash?" — see
  the [agent walkthrough](https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/devtools.md).

:::caution Experimental
The DevTools modules are experimental and the CLI is an **unpublished**
developer tool — you build it from the repo, it is not a Maven artifact.
:::

> **Screenshots in this guide are placeholders.** Image slots reference
> `static/img/devtools-cli/*.png`; capture them against the
> [TaskFlow sample](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/taskflow)
> when filling this page in.

---

## The scenario

We use the **TaskFlow** Kanban sample. Bug to reproduce: dragging a card to a
new column shows a transient "Saving…" badge that **never clears** when the
fake backend rejects the write — the card looks stuck. We want the action that
fired and the state field that went wrong.

![TaskFlow board with a stuck "Saving…" card](/img/devtools-cli/00-stuck-card.png)

---

## Step 1 — Build the CLI

`rk-devtools` installs from the repo with Gradle's `installDist`:

```bash
./gradlew :redux-kotlin-devtools-cli:installDist

# resulting launcher:
redux-kotlin-devtools-cli/build/install/rk-devtools/bin/rk-devtools
```

Put it on your `PATH` for the session so the rest of the commands read cleanly:

```bash
export PATH="$PWD/redux-kotlin-devtools-cli/build/install/rk-devtools/bin:$PATH"
rk-devtools --help
```

```text
Usage: rk-devtools [<options>] <command> [<args>]...

  Receive a redux-kotlin app's event stream over the bridge and query the
  captured .jsonl logs.

Commands:
  serve     Host the bridge receiver and write per-store captures
  stores    List captured store keys
  actions   Print the action log
  diff      Print per-action field diffs
  state     Print the full state at an actionId
  tail      Print recent actions; --follow to poll live
```

![rk-devtools --help in a terminal](/img/devtools-cli/01-help.png)

---

## Step 2 — Start the receiver

`serve` hosts the bridge receiver on `127.0.0.1:9090` and writes one
`<storeKey>.jsonl` capture per store under `.rk-devtools/`. Leave it running in
its own terminal **before** you launch the app:

```bash
rk-devtools serve
```

```text
rk-devtools: listening on 127.0.0.1:9090
rk-devtools: writing captures to ./.rk-devtools/
rk-devtools: waiting for app… (Ctrl-C to stop)
```

Useful flags: `--port`, `--host`, `--out <dir>`, `--token <t>` (required for
non-loopback binds), and `--ui` to also launch the desktop GUI monitor against
the same ingest.

![serve waiting for a connection](/img/devtools-cli/02-serve-waiting.png)

---

## Step 3 — Point the app at the bridge

Add a `BridgeOutput` to the DevTools hub in **debug-only** code, with
`startEnabled = true` so it connects on launch:

```kotlin
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.bridge.BridgeConfig
import org.reduxkotlin.devtools.bridge.BridgeOutput

DevToolsHub.registerOutput(
    BridgeOutput(
        BridgeConfig(
            host = "127.0.0.1",
            port = 9090,
            startEnabled = true,
            clientLabel = "taskflow",
        ),
    ),
)
```

The store must already be wired with the `devTools(...)` enhancer — see
[Wiring the store](./DevTools.md#wiring-the-store). Launch the app; `serve`
prints the connection:

```text
rk-devtools: client connected — taskflow
rk-devtools: capturing store taskflow::board
```

---

## Step 4 — List the captured stores

Reproduce the bug in the app (drag the card so the backend rejects it), then
confirm the CLI is recording:

```bash
rk-devtools stores
```

```text
taskflow::board
taskflow::account
```

The `clientId::storeInstanceId` key is what you pass to `--store` when more
than one store is captured. With a single store, the query commands resolve it
automatically.

![stores listing two captured stores](/img/devtools-cli/03-stores.png)

---

## Step 5 — Scan the recent action log

```bash
rk-devtools actions --last 8
```

```text
  39  CardDragStarted          taskflow::board  12:04:51.310
  40  CardMoved                taskflow::board  12:04:51.""
  41  SyncRequested            taskflow::board  12:04:51.402
  42  SyncFailed               taskflow::board  12:04:52.118
  43  CardMoved                taskflow::board  12:04:52.""
```

There's the suspect: `SyncFailed` at action **42**. Filter to just the sync
traffic with a type glob:

```bash
rk-devtools actions --type '*Sync*' --last 5
```

![filtered action log showing SyncFailed](/img/devtools-cli/04-actions-filtered.png)

---

## Step 6 — Diff the offending action

`diff` shows the per-field JSON change each action produced — exactly what
`SyncFailed` did to the state:

```bash
rk-devtools diff --type 'SyncFailed' --last 1
```

```text
  42  SyncFailed  taskflow::board  12:04:52.118
    ~ cards.c7.saving            true  ->  true        # never cleared
    ~ cards.c7.syncError         null  ->  "rejected"
    + errors.lastSyncError       "card c7 rejected by backend"
```

The diff makes the bug obvious: the failure handler sets `syncError` but
**leaves `saving = true`** — so the badge never clears. The reducer should set
`saving = false` on `SyncFailed`.

![diff output highlighting saving stayed true](/img/devtools-cli/05-diff.png)

---

## Step 7 — Confirm against full state

Print the full state snapshot at that action to confirm the stuck field:

```bash
rk-devtools state --at 42 --pretty
```

```json
{
  "cards": {
    "c7": { "title": "Ship v1", "column": "done", "saving": true, "syncError": "rejected" }
  }
}
```

`saving: true` with a populated `syncError` — the smoking gun. Fix the
`SyncFailed` branch, rebuild, and re-run the trace to verify it now flips to
`false`.

---

## Watching live: `tail --follow`

While iterating, stream actions as they fire instead of re-running `actions`:

```bash
rk-devtools tail --follow
```

`--follow` polls the capture every 300ms and prints new actions. Combine with
`--type` to watch only what you care about:

```bash
rk-devtools tail --follow --type '*Sync*'
```

![tail --follow streaming actions](/img/devtools-cli/06-tail-follow.png)

---

## Other scenarios

| Goal | Command |
|---|---|
| Everything since a known-good action | `rk-devtools actions --since 40` |
| A bounded window | `rk-devtools actions --since 40 --until 50` |
| Only actions in a time window | `rk-devtools actions --since-time 2026-06-15T12:04:00Z` |
| Full state + diff for every action | `rk-devtools actions --format full --pretty` |
| One specific store when several are captured | `rk-devtools diff --store 'taskflow::board' --last 5` |
| Inspect captures with the GUI too | `rk-devtools serve --ui` |

---

## Captures are just files

Each store is a plain `<storeKey>.jsonl` under `.rk-devtools/` — commit one to a
bug report, diff two runs, or decode it programmatically with the bridge codec
(`decodeRecording` / `decodeRecordingLenient`). See the
[bridge module](https://github.com/reduxkotlin/redux-kotlin/tree/master/redux-kotlin-devtools-bridge).

## See also

- [DevTools reference](./DevTools.md) — full module map, store wiring, in-app
  drawer, remote streaming, standalone monitor, security notes.
- [CLI README](https://github.com/reduxkotlin/redux-kotlin/tree/master/redux-kotlin-devtools-cli) —
  command/flag table.
- [Agent walkthrough](https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/devtools.md) —
  the same loop driven by an AI agent.
