import Foundation
import SwiftUI
import SharedCounter

/// SwiftUI bridge for the redux-kotlin store.
///
/// This view model is the proof-of-concept for the granular-subscriptions
/// Swift API: a single `subscribeFields` block batches four field
/// bindings behind one underlying `store.subscribe` listener, each
/// `@Published` property updates exactly when its source field moves.
///
/// What's exercised here:
///
/// - The **lambda-selector** overload of `scope.on(...)`. KProperty1
///   references are `@HiddenFromObjC` and can't appear from Swift
///   anyway — the Kotlin compiler exports the lambda-form overload as
///   `on(selector:triggerOnSubscribe:listener:)` and Swift consumers
///   use it directly.
///
/// - The explicit downcast required by `FieldSubscriptionScope<State>`
///   generic erasure on the Native target. The `state` argument arrives
///   as `Any?` and is cast back to `GranularCounterState` inside the
///   selector closure. Documented in adversarial-review item B5.
///
/// - A derived value (`isEven`) read by an in-block selector lambda
///   rather than a property reference — same path as the Kotlin
///   `selectorState`-style call site.
///
/// - The `ThreadSafeStore` (via `createThreadSafeStore`), because
///   subscriber invocation needs to be serial per dispatch for the
///   granular layer's `@Volatile var last` visibility contract to
///   hold across threads.
///
/// - The Reducer-typealias-from-Swift quirk: Kotlin
///   `granularCounterReducer` is typed `(GranularCounterState, Any) ->
///   GranularCounterState`, but `createThreadSafeStore` takes a generic
///   `(Any?, Any) -> Any?`. Swift closures aren't covariant in
///   parameter types, so we wrap the typed reducer in an Any-shaped
///   adapter closure right at the call site.
final class CounterViewModel: ObservableObject {

    @Published private(set) var count: Int32 = 0
    @Published private(set) var label: String = ""
    @Published private(set) var isEven: Bool = true
    @Published private(set) var lastAction: String = "<none>"

    private let store: any TypedStore
    private var unsubscribe: (() -> Void)?

    init() {
        let typedReducer = GranularCounterStateKt.granularCounterReducer
        let reducerAdapter: (Any?, Any) -> Any? = { state, action in
            typedReducer(state as! GranularCounterState, action)
        }
        store = CreateThreadSafeStoreKt.createThreadSafeStore(
            reducer: reducerAdapter,
            preloadedState: GranularCounterState(count: 0, label: "Counter", lastAction: "<none>"),
            enhancer: nil
        )

        // ONE underlying store.subscribe listener, FOUR Published
        // properties driven by it. triggerOnSubscribe: true for the
        // UI-binding fields so the first frame has real values;
        // triggerOnSubscribe: false on the analytics-style logger.
        unsubscribe = SubscribeFieldsKt.subscribeFields(
            store,
            onSelectorError: { error in
                NSLog("granular selector error: \(String(describing: error))")
            }
        ) { scope in
            // count — primitive Int32 (Kotlin Int) crosses the Any?
            // boundary as a boxed Integer; the listener receives it
            // as Any? and unboxes.
            scope.on(
                selector: { stateAny in
                    let state = stateAny as! GranularCounterState
                    return KotlinInt(value: state.count)
                },
                triggerOnSubscribe: true,
                listener: { [weak self] _, new in
                    guard let boxed = new as? KotlinInt else { return }
                    DispatchQueue.main.async { self?.count = boxed.int32Value }
                }
            )

            // label — String round-trips as NSString through Any?.
            scope.on(
                selector: { stateAny in (stateAny as! GranularCounterState).label },
                triggerOnSubscribe: true,
                listener: { [weak self] _, new in
                    guard let s = new as? String else { return }
                    DispatchQueue.main.async { self?.label = s }
                }
            )

            // isEven — derived from count, never written directly to
            // the model. selectorState-style demo.
            scope.on(
                selector: { stateAny in
                    KotlinBoolean(value: (stateAny as! GranularCounterState).isEven)
                },
                triggerOnSubscribe: true,
                listener: { [weak self] _, new in
                    guard let b = new as? KotlinBoolean else { return }
                    DispatchQueue.main.async { self?.isEven = b.boolValue }
                }
            )

            // lastAction — analytics logger pattern, change-only.
            scope.on(
                selector: { stateAny in (stateAny as! GranularCounterState).lastAction },
                triggerOnSubscribe: false,
                listener: { [weak self] _, new in
                    guard let s = new as? String else { return }
                    DispatchQueue.main.async { self?.lastAction = s }
                }
            )
        }
    }

    deinit {
        unsubscribe?()
    }

    func increment(by amount: Int = 1) {
        _ = store.dispatch(GranularIncrement(amount: Int32(amount)))
    }

    func decrement(by amount: Int = 1) {
        _ = store.dispatch(GranularDecrement(amount: Int32(amount)))
    }

    func setLabel(_ text: String) {
        _ = store.dispatch(GranularSetLabel(label: text))
    }

    func reset() {
        _ = store.dispatch(GranularReset())
    }
}
