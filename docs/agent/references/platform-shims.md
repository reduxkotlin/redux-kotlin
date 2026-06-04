---
tier: T1
concern: platform-shims
derives_from:
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/DriverFactory.kt → DriverFactory, createDriver
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/DynamicColor.kt → dynamicColorScheme
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/HttpEngine.kt → ktorEngineOrNull
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/Ids.kt → newUuid
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/Notification.kt → mainNotificationContext
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/util/IdGenerator.kt → IdGenerator, DefaultIdGenerator
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/Ids.kt → OpId, BoardId, CardId, ColumnId
rules: [E, G]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 3c1cd67, date: 2026-06-04 }
---

# Platform shims (expect/actual)

> The five seams taskflow declares once in `commonMain/infra/platform/` and implements per target —
> what each one abstracts and how the app injects it.

## The discipline

Each shim is an `expect` declaration in
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/`
with one `actual` per target under `androidMain` / `iosMain` / `jvmMain` / `wasmJsMain`. Common code
depends on the `expect` only; the composition root injects the result. Keep the seam **small and
behind a domain-meaningful name** — `ktorEngineOrNull()`, not a leaked platform type — so common code
reads the same on every platform.

## The five shims

| Shim (commonMain expect) | Abstracts | android / ios / jvm / wasmJs backing |
|---|---|---|
| `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/DriverFactory.kt → DriverFactory` | the SQLDelight `SqlDriver` | Android `AndroidSqliteDriver` · iOS `NativeSqliteDriver` · JVM `JdbcSqliteDriver` · wasmJs `WebWorkerDriver` (async init) |
| `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/DynamicColor.kt → dynamicColorScheme` | Material You color | Android Material You on API 31+, else `null` · iOS/JVM/wasmJs `null` |
| `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/HttpEngine.kt → ktorEngineOrNull` | the Ktor client engine | Android `Android` · iOS `Darwin` · JVM `Java` · wasmJs `null` (browser fetch) |
| `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/Ids.kt → newUuid` | a fresh UUID string | Android/JVM `java.util.UUID` · iOS `NSUUID` · wasmJs `crypto.randomUUID()` |
| `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/Notification.kt → mainNotificationContext` | UI-thread marshalling | Android `Handler(mainLooper)` · iOS `dispatch_get_main_queue` · JVM Swing EDT · wasmJs inline |

## How each is wired

- **DriverFactory** → its `createDriver()` feeds the SQLDelight database that backs `SqlDelightLocalStore`
  (the durable side of [effects-sync.md](./effects-sync.md)). It is `suspend` so the async wasmJs Web
  Worker driver fits the same signature as the synchronous ones.
- **DynamicColor** → the theme calls `dynamicColorScheme(dark) ?: fallbackColors`; a `null` actual simply
  falls back to the bundled palette.
- **HttpEngine** → builds the Ktor client for image loading; a `null` actual lets the platform default
  apply. The same seam is where a real `RemoteApi` would get its client.
- **Notification** → `mainNotificationContext()` is the default notification context passed to both store
  factories ([store-setup.md](./store-setup.md)); it is what makes Rule E's off-main effects safe without
  an explicit main hop.

## Ids and Rule G

The id chain ties three files together. The platform `newUuid()` is wrapped by
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/util/IdGenerator.kt → IdGenerator, DefaultIdGenerator`
into typed factories (`newCardId()`, `newBoardId()`, …) that mint the value classes in
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/Ids.kt → OpId, BoardId, CardId, ColumnId`.
The UI reads the generator from a CompositionLocal and mints at the dispatch site (Rule G,
[compose-binding.md](./compose-binding.md)); a `FakeIdGenerator` injected in tests yields a deterministic
sequence. Android additionally needs an `AndroidContextHolder` set early (from the activity) because two
actuals require a `Context`.

## Verify loop

`./gradlew :examples:taskflow:composeApp:jvmTest` compiles and runs the jvm actuals; native actuals are
host-gated (iOS sim needs a Mac with the Xcode SDK — trust CI). Then `./gradlew detektAll`. A new shim
must keep `explicitApi()` happy on the `expect` (KDoc on the public declaration).

## Pitfalls

- Leaking a platform type through the `expect` signature forces common code to know the platform —
  return a common/abstract type or `null` instead.
- Forgetting one target's `actual` fails compilation only on that host; compile `allTests` or trust CI to
  catch native gaps.
- Generating ids in a reducer instead of via the injected generator breaks Rule G and determinism.

## See also

- [store-setup.md](./store-setup.md) — `mainNotificationContext` as the store's default context.
- [effects-sync.md](./effects-sync.md) — `DriverFactory`/`HttpEngine` behind the data layer.
- [compose-binding.md](./compose-binding.md) — the id generator injected for Rule G.
- [README](./README.md)
