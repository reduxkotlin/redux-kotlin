# redux-kotlin-devtools-inapp-noop

The zero-overhead release sibling of the DevTools. It mirrors the
[`redux-kotlin-devtools-inapp`](../redux-kotlin-devtools-inapp) API **and** the
core facade (`devTools`, `devToolsMiddleware`, `devToolsCombineReducers`,
`named`, `DevToolsConfig`, `KotlinxValueSerializer`, …) with empty bodies, so
DevTools call sites in shared/main source sets compile in release builds while
linking no Compose material3, Ktor, or recording machinery. Available on all
targets, including `linuxX64` and `mingwX64`. An automated parity gate keeps
this facade aligned with the real modules.

## Debug/release substitution (Android)

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("org.reduxkotlin:redux-kotlin-devtools-core:<version>")
    debugImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp:<version>")

    releaseImplementation("org.reduxkotlin:redux-kotlin-devtools-inapp-noop:<version>")
}
```

For non-Android KMP targets, substitute by build type via Gradle dependency
substitution, or guard the `devTools(cfg)` call behind a compile flag.

## The contract

**Only the mirrored API may be referenced from main source sets.** Anything
outside it (e.g. `DevToolsHub`, `BridgeOutput`, `RemoteOutput`, session
queries) exists only in the real modules and must stay in debug-only source
sets/variants — otherwise the release build fails to link.

Mirrored surface: `ReduxDevToolsHost`, `ReduxDevTools`, `InAppConfig`,
`DevToolsTrigger` (in `org.reduxkotlin.devtools.inapp`), `DevToolsTab`,
`DevToolsThemeMode` (in `org.reduxkotlin.devtools.ui`), and the core facade in
`org.reduxkotlin.devtools` (`devTools`, `devToolsMiddleware`,
`devToolsCombineReducers`, `named`, `DevToolsConfig`, `ValueSerializer`,
`KotlinxValueSerializer`, `ToStringValueSerializer`).

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- The real drawer: [`redux-kotlin-devtools-inapp`](../redux-kotlin-devtools-inapp)
