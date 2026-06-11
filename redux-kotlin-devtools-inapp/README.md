# redux-kotlin-devtools-inapp

The in-app Redux-Kotlin DevTools drawer for Compose Multiplatform. Wrapping your
app root in `ReduxDevToolsHost` adds a floating bubble and a right-edge swipe
tab that open a drawer with **Actions**, **State**, **Diff**, **Pipeline**, and
**Outputs** tabs. The drawer renders inside the app's own Compose tree — no
`SYSTEM_ALERT_WINDOW`, no system overlay. Not available on `linuxX64`/`mingwX64`
(no Compose material3 there) — use the no-op on those targets and the
[standalone monitor](../redux-kotlin-devtools-standalone) to observe them.

## Dependency

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp:<version>")
releaseImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp-noop:<version>")
```

## Quick start

```kotlin
import org.reduxkotlin.devtools.inapp.ReduxDevToolsHost

@Composable
fun App() {
    ReduxDevToolsHost {
        // your app content
    }
}
```

Customizing (note `DevToolsTab` / `DevToolsThemeMode` import from
`org.reduxkotlin.devtools.ui`, the shared UI module):

```kotlin
import org.reduxkotlin.devtools.inapp.DevToolsTrigger
import org.reduxkotlin.devtools.inapp.InAppConfig
import org.reduxkotlin.devtools.ui.DevToolsTab
import org.reduxkotlin.devtools.ui.DevToolsThemeMode

ReduxDevToolsHost(
    InAppConfig(
        triggers = setOf(DevToolsTrigger.EDGE_SWIPE),
        startTab = DevToolsTab.STATE,
        theme = DevToolsThemeMode.SYSTEM,
        instanceId = "appStore", // pin to one store; omit for the multi-store picker
    )
) { /* … */ }
```

## Key entry points (`org.reduxkotlin.devtools.inapp`)

| Symbol | Role |
|---|---|
| `ReduxDevToolsHost(config, content)` | Composable root wrapper; adds the overlay |
| `ReduxDevTools.open()` / `.close()` | Programmatic drawer control |
| `InAppConfig` | `triggers`, `startTab`, `theme`, `instanceId` |
| `DevToolsTrigger` | `BUBBLE` / `EDGE_SWIPE` |

Output toggles in the Outputs tab are **hub-global** — they act on the outputs
registered with `DevToolsHub`, across all sessions.

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- Release substitution: [`redux-kotlin-devtools-inapp-noop`](../redux-kotlin-devtools-inapp-noop)
- Shared panels: [`redux-kotlin-devtools-ui`](../redux-kotlin-devtools-ui)
