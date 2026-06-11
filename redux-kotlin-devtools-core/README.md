# redux-kotlin-devtools-core

> **Experimental:** published and version-aligned by `redux-kotlin-bom`, but exempt
> from the semver guarantee until the devtools surface stabilizes — the API may
> change in minor releases.

The recording core of the Redux-Kotlin DevTools. Provides the `devTools(config)`
store enhancer that records actions, state snapshots, and JSON diffs into a
`DevToolsSession`; the process-global `DevToolsHub` where sessions and outputs
rendezvous; and the pipeline instrumentation combinators (`devToolsMiddleware`,
`devToolsCombineReducers`) that capture per-middleware / per-slice timing for
the Pipeline view. Always required in debug builds; release builds substitute
[`redux-kotlin-devtools-inapp-noop`](../redux-kotlin-devtools-inapp-noop).

## Dependency

```kotlin
debugImplementation("org.reduxkotlin:redux-kotlin-devtools-core:<version>")
```

## Quick start

```kotlin
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools

val cfg = DevToolsConfig(name = "appStore")
val store = createStore(reducer, AppState(), devTools(cfg))
```

Pass the **same** `DevToolsConfig` instance to `devTools`, `devToolsMiddleware`,
and `devToolsCombineReducers` — they key the shared session on
`instanceId ?: name`.

## Key entry points (`org.reduxkotlin.devtools`)

| Symbol | Role |
|---|---|
| `devTools(config)` | Store enhancer — records actions/state/diffs into a session |
| `devToolsMiddleware(config, vararg NamedMiddleware)` | Drop-in for `applyMiddleware` with per-middleware timing |
| `devToolsCombineReducers(config, vararg NamedReducer)` | Drop-in for `combineReducers` with per-slice timing |
| `named(label, …)` | Labels a middleware/reducer for the pipeline view |
| `DevToolsConfig` | `name`, `instanceId`, `maxAge`, allow/deny filters, `serializer`, `logger` |
| `DevToolsHub` | Process-global registry: `createSession`, `session(id)`, `registerOutput`, `sessionsFlow`, `outputsFlow` |
| `DevToolsSession` | The recorded feed: `events`, `history()`, `liftedState()`, `maxAge` |
| `KotlinxValueSerializer(json)` / `ToStringValueSerializer` | State/action serialization strategies |

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- Outputs: [`redux-kotlin-devtools-bridge`](../redux-kotlin-devtools-bridge),
  [`redux-kotlin-devtools-remote`](../redux-kotlin-devtools-remote)
- In-app drawer: [`redux-kotlin-devtools-inapp`](../redux-kotlin-devtools-inapp)
