# redux-kotlin-devtools-ui

The shared Compose UI layer of the Redux-Kotlin DevTools: the panels (action
log, State, Diff, Pipeline, Outputs), view models, and theme used by **both**
the [in-app drawer](../redux-kotlin-devtools-inapp) and the
[standalone desktop monitor](../redux-kotlin-devtools-standalone). Most apps
never depend on it directly — it arrives transitively via
`redux-kotlin-devtools-inapp` — but two of its types appear in app code:
`DevToolsTab` and `DevToolsThemeMode`, which configure `InAppConfig`.

Package: `org.reduxkotlin.devtools.ui` (renamed from
`org.reduxkotlin.devtools.inapp` pre-1.0 — update imports of `DevToolsTab` /
`DevToolsThemeMode`).

## Dependency

Transitively included by `redux-kotlin-devtools-inapp`. Direct dependency is
only needed when building a custom DevTools surface:

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-ui:<version>")
```

## Key entry points (`org.reduxkotlin.devtools.ui`)

| Symbol | Role |
|---|---|
| `DevToolsTab` | `ACTIONS` / `STATE` / `DIFF` / `PIPELINE` / `OUTPUTS` — used by `InAppConfig.startTab` |
| `DevToolsThemeMode` | `DARK` / `LIGHT` / `SYSTEM` — used by `InAppConfig.theme` |
| `org.reduxkotlin.devtools.ui.model.*` | Panel view-state models (`InAppState`, `ActionLogRow`, `StoreRegistryState`, …) |
| `org.reduxkotlin.devtools.ui.theme.RkTokens` | The DevTools design tokens |

Not published for `linuxX64`/`mingwX64` (Compose material3 is unavailable
there).

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- Consumers: [`redux-kotlin-devtools-inapp`](../redux-kotlin-devtools-inapp),
  [`redux-kotlin-devtools-standalone`](../redux-kotlin-devtools-standalone)
