---
id: devtools
title: DevTools
sidebar_label: DevTools
---

# DevTools

The Redux-Kotlin DevTools give you action/state inspection, JSON diffing,
middleware-pipeline timing, and time-ordered multi-store views for a running
redux-kotlin app — in-app, on the desktop, from the terminal, or in the
classic Redux DevTools browser monitor.

:::caution Experimental

The DevTools modules are **experimental**. They are published alongside the
other modules and version-aligned by `redux-kotlin-bom`, but they are exempt
from the semantic-versioning guarantee until the devtools surface stabilizes —
the API may change in minor releases. Everything else under the BOM carries the
full stability promise.

:::

## Artifacts overview

| Artifact | Kind | Role |
|---|---|---|
| `redux-kotlin-devtools-core` | published library | Store enhancer (`devTools`), `DevToolsConfig`, the process-global `DevToolsHub`/`DevToolsSession`, pipeline instrumentation, JSON diffing. Always required in debug builds. |
| `redux-kotlin-devtools-bridge` | published library | `BridgeOutput` — streams a session to the standalone monitor / CLI over WebSocket; also the `.jsonl` recording codec. |
| `redux-kotlin-devtools-remote` | published library | `RemoteOutput` — streams to an external Redux DevTools monitor (browser extension / `@redux-devtools/cli`). |
| `redux-kotlin-devtools-inapp` | published library | `ReduxDevToolsHost` — the in-app Compose Multiplatform drawer. |
| `redux-kotlin-devtools-inapp-noop` | published library | Zero-overhead release sibling mirroring the inapp + core API for build-variant substitution. |
| `redux-kotlin-devtools-ui` | published library | Shared Compose UI panels (`DevToolsTab`, `DevToolsThemeMode`) used by the drawer and the standalone monitor. |
| `redux-kotlin-devtools-standalone` | unpublished tool | Compose desktop monitor app (run from the repo). |
| `redux-kotlin-devtools-cli` | library (behind `rk devtools`) | `devToolsCommand()` — the library backing `rk devtools`; the installable tool is `rk` from `:redux-kotlin-cli`. |

## Core entry points

Package: `org.reduxkotlin.devtools`

| Symbol | Role |
|---|---|
| `devTools(config)` | Store enhancer — records actions and state into a `DevToolsSession` |
| `devToolsMiddleware(config, vararg NamedMiddleware)` | Drop-in for `applyMiddleware`; captures timing and forwarding per middleware |
| `devToolsCombineReducers(config, vararg NamedReducer)` | Drop-in for `combineReducers`; captures per-slice timing and state changes |
| `named(label, middleware)` / `named(label, reducer)` | Labels a middleware or reducer for the pipeline view |
| `DevToolsConfig` | Recording options: `name`, `instanceId`, `maxAge`, allow/deny filters, `serializer`, `logger` |
| `DevToolsHub` | Process-global registry; rendezvous point for enhancers and outputs (`sessionsFlow`, `outputsFlow`) |
| `KotlinxValueSerializer(json)` | Structured state serialization via kotlinx.serialization (recommended off-JVM) |

## Android: debug/release variant wiring

Android Gradle build variants let you swap the real artifact for the no-op at
link time:

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("org.reduxkotlin:redux-kotlin-devtools-core:<version>")
    debugImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp:<version>")

    releaseImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp-noop:<version>")

    // optional remote streaming — debug only:
    // debugImplementation("org.reduxkotlin:redux-kotlin-devtools-remote:<version>")
}
```

`ReduxDevToolsHost` and `devTools()` are called from the shared/main source set,
so a plain `debugImplementation` for the real artifact would leave the call sites
unresolved in a release build. The no-op mirrors the identical API (including the
core facade — `devTools`, `devToolsMiddleware`, `devToolsCombineReducers`,
`KotlinxValueSerializer`) with empty bodies, so release compiles and links
nothing meaningful. **Only the mirrored API may be referenced from main source
sets** — anything else must stay in debug-only code.

## KMP, iOS, and Desktop

`debugImplementation` / `releaseImplementation` are Android build-variant
features. For other Kotlin Multiplatform targets (iOS, Desktop, JS, Wasm) the
idiomatic swap is either:

- **Dependency substitution by build type** — configure Gradle to substitute the
  real artifact with the no-op when the `release` build type is active.
- **A compile flag** — guard the `devTools(cfg)` call with a constant
  (`BuildConfig.DEBUG` or a custom flag) and use the no-op everywhere else.

The in-app drawer needs Compose material3, so it is unavailable on
`linuxX64`/`mingwX64` — use the no-op there, and use the
[standalone monitor](#standalone-monitor-desktop) to observe those targets from
outside the process.

## Wiring the store

Create one `DevToolsConfig` and pass the **same instance** to all three
combinators so they resolve the same session:

```kotlin
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.devtools.devToolsCombineReducers
import org.reduxkotlin.devtools.devToolsMiddleware
import org.reduxkotlin.devtools.named

val cfg = DevToolsConfig(name = "appStore")

val store = createStore(
    devToolsCombineReducers(cfg, named("todos", todosReducer), named("filter", filterReducer)),
    AppState(),
    compose(devTools(cfg), devToolsMiddleware(cfg, named("thunk", thunkMiddleware), named("logger", loggerMiddleware))),
)
```

`devToolsCombineReducers` is a drop-in for `combineReducers`; it folds
whole-state reducers left-to-right and records per-slice timing and state
changes. `devToolsMiddleware` is a drop-in for `applyMiddleware`; it times each
middleware and records whether it forwarded the action. If you only want the
action/state log, `devTools(cfg)` alone is enough.

Give each store a distinct `name` (or `instanceId`) — see
[footguns](#footguns).

## The in-app drawer

Wrap your app root:

```kotlin
import org.reduxkotlin.devtools.inapp.ReduxDevToolsHost

@Composable
fun App() {
    ReduxDevToolsHost {
        // your app content
    }
}
```

By default `ReduxDevToolsHost` shows a floating draggable bubble (tap to open)
and a right-edge swipe tab. Both open the drawer with tabs **Actions**,
**State**, **Diff**, **Pipeline**, **Outputs**. The drawer renders inside the
app's own Compose tree — no `SYSTEM_ALERT_WINDOW`, no system overlay.

Programmatic control:

```kotlin
import org.reduxkotlin.devtools.inapp.ReduxDevTools

ReduxDevTools.open()
ReduxDevTools.close()
```

Customizing — note that `DevToolsTab` and `DevToolsThemeMode` live in the
`org.reduxkotlin.devtools.ui` package (the shared UI module), while the
triggers and `InAppConfig` stay in `org.reduxkotlin.devtools.inapp`:

```kotlin
import org.reduxkotlin.devtools.inapp.DevToolsTrigger
import org.reduxkotlin.devtools.inapp.InAppConfig
import org.reduxkotlin.devtools.ui.DevToolsTab
import org.reduxkotlin.devtools.ui.DevToolsThemeMode

ReduxDevToolsHost(
    config = InAppConfig(
        triggers = setOf(DevToolsTrigger.EDGE_SWIPE), // disable bubble
        startTab = DevToolsTab.STATE,
        theme = DevToolsThemeMode.SYSTEM,
        instanceId = "appStore",                      // pin to one store if you have multiple
    )
) {
    // ...
}
```

The drawer's **Outputs** tab toggles outputs registered on the hub. Toggles are
**hub-global**: enabling the bridge output there enables it for every session it
serves, not just the store currently shown.

## Remote streaming (browser extension)

```kotlin
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.remote.RemoteConfig
import org.reduxkotlin.devtools.remote.RemoteOutput

val remote = RemoteOutput(RemoteConfig(host = "10.0.2.2", port = 8000))
DevToolsHub.registerOutput(remote)
// start manually, or set startEnabled = true in RemoteConfig:
val session = DevToolsHub.session(cfg.instanceId ?: cfg.name)
if (session != null) remote.start(session)
```

`RemoteOutput` is off by default (`RemoteConfig.startEnabled = false`). Use
`10.0.2.2` from an Android emulator, or `localhost` with
`adb reverse tcp:8000 tcp:8000` from a physical device. The in-app Outputs tab
can also toggle it at runtime. `RemoteOutput` takes an optional second `logger`
parameter (`(String) -> Unit`) for surfacing connection diagnostics.

## Standalone monitor (desktop)

A separate Compose desktop app — `redux-kotlin-devtools-standalone` — monitors
a debugged app from *outside* its process, with desktop-class screen real
estate: all panels visible at once (action log, State, Diff, Pipeline), a
multi-store rail, a time-travel timeline, global search, and session save/load.
It is an unpublished tool; run it from the repository:

```
./gradlew :redux-kotlin-devtools-standalone:run
```

The monitor binds a WebSocket server on `ws://127.0.0.1:9090` (loopback) and
opens a window. Then point your app at it via the bridge:

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-bridge:<version>")
```

```kotlin
import org.reduxkotlin.devtools.bridge.BridgeConfig
import org.reduxkotlin.devtools.bridge.BridgeOutput

val cfg = DevToolsConfig(name = "appStore")
val store = createStore(reducer, AppState(), devTools(cfg))
DevToolsHub.session(cfg.instanceId ?: cfg.name)?.let { session ->
    BridgeOutput(BridgeConfig(clientId = "myapp", clientLabel = "MyApp · desktop")).start(session)
}
```

Multiple stores stream as multiple sessions (one `BridgeOutput` per store,
sharing a `clientId`); the monitor groups them under one client and offers
per-store / "all stores" (merged-by-time) views. `BridgeConfig.storeName` sets
an explicit display name for the monitor's store rail.

The bridge needs only a Ktor WebSocket client, so it compiles on every standard
companion-module target — making the standalone monitor the **only** devtools
option for headless/native/server redux-kotlin apps. For structured state on
iOS/native/JS, register a `KotlinxValueSerializer(json)` as
`DevToolsConfig.serializer`.

## The `rk` CLI

`redux-kotlin-cli` bundles `rk devtools` (bridge receiver + capture queries) and
`rk snapshot` (headless renderer) in a single terminal tool — ideal for agents,
scripts, and headless debugging. It is unpublished; install it from the
repository:

```
./gradlew :redux-kotlin-cli:installDist
# binary lands at:
redux-kotlin-cli/build/install/rk/bin/rk
```

(Add that `bin/` directory to your `PATH`, or symlink the binary.)

### Subcommands

| Command | What it does |
|---|---|
| `rk devtools serve` | Hosts the bridge receiver on `127.0.0.1:9090` and writes one `<storeKey>.jsonl` capture per connected store into `.rk-devtools/`. Options: `--port`, `--host`, `--token`, `--out`, `--ui` (also launch the GUI monitor). |
| `rk devtools stores` | Lists captured stores (`clientId::storeInstanceId` keys). |
| `rk devtools actions` | Prints the action log. Filters: `--store`, `--type '*Card*'`, `--since`/`--until`, `--last N`, `--format actions\|diff\|full`, `--pretty`. |
| `rk devtools diff` | Same filters; each line includes the per-field JSON-diff for the action. |
| `rk devtools state --at <id>` | Full state snapshot recorded at an actionId. |
| `rk devtools tail [--follow]` | Recent actions; `--follow` polls for new ones live. |

Typical loop: `rk devtools serve` in a background terminal → run the app → `rk devtools stores` →
`rk devtools actions --last 30` → `rk devtools diff --type '*Failed*' --last 5` → `rk devtools state --at <id>`.

## Recording codec (`.jsonl` captures)

The bridge module ships the capture codec, so any JVM/KMP code can save, load,
and inspect recordings — the same format the CLI writes to `.rk-devtools/`:

```kotlin
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.decodeRecording
import org.reduxkotlin.devtools.bridge.decodeRecordingLenient
import org.reduxkotlin.devtools.bridge.encodeRecording

// save: header line + one BridgeMessage per line
val text: String = encodeRecording(header, messages)

// load (strict — throws on a malformed line):
val (header, messages) = decodeRecording(text)

// load (lenient — skips malformed/unknown lines; use for captures from a
// crashed app or a newer protocol version):
val (header2, kept) = decodeRecordingLenient(text)
```

`RecordingHeader` carries the protocol version, client id/label, and store
name/instance-id, so a capture is self-describing.

## Security notes

- The standalone monitor and the CLI bind `127.0.0.1`, and the bridge defaults
  to loopback. Streaming app state off the loopback interface requires a
  non-loopback `host` **and** a shared `token` (sent in the handshake, verified
  by the monitor against the connecting peer).
- All recorded actions and state snapshots are visible in every monitor
  surface. If your state contains tokens, credentials, or PII, provide a custom
  `ValueSerializer` in `DevToolsConfig` to redact them before serialization.
- The bridge and remote outputs are debug-only — never ship them enabled in a
  release build. The no-op substitution above is the guard rail.

## Footguns

### Colliding session ids

Give each store a distinct `DevToolsConfig.name` (or `instanceId`). Two stores
sharing an id resolve to the same `DevToolsSession` in the hub — their actions
interleave into one timeline. The hub logs a warning, but does not split them.

### Mismatched config objects

Pass the **same** `DevToolsConfig` instance to `devTools`, `devToolsMiddleware`,
and `devToolsCombineReducers`. All three call `DevToolsHub.createSession(config)`,
which keys on `instanceId ?: name`. If you pass configs with different ids, the
pipeline structure registers against a different session than the one the
enhancer records into — the Pipeline tab stays silently empty (dispatch still
works).

### The drawer only shows while your app is foregrounded

The in-app drawer is part of your app's Compose tree, not a system overlay.
Use the standalone monitor or the CLI to keep observing across app restarts.

## See also

- [Compose integration](compose-integration) — binding store state to Compose.
- [Granular subscriptions](granular-subscriptions) — the subscription layer the
  Compose bindings build on.
- Repo guide: [docs/devtools.md](https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/devtools.md).
