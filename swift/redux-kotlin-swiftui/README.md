# redux-kotlin-swiftui

SwiftUI helpers for binding a `redux-kotlin-granular`-style store into a
SwiftUI view tree without writing the lifecycle boilerplate by hand.

Two pieces, both framework-agnostic — they don't import any specific
Kotlin-generated type. Wire your own framework's symbol names in at the
call site.

## `ReduxField<F>` — one field, owned by a view

`@StateObject`-friendly wrapper around a single granular subscription.
`@Published var value: F` updates on every change; the underlying
subscription is torn down in `deinit`.

```swift
@StateObject var displayName = ReduxField<String>(
    initial: store.state.user.displayName,
    subscribe: { update in
        let unsub = SubscribeKt.subscribeTo(
            store,
            selector: { stateAny in
                (stateAny as! AppState).user.displayName
            },
            triggerOnSubscribe: false,
            listener: { _, newValue in
                update(newValue as! String)
            }
        )
        return { _ = unsub() }
    }
)

var body: some View {
    Text("Hello, \(displayName.value)")
}
```

The `update` sink trampolines onto `@MainActor` before mutating
`@Published value`, so SwiftUI observes the change on the right thread
regardless of which thread the store dispatched on.

## `.subscribeReduxFields { … }` — N fields tied to a view subtree

`ViewModifier` that installs a `subscribeFields` block at `onAppear`
and tears down the returned unsubscribe at `onDisappear`. Use this when
a single screen watches several fields and you want to keep them all
behind one underlying `store.subscribe`.

```swift
struct ProfileScreen: View {
    let store: TypedStore
    @State private var displayName: String = ""
    @State private var avatar: String?

    var body: some View {
        VStack {
            Text(displayName)
            AsyncImage(url: avatar.map(URL.init(string:)) ?? nil)
        }
        .subscribeReduxFields {
            SubscribeFieldsKt.subscribeFields(store) { scope in
                scope.on(
                    selector: { ($0 as! AppState).user.displayName },
                    triggerOnSubscribe: true,
                    listener: { _, new in
                        DispatchQueue.main.async {
                            self.displayName = new as! String
                        }
                    }
                )
                scope.on(
                    selector: { ($0 as! AppState).user.avatar },
                    triggerOnSubscribe: true,
                    listener: { _, new in
                        DispatchQueue.main.async {
                            self.avatar = new as? String
                        }
                    }
                )
            }
        }
    }
}
```

The `subscribe` closure is called once per `onAppear`. If the view
re-appears after disappearing, a new subscription is installed and a
new unsubscribe handle is stored. The internal handle is reference-
boxed so SwiftUI's `@State` diff doesn't churn it.

## Installation

This module ships as **source files only**, not as a Swift Package.
The two `.swift` files have no external dependencies beyond `SwiftUI`
and `Foundation`. Drop them into your iOS / macOS / watchOS target
and import where needed.

A proper SPM package + binary `SharedKotlin.xcframework` distribution
is on the roadmap once `redux-kotlin` core publishes a hosted
`xcframework` artefact.

## Scope and limitations

- **No automatic generics**: Swift sees the Kotlin `Store<S>` as
  `<Framework>TypedStore` with generic erased to `Any?`, so selectors
  always require `as! YourState` downcasts. This is a Kotlin/Native
  interop limitation (review B5), not specific to these helpers.
- **No `KProperty1` references from Swift**: the granular module's
  property-reference overloads are `@HiddenFromObjC` for that reason.
  Use the lambda-selector form from Swift.
- **No multi-model sugar yet**: a `subscribeReduxModelField` shim that
  consumes the `redux-kotlin-multimodel-granular` `subscribeToModel`
  shim is straightforward but deferred until a real consumer needs it.
- **Combine `Publisher`s**: see the sibling `redux-kotlin-combine`
  module for `AnyPublisher<F, Never>`-shaped bindings.
