# redux-kotlin-devtools

Stream a redux-kotlin `Store` to the [Redux DevTools](https://github.com/reduxjs/redux-devtools)
Remote monitor. Monitoring MVP: action log + state inspector. Time travel is not yet supported.

## Setup

1. Run the monitor + server on your machine:

   ```bash
   npx @redux-devtools/cli --open      # SocketCluster server + UI on :8000
   ```

2. Add the dependency (debug-only recommended so it never ships in release):

   ```kotlin
   debugImplementation("org.reduxkotlin:redux-kotlin-devtools:<version>")
   ```

3. Wire the enhancer:

   ```kotlin
   val store = createStore(reducer, initialState, devTools(DevToolsConfig(name = "MyStore")))
   ```

   With middleware, compose the enhancers:

   ```kotlin
   import org.reduxkotlin.compose

   val store = createStore(
       reducer,
       initialState,
       compose(applyMiddleware(myMiddleware), devTools(DevToolsConfig(name = "MyStore"))) as StoreEnhancer<MyState>,
   )
   ```

## Android

The app's `localhost` is the device, not your machine.

| Scenario | `DevToolsConfig.host` | Extra step |
|----------|----------------------|------------|
| Emulator | `10.0.2.2` | none |
| Emulator or USB device | `localhost` | `adb reverse tcp:8000 tcp:8000` |
| Device over WiFi | your machine's LAN IP | server binds `0.0.0.0`; open firewall |

`ws://` is cleartext, which Android 9+ blocks by default. Add a **debug** network security
config or the connection silently hangs:

`src/debug/res/xml/network_security_config.xml`:
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```
Reference it from `src/debug/AndroidManifest.xml`:
```xml
<application android:networkSecurityConfig="@xml/network_security_config" />
```

## Serialization tiers

- **JVM / Android**: zero-config reflection → full structured inspector.
- **iOS / native / JS / Wasm**: `toString()` fallback → text log (no structured diff).
- **Opt-in everywhere**: pass a `DevToolsConfig.serializer` that emits structured JSON for rich
  inspection on every platform.

> The Android reflection tier assumes a debug build. Because the module is meant to be added via
> `debugImplementation`, R8/ProGuard (release-only) never runs against it — so reflection over your
> action/state classes is not stripped. Do not ship it in release builds.

## Not yet supported

Time travel, skip/commit/rollback/reset, import/export, remote action dispatch, the browser
extension transport. See the design spec for the parity matrix and roadmap.
