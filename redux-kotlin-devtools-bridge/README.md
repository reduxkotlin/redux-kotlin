# redux-kotlin-devtools-bridge

The app→monitor transport of the Redux-Kotlin DevTools. `BridgeOutput` streams a
`DevToolsSession`'s feed over WebSocket to the
[standalone monitor](../redux-kotlin-devtools-standalone) or the
[`rk-devtools` CLI](../redux-kotlin-devtools-cli). It needs only a Ktor
WebSocket client, so it compiles on every standard companion-module target —
including `linuxX64`/`mingwX64` and headless/server apps where the in-app
drawer can't run. The module also ships the `.jsonl` recording codec used for
capture files.

## Dependency

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-bridge:<version>")
```

Debug-only — never ship the bridge in a release build.

## Quick start

```kotlin
import org.reduxkotlin.devtools.bridge.BridgeConfig
import org.reduxkotlin.devtools.bridge.BridgeOutput

DevToolsHub.session(cfg.instanceId ?: cfg.name)?.let { session ->
    BridgeOutput(BridgeConfig(clientId = "myapp", clientLabel = "MyApp")).start(session)
}
```

One `BridgeOutput` per store; share a `clientId` so the monitor groups the
stores under one client.

## Key entry points (`org.reduxkotlin.devtools.bridge`)

| Symbol | Role |
|---|---|
| `BridgeOutput(config, logger = …)` | `DevToolsOutput` streaming a session to the monitor |
| `BridgeConfig` | `host` (default `127.0.0.1`), `port` (default 9090), `secure`, `startEnabled`, `token`, `clientId`, `clientLabel`, `storeName` |
| `BridgeMessage` | The wire protocol (`Hello`/`HelloAck`/`Init`/`Action`/…) |
| `RecordingHeader` + `encodeRecording` / `decodeRecording` / `decodeRecordingLenient` | The `.jsonl` capture codec — save, load, and inspect recordings (`decodeRecordingLenient` skips malformed lines) |

Security: the bridge defaults to loopback. A non-loopback `host` requires a
shared `token`, verified by the monitor.

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- Core: [`redux-kotlin-devtools-core`](../redux-kotlin-devtools-core)
