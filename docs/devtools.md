# Redux-Kotlin DevTools integration guide

This guide covers how to add the Redux-Kotlin DevTools to an application,
how to wire up each artifact, and the sharp edges to avoid. The same guide is
published on the website at
[reduxkotlin.org/advanced/devtools](https://www.reduxkotlin.org/advanced/devtools);
the agent-oriented CLI walkthrough lives at
[docs/agent/references/devtools.md](agent/references/devtools.md).

> **Availability:** the DevTools modules are new and experimental — they are not
> part of any published release yet and will be available from the next release.
> Until then, build them from source (this repo).

---

## Artifacts overview

The DevTools ship as separate artifacts so release builds can link none of
the debug infrastructure. Published libraries: `-core`, `-bridge`, `-remote`,
`-inapp`, `-inapp-noop`, `-ui`. Unpublished repo tools: `-standalone` (the
desktop monitor app) and `-cli` (the `rk-devtools` terminal tool).

### `redux-kotlin-devtools-core`

Always required in debug builds. Provides the store enhancer, the process-global
session hub, and the pipeline instrumentation combinators.

Package: `org.reduxkotlin.devtools`

Key public API:

| Symbol | Role |
|---|---|
| `devTools(config)` | Store enhancer — records actions and state into a `DevToolsSession` |
| `devToolsMiddleware(config, vararg NamedMiddleware)` | Drop-in for `applyMiddleware`; captures timing and forwarding per middleware |
| `devToolsCombineReducers(config, vararg NamedReducer)` | Drop-in for `combineReducers`; captures per-slice timing and state changes |
| `named(label, middleware)` / `named(label, reducer)` | Labels a middleware or reducer for the pipeline view |
| `DevToolsConfig` | Recording options: name, filters, serializer, logger |
| `DevToolsHub` | Process-global registry; rendezvous point for enhancers and outputs |

### `redux-kotlin-devtools-remote`

Optional. Streams the session feed to an external Redux DevTools monitor (e.g.
the browser extension or `@redux-devtools/cli`) over WebSocket.

Package: `org.reduxkotlin.devtools.remote`

| Symbol | Role |
|---|---|
| `RemoteOutput(config, logger = …)` | `DevToolsOutput` implementation; off by default. Optional `logger: (String) -> Unit` surfaces connection diagnostics |
| `RemoteConfig` | Connection settings: `host`, `port`, `secure`, `startEnabled` |

Off by default — `RemoteConfig.startEnabled` defaults to `false`. Enable it
deliberately or via the in-app Outputs toggle.

### `redux-kotlin-devtools-inapp`

The in-app Compose Multiplatform DevTools drawer. Renders inside the app's own
Compose tree — no `SYSTEM_ALERT_WINDOW` or system overlay is used.

Package: `org.reduxkotlin.devtools.inapp`

| Symbol | Role |
|---|---|
| `ReduxDevToolsHost(config, content)` | Composable root wrapper; adds the overlay (bubble + edge-swipe trigger, drawer) |
| `ReduxDevTools.open()` / `ReduxDevTools.close()` | Programmatic drawer control |
| `InAppConfig` | Drawer options: `triggers`, `startTab`, `theme`, `instanceId` |
| `DevToolsTrigger` | `BUBBLE` / `EDGE_SWIPE` |
| `DevToolsThemeMode` | `DARK` (default) / `LIGHT` / `SYSTEM` — lives in `org.reduxkotlin.devtools.ui` |
| `DevToolsTab` | `ACTIONS` / `STATE` / `DIFF` / `PIPELINE` / `OUTPUTS` — lives in `org.reduxkotlin.devtools.ui` |

Not published for `linuxX64` or `mingwX64` (Compose material3 is not available
on those targets). Use the no-op on those targets.

### `redux-kotlin-devtools-inapp-noop`

The zero-overhead release sibling. Mirrors the `org.reduxkotlin.devtools.inapp`
API (`ReduxDevToolsHost`, `ReduxDevTools`, `InAppConfig`, the enum types) **and**
the core facade (`devTools`, `devToolsMiddleware`, `devToolsCombineReducers`,
`named`, `DevToolsConfig`, `KotlinxValueSerializer`, …) with empty bodies. Has
no dependency on Compose material3, Ktor, or core. Available on all targets,
including `linuxX64` and `mingwX64`. Only the mirrored API may be referenced
from main source sets — everything else stays in debug-only code.

### `redux-kotlin-devtools-ui`

The shared Compose UI panels (action log, State, Diff, Pipeline, Outputs) used
by both the in-app drawer and the standalone monitor. Arrives transitively via
`redux-kotlin-devtools-inapp`; apps touch it only for the
`org.reduxkotlin.devtools.ui` config enums `DevToolsTab` and `DevToolsThemeMode`.

Package: `org.reduxkotlin.devtools.ui`

---

## Android: debug/release variant wiring

Android Gradle build variants let you swap the real artifact for the no-op at
link time.

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
unresolved in a release build. The no-op provides the identical API so release
compiles and links nothing meaningful.

---

## KMP, iOS, and Desktop

`debugImplementation` / `releaseImplementation` are Android build-variant
features. For other Kotlin Multiplatform targets (iOS, Desktop, JS, Wasm) the
idiomatic swap is either:

- **Dependency substitution by build type** — configure Gradle to substitute the
  real artifact with the no-op when the `release` build type is active.
- **A compile flag** — guard the `devTools(cfg)` call with a constant
  (`BuildConfig.DEBUG` or a custom flag) and use the no-op everywhere else.

A Gradle plugin to auto-wire debug/release substitution across all KMP targets
is planned as future work.

---

## Integration

### 1. Create the config

```kotlin
import org.reduxkotlin.devtools.DevToolsConfig

val cfg = DevToolsConfig(name = "appStore")
```

Give each store a distinct `name` (or `instanceId`). See [footguns](#footguns).

### 2. Create the store

Pass the same `cfg` to all three combinators so they resolve the same session.

```kotlin
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.devtools.devToolsCombineReducers
import org.reduxkotlin.devtools.devToolsMiddleware
import org.reduxkotlin.devtools.named

val store = createStore(
    devToolsCombineReducers(cfg, named("todos", todosReducer), named("filter", filterReducer)),
    AppState(),
    compose(devTools(cfg), devToolsMiddleware(cfg, named("thunk", thunkMiddleware), named("logger", loggerMiddleware))),
)
```

`devToolsCombineReducers` is a drop-in for `combineReducers`; it folds
whole-state reducers left-to-right and records per-slice timing and state
changes. `devToolsMiddleware` is a drop-in for `applyMiddleware`; it times each
middleware and records whether it forwarded the action.

### 3. Wrap the app root

```kotlin
import org.reduxkotlin.devtools.inapp.ReduxDevToolsHost

@Composable
fun App() {
    ReduxDevToolsHost {
        // your app content
    }
}
```

By default `ReduxDevToolsHost` shows:

- A floating draggable bubble (tap to open).
- A right-edge swipe tab.

Both open the drawer. Tabs: **Actions**, **State**, **Diff**, **Pipeline**,
**Outputs**.

### 4. Programmatic control (optional)

```kotlin
import org.reduxkotlin.devtools.inapp.ReduxDevTools

ReduxDevTools.open()
ReduxDevTools.close()
```

### 5. Remote streaming (optional, debug only)

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

Use `10.0.2.2` from an Android emulator, or `localhost` with `adb reverse tcp:8000 tcp:8000`
from a physical device. The in-app Outputs tab can also toggle this at runtime.

### Customizing the drawer

```kotlin
import org.reduxkotlin.devtools.ui.DevToolsTab
import org.reduxkotlin.devtools.ui.DevToolsThemeMode
import org.reduxkotlin.devtools.inapp.DevToolsTrigger
import org.reduxkotlin.devtools.inapp.InAppConfig

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

---

## Footguns

### Colliding session ids

Give each store a distinct `DevToolsConfig.name` (or `instanceId`). Two stores
sharing an id resolve to the same `DevToolsSession` in the hub — their actions
interleave into one timeline. The hub logs a warning when this happens, but it
does not split them automatically.

```kotlin
// correct: distinct names
val cartConfig = DevToolsConfig(name = "cartStore")
val userConfig = DevToolsConfig(name = "userStore")

// wrong: both default to name = "redux-kotlin"
val cartConfig = DevToolsConfig()
val userConfig = DevToolsConfig()
```

### Mismatched config objects

Pass the **same** `DevToolsConfig` instance to `devTools`, `devToolsMiddleware`,
and `devToolsCombineReducers`. All three call `DevToolsHub.createSession(config)`,
which keys on `instanceId ?: name`. If you pass configs with different ids, the
middleware registers its pipeline structure against a different session than the
one the enhancer records into — pipeline capture is silently disabled. The store
still dispatches correctly; only the Pipeline tab in the drawer will be empty.

### Sensitive state in the drawer

All actions and state snapshots are visible in the DevTools drawer. If your state
contains tokens, credentials, or PII, provide a custom `ValueSerializer` to
redact them before they are serialized:

```kotlin
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.ValueSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

val cfg = DevToolsConfig(
    name = "appStore",
    serializer = object : ValueSerializer {
        override fun toJson(value: Any?): JsonElement =
            when (value) {
                is AppState -> JsonPrimitive("AppState(token=<redacted>, ...)")
                else -> JsonPrimitive(value?.toString() ?: "null")
            }
    },
)
```

### Remote streaming leaves the device

`RemoteOutput` opens a WebSocket to the configured host. It is off by default
(`RemoteConfig.startEnabled = false`). Enable it intentionally in debug builds
only — never ship it enabled.

### No system overlay

The in-app drawer renders inside the app's own Compose tree via
`ReduxDevToolsHost`. It does not request `SYSTEM_ALERT_WINDOW` and is not a
system overlay window. This means it is only visible while your app is in the
foreground, which is the intended behavior for a developer tool.

## Standalone monitor (desktop)

A separate Compose app — `redux-kotlin-devtools-standalone` — that monitors a
debugged app from *outside* its process, with desktop-class screen real estate:
all panels visible at once (action log, State, Diff, Pipeline), a multi-store
rail, a time-travel timeline, global search, and session save/load.

### Run it (desktop)

```
./gradlew :redux-kotlin-devtools-standalone:run
```

The monitor binds a WebSocket server on `ws://127.0.0.1:9090` (loopback) and
opens a window. Then point your app at it.

### Stream from your app (the bridge)

Add the bridge (debug-only) and register a `BridgeOutput` against your store's
session — it is off by default and connects to `127.0.0.1:9090`:

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-bridge:<version>")
```

```kotlin
val cfg = DevToolsConfig(name = "appStore")
val store = createStore(reducer, AppState(), devTools(cfg))
DevToolsHub.session(cfg.instanceId ?: cfg.name)?.let { session ->
    BridgeOutput(BridgeConfig(clientId = "myapp", clientLabel = "MyApp · desktop")).start(session)
}
```

Multiple stores stream as multiple sessions (one `BridgeOutput` per store,
sharing a `clientId`); the monitor groups them under one client and offers
per-store / "all stores" (merged-by-time) views with per-row store badges.
`BridgeConfig.storeName` sets an explicit display name for the monitor's
store rail.

### Reaches platforms the in-app drawer cannot

The in-app drawer needs Compose-material3, so it is unavailable on
`linuxX64`/`mingwX64` and headless/server-side Kotlin apps. The bridge needs
only a Ktor WebSocket client, so it compiles on every target `redux-kotlin`
supports — making the standalone monitor the **only** devtools option for
headless/native/server redux-kotlin apps. For structured state on iOS/native/JS,
register a `KotlinxValueSerializer(json)` as `DevToolsConfig.serializer`.

### Desktop-only

The monitor is a desktop app. An earlier `wasmJs` web viewer was removed (the
embedded server never served the compiled bundle); the desktop window is the
only monitor surface.

### Security

The monitor binds `127.0.0.1` and the bridge defaults to loopback. Streaming app
state off the loopback interface requires a non-loopback `host` **and** a shared
`token` (sent in the handshake, verified by the monitor against the connecting
peer). The bridge is debug-only — never ship it in a release build.

## The `rk-devtools` CLI

`redux-kotlin-devtools-cli` wraps the same bridge receiver in a terminal tool —
ideal for agents, scripts, and headless debugging. It is unpublished; install it
from the repository:

```
./gradlew :redux-kotlin-devtools-cli:installDist
# binary:
redux-kotlin-devtools-cli/build/install/rk-devtools/bin/rk-devtools
```

| Command | What it does |
|---|---|
| `rk-devtools serve` | Hosts the receiver on `127.0.0.1:9090`; writes one `<storeKey>.jsonl` capture per connected store into `.rk-devtools/`. Options: `--port`, `--host`, `--token`, `--out`, `--ui` (also launch the GUI monitor). |
| `rk-devtools stores` | Lists captured stores (`clientId::storeInstanceId`). |
| `rk-devtools actions` | Action log. Filters: `--store`, `--type '*Card*'`, `--since`/`--until`, `--last N`, `--format actions\|diff\|full`, `--pretty`. |
| `rk-devtools diff` | Same filters; each line includes the per-field JSON diff. |
| `rk-devtools state --at <id>` | Full state snapshot at an actionId. |
| `rk-devtools tail [--follow]` | Recent actions; `--follow` polls live. |

The agent-oriented walkthrough of the full debugging loop lives in
[docs/agent/references/devtools.md](agent/references/devtools.md).

## Recording codec (`.jsonl` captures)

The bridge module ships the capture codec, so any code can save, load, and
inspect recordings — the same format the CLI writes:

```kotlin
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.decodeRecording
import org.reduxkotlin.devtools.bridge.decodeRecordingLenient
import org.reduxkotlin.devtools.bridge.encodeRecording

val text: String = encodeRecording(header, messages)   // header line + one BridgeMessage per line
val (header, messages) = decodeRecording(text)         // strict — throws on a malformed line
val (header2, kept) = decodeRecordingLenient(text)     // lenient — skips malformed/unknown lines
```

`RecordingHeader` carries the protocol version, client id/label, and store
name/instance-id, so a capture is self-describing. Use the lenient decoder for
captures from a crashed app or a newer protocol version.
