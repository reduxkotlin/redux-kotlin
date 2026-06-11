# redux-kotlin-devtools-remote

Streams a `DevToolsSession` feed to an external Redux DevTools monitor — the
browser extension or `@redux-devtools/cli` — over WebSocket, so you can inspect
a redux-kotlin app in the same monitor UI the JS ecosystem uses.

## Dependency

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-remote:<version>")
```

Debug-only — never ship remote streaming in a release build.

## Quick start

```kotlin
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.remote.RemoteConfig
import org.reduxkotlin.devtools.remote.RemoteOutput

val remote = RemoteOutput(RemoteConfig(host = "10.0.2.2", port = 8000))
DevToolsHub.registerOutput(remote)
DevToolsHub.session(cfg.instanceId ?: cfg.name)?.let { remote.start(it) }
```

Off by default (`RemoteConfig.startEnabled = false`) — enable deliberately, or
toggle at runtime from the in-app drawer's Outputs tab. Use `10.0.2.2` from an
Android emulator, or `localhost` with `adb reverse tcp:8000 tcp:8000` from a
physical device.

## Key entry points (`org.reduxkotlin.devtools.remote`)

| Symbol | Role |
|---|---|
| `RemoteOutput(config, logger = …)` | `DevToolsOutput` implementation; the optional `logger: (String) -> Unit` surfaces connection diagnostics |
| `RemoteConfig` | `host`, `port`, `secure`, `startEnabled` |

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- Core: [`redux-kotlin-devtools-core`](../redux-kotlin-devtools-core)
- Local monitor alternative: [`redux-kotlin-devtools-bridge`](../redux-kotlin-devtools-bridge)
