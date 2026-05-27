import Foundation
import SwiftUI

/// Lightweight `ObservableObject` that wraps a single granular-subscription
/// field. Use as a `@StateObject` (owned) or `@ObservedObject` (passed in)
/// inside a SwiftUI view to bind the published `value` into the view tree.
///
/// The bridge is intentionally framework-agnostic: this class doesn't
/// import any Kotlin-generated type. The caller supplies a `subscribe`
/// closure that hooks into whatever Redux store implementation they're
/// using (typically `redux-kotlin-granular`'s `subscribeTo` overload on
/// `Store<S>` from the Kotlin framework) and returns an unsubscribe
/// callback. ``ReduxField`` invokes the unsubscribe in `deinit`.
///
/// Typical wiring against a `redux-kotlin-granular`-emitting framework:
///
/// ```swift
/// @StateObject var displayName = ReduxField<String>(
///     initial: store.state.user.displayName,
///     subscribe: { update in
///         let unsub = SubscribeKt.subscribeTo(
///             store,
///             selector: { stateAny in
///                 (stateAny as! AppState).user.displayName
///             },
///             triggerOnSubscribe: false,
///             listener: { _, newValue in
///                 update(newValue as! String)
///             }
///         )
///         return { _ = unsub() }
///     }
/// )
/// ```
@MainActor
public final class ReduxField<F>: ObservableObject {

    @Published public private(set) var value: F

    private var unsubscribeHandle: (() -> Void)?

    /// Builds a field binding.
    ///
    /// - Parameters:
    ///   - initial: Initial value, typically read off the store's
    ///     `state.field` synchronously before the subscription is installed.
    ///   - subscribe: Closure that installs the underlying subscription.
    ///     It receives a `(F) -> Void` update sink to call with every new
    ///     value (any thread); it must return an unsubscribe callback to
    ///     be invoked when this `ReduxField` is deinitialised. The update
    ///     sink trampolines onto the main actor before mutating `value`,
    ///     so SwiftUI observes mutations on the right thread.
    public init(
        initial: F,
        subscribe: (@escaping (F) -> Void) -> (() -> Void)
    ) {
        self.value = initial
        self.unsubscribeHandle = subscribe { [weak self] newValue in
            Task { @MainActor in
                self?.value = newValue
            }
        }
    }

    deinit {
        unsubscribeHandle?()
    }
}
