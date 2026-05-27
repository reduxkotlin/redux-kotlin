# redux-kotlin-combine

Combine `Publisher` bridge for `redux-kotlin-granular` subscriptions.
Resolves adversarial-review item **I8** — the granular-subs v1 shipped
without a Combine `Publisher` story, leaving Swift/iOS consumers to
build `PassthroughSubject` plumbing by hand.

## What ships

`ReduxPublisher<F>`: a hot `Publisher` (`Output == F`, `Failure == Never`)
that wraps a single granular subscription. On first subscribe it emits
the configured initial value, then forwards every subsequent change
into the Combine chain. Cancellation tears down the upstream redux
subscription via the unsubscribe handle.

```swift
let displayName = ReduxPublisher<String>(
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

let cancellable = displayName
    .receive(on: DispatchQueue.main)
    .sink { name in
        label.text = name
    }
```

## Composing with other Combine operators

`ReduxPublisher` is a stock Combine publisher; everything in the
standard operator set works:

- `.receive(on: DispatchQueue.main)` — hop to main thread for UI work
- `.combineLatest(otherReduxPublisher)` — derive a value from two
  Redux fields
- `.removeDuplicates()` — extra paranoia (granular subs already diff
  via `===`+`==` upstream; only useful when the same value can arrive
  via concurrent dispatchers)
- `.throttle` / `.debounce` — flow control for rapidly-moving fields
- `.assign(to: \\.label, on: viewController)` — direct keypath
  assignment

## Hot vs cold

The bridge is intentionally hot: `Subscribers.Demand` is **not**
honoured. Redux dispatchers have no concept of demand and dropping a
value would silently violate the granular subscription contract.
Consumers that need flow control should compose `.buffer`, `.throttle`,
or `.collect` themselves.

## Installation

Source file only. No `Package.swift`, no `xcframework`. Drop
`Sources/ReduxPublisher.swift` into any iOS / macOS / watchOS / tvOS
target that imports `Combine`.

Distribution as an SPM package waits on `redux-kotlin` core
publishing a hosted xcframework artefact.

## Scope and limitations

- **Single field per publisher.** A `subscribeFields`-style batch
  (one underlying store.subscribe behind N field bindings) is
  available through `redux-kotlin-swiftui`'s `.subscribeReduxFields`
  modifier; for Combine, compose multiple `ReduxPublisher`s via
  `.combineLatest` or `.merge`.
- **Same generic-erasure constraint as the other Swift modules**:
  selectors take `Any?` and downcast to your concrete state type.
  Review B5.
- **No multi-model sugar.** A `publisher(forModel: ModelClass, …)`
  thin wrapper around the v2 `subscribeToModel` shim is straightforward
  but not in this PR — straightforward to add once a real consumer
  needs it.
