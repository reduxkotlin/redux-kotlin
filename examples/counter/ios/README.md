# Counter — iOS sample

This sample validates the `redux-kotlin-granular` Swift call site
ergonomics. It demonstrates the canonical UI binding pattern
documented in adversarial-review items B4 (`@HiddenFromObjC` on the
property-reference overload) and B5 (`FieldSubscriptionScope`'s
generic erasure to `Any?` requiring an explicit downcast inside
Swift selectors).

No `.xcodeproj` is checked in — the goal is to show the working
Swift call site, not to maintain a brittle hand-generated
`project.pbxproj`. Drop the sources into any SwiftUI App template
to run.

**Deployment target: iOS 15+** (the `ContentView` uses
`.buttonStyle(.bordered)` / `.borderedProminent`, both 15.0+). The
granular library itself has no iOS-version floor of its own.

## What it shows

`CounterViewModel.swift` registers one `subscribeFields` block with
four entries — count, label, `isEven` (derived), `lastAction`
(change-only). All four `@Published` properties update behind a
single underlying `store.subscribe` listener, which is the same
"one underlying subscriber per screen" guarantee documented for the
Kotlin call site.

The `selector` closure inside each `scope.on(...)` call takes the
state as `Any?` (Kotlin generics on classes erase across the Native
ABI) and downcasts to `GranularCounterState`. This is the realistic
Swift call site — the plan's earlier draft incorrectly implied
`{ $0.count }` worked directly. The downcast is the v1 friction price
of Kotlin generics from Swift; a future `redux-kotlin-swift`
companion module could wrap `Store<S>` in a typed `final class` to
remove it.

## Build the framework

From the repo root:

```
./gradlew :examples:counter:common:linkReleaseFrameworkIosSimulatorArm64
```

Outputs `SharedCounter.framework` under:

```
examples/counter/common/build/bin/iosSimulatorArm64/releaseFramework/
```

Substitute `IosArm64` or `IosX64` for device / Intel-sim builds. Or
just run `assemble` to build all variants:

```
./gradlew :examples:counter:common:assemble
```

## Smoke compile the Swift sources

Standalone compile against the simulator framework (no Xcode project
required) — useful as a CI gate that the Swift API surface still
links:

```
xcrun -sdk iphonesimulator swiftc \
  -emit-module -emit-library \
  -target arm64-apple-ios15.0-simulator \
  -F examples/counter/common/build/bin/iosSimulatorArm64/releaseFramework \
  -framework SharedCounter \
  -o /tmp/CounterSample \
  examples/counter/ios/Sources/*.swift
```

A clean exit with no diagnostics means the Kotlin-exported headers
match the call site this sample documents.

## Plug into Xcode

1. **Create a new SwiftUI App project** in Xcode (File → New → Project
   → iOS → App → SwiftUI lifecycle). Bundle ID and signing are your
   choice — none required for the simulator.
2. **Drop the Swift files in:** copy `Sources/*.swift` into the new
   project's source group.
3. **Remove Xcode's stock `<ProjectName>App.swift` and `ContentView.swift`** —
   the sample's `CounterApp.swift` is the new `@main`.
4. **Embed the framework:**
   - In the project's target settings → "Frameworks, Libraries, and
     Embedded Content" → click `+` → "Add Other…" → "Add Files…"
   - Pick `SharedCounter.framework` from the Gradle output path above.
   - Set "Embed & Sign" so it's bundled into the app.
5. **Set Framework Search Paths** under Build Settings to the
   framework's parent directory.
6. Build & run. The counter increments / decrements drive Redux
   actions; the UI updates via the granular subscription bridge.

## What to look for when reviewing

- **Single `subscribeFields` block** — registers four field bindings,
  unsubscribes through a single returned handle in `deinit`.
- **`scope.on` lambda overload only** — no KProperty1 references
  appear in Swift autocomplete (hidden via `@HiddenFromObjC`).
- **`as!` downcast inside selector closures** — B5's call-site
  friction is right here, in line.
- **`triggerOnSubscribe: false` on the `lastAction` binding** —
  shows the change-only path (analytics-style logger).
- **`DispatchQueue.main.async` inside listeners** — the store's
  listener runs on whatever thread dispatched; SwiftUI mutates
  `@Published` from the main thread.

## Caveats / known limitations

- No Combine `Publisher` bridge yet (review I8). Each `@Published`
  is wired through a granular listener manually. Future
  `redux-kotlin-combine` would supply `PassthroughSubject` plumbing.
- No SwiftUI `View.subscribeFields { … }` `ViewModifier` (review I9).
  When that ships in `redux-kotlin-swiftui`, the view-model layer
  here would shrink to a single modifier call.
- Generics-erasure downcast: see B5 commentary above.
