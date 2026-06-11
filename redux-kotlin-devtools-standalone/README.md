# redux-kotlin-devtools-standalone

A Compose desktop monitor app that observes a debugged redux-kotlin app from
**outside** its process, with desktop-class screen real estate: all panels
visible at once (action log, State, Diff, Pipeline), a multi-store rail, a
time-travel timeline, global search, and session save/load. Because the app
side only needs the [bridge](../redux-kotlin-devtools-bridge) (a Ktor WebSocket
client), this is the devtools surface for headless, native, and server
redux-kotlin apps where the in-app drawer can't run.

**Unpublished.** This is a developer tool (`convention.control`), not a Maven
artifact — run it from the repository. Desktop-only (the earlier `wasmJs` web
viewer was removed).

## Run it

```
./gradlew :redux-kotlin-devtools-standalone:run
```

The monitor binds a WebSocket server on `ws://127.0.0.1:9090` (loopback) and
opens a window.

## Point your app at it

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-bridge:<version>")
```

```kotlin
DevToolsHub.session(cfg.instanceId ?: cfg.name)?.let { session ->
    BridgeOutput(BridgeConfig(clientId = "myapp", clientLabel = "MyApp")).start(session)
}
```

Multiple stores stream as multiple sessions (one `BridgeOutput` per store,
sharing a `clientId`); the monitor groups them under one client with
per-store / merged-by-time views.

Security: the monitor binds `127.0.0.1`. Non-loopback connections require a
shared `token` (sent in the bridge handshake, verified against the peer).

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- Terminal sibling on the same ingest layer: [`redux-kotlin-devtools-cli`](../redux-kotlin-devtools-cli)
