import SwiftUI

/// `ViewModifier` that ties a Redux `subscribeFields`-style subscription
/// to a SwiftUI view's lifecycle. The subscription is installed at
/// `onAppear` and torn down at `onDisappear`, so the view is entirely
/// responsible for keeping the Redux side of the binding alive — there
/// is no manual `init`/`deinit` ceremony in the call site.
///
/// Companion to [ReduxField]. Where `ReduxField` owns one binding for the
/// lifetime of the parent `View`, this modifier owns N bindings for the
/// lifetime of the modified subtree — typically one
/// `subscribeFields { scope in scope.on(…); scope.on(…) }` block backed
/// by a single underlying `store.subscribe`.
///
/// Usage against a `redux-kotlin-granular`-emitting framework:
///
/// ```swift
/// struct ProfileScreen: View {
///     let store: TypedStore
///     @State private var displayName: String = ""
///     @State private var avatar: String?
///
///     var body: some View {
///         VStack {
///             Text(displayName)
///             AsyncImage(url: avatar.map(URL.init(string:)) ?? nil)
///         }
///         .subscribeReduxFields {
///             SubscribeFieldsKt.subscribeFields(store) { scope in
///                 scope.on(
///                     selector: { ($0 as! AppState).user.displayName },
///                     triggerOnSubscribe: true,
///                     listener: { _, new in
///                         DispatchQueue.main.async {
///                             self.displayName = new as! String
///                         }
///                     }
///                 )
///                 scope.on(
///                     selector: { ($0 as! AppState).user.avatar },
///                     triggerOnSubscribe: true,
///                     listener: { _, new in
///                         DispatchQueue.main.async {
///                             self.avatar = new as? String
///                         }
///                     }
///                 )
///             }
///         }
///     }
/// }
/// ```
public struct SubscribeReduxFieldsModifier: ViewModifier {

    private let subscribe: () -> (() -> Void)
    @State private var unsubscribe: UnsubscribeBox = UnsubscribeBox()

    public init(subscribe: @escaping () -> (() -> Void)) {
        self.subscribe = subscribe
    }

    public func body(content: Content) -> some View {
        content
            .onAppear {
                if unsubscribe.handle == nil {
                    unsubscribe.handle = subscribe()
                }
            }
            .onDisappear {
                unsubscribe.handle?()
                unsubscribe.handle = nil
            }
    }
}

/// Boxed-mutable holder so the modifier can replace the unsubscribe
/// handle without re-creating its `@State` cell. `@State` requires
/// `Equatable`-ish value semantics, but the handle is reference-typed
/// (a closure); boxing inside a `class` sidesteps the diff check.
private final class UnsubscribeBox {
    var handle: (() -> Void)?
}

extension View {
    /// Attaches a Redux `subscribeFields` subscription to this view's
    /// lifecycle. See [SubscribeReduxFieldsModifier].
    public func subscribeReduxFields(
        _ subscribe: @escaping () -> (() -> Void)
    ) -> some View {
        modifier(SubscribeReduxFieldsModifier(subscribe: subscribe))
    }
}
