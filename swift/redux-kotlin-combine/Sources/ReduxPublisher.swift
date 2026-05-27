import Combine
import Foundation

/// Combine ``Publisher`` that emits granular-subscription updates from a
/// redux-kotlin store.
///
/// On first subscription:
///   1. The configured `initial` value is delivered immediately to the
///      downstream subscriber (mirrors `CurrentValueSubject`/`StateFlow`
///      semantics — UI bindings get a real value on frame zero).
///   2. The `subscribe` closure is invoked exactly once, installing the
///      underlying granular subscription that forwards every change into
///      the publisher chain.
///
/// On cancellation (subscriber dropped, `Cancellable` deallocated, etc.):
///   - The unsubscribe handle returned by `subscribe` is invoked.
///   - No further values are forwarded.
///
/// Like its sibling ``ReduxField`` in `redux-kotlin-swiftui`, this type
/// is intentionally framework-agnostic — it doesn't import any Kotlin-
/// generated symbol. The caller provides a `subscribe` closure that
/// hooks the publisher into whatever `redux-kotlin-granular` framework
/// is being consumed. Typical wiring:
///
/// ```swift
/// let displayNamePublisher = ReduxPublisher<String>(
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
///
/// let cancellable = displayNamePublisher
///     .receive(on: DispatchQueue.main)
///     .sink { name in
///         label.text = name
///     }
/// ```
///
/// The publisher is "hot" — backpressure (`Subscribers.Demand`) is not
/// honoured, because the upstream redux store has no concept of demand
/// and dropping values silently would violate the granular subscription
/// contract. Downstream operators that need flow control should compose
/// `.buffer`, `.throttle`, or `.collect` themselves.
public struct ReduxPublisher<F>: Publisher {

    public typealias Output = F
    public typealias Failure = Never

    private let initial: F
    private let subscribe: (@escaping (F) -> Void) -> (() -> Void)

    public init(
        initial: F,
        subscribe: @escaping (@escaping (F) -> Void) -> (() -> Void)
    ) {
        self.initial = initial
        self.subscribe = subscribe
    }

    public func receive<S>(subscriber: S) where S: Subscriber, S.Failure == Never, S.Input == F {
        let subscription = ReduxSubscription(
            initial: initial,
            subscribe: subscribe,
            downstream: AnySubscriber(subscriber)
        )
        subscriber.receive(subscription: subscription)
        subscription.start()
    }
}

private final class ReduxSubscription<F>: Subscription {

    private let lock = NSLock()
    private var downstream: AnySubscriber<F, Never>?
    private let initial: F
    private let subscribe: (@escaping (F) -> Void) -> (() -> Void)
    private var unsubscribe: (() -> Void)?

    init(
        initial: F,
        subscribe: @escaping (@escaping (F) -> Void) -> (() -> Void),
        downstream: AnySubscriber<F, Never>
    ) {
        self.initial = initial
        self.subscribe = subscribe
        self.downstream = downstream
    }

    /// Called after `subscriber.receive(subscription:)`. Delivers the
    /// initial value, then installs the underlying redux subscription.
    /// Splitting `init` from `start` keeps the `subscriber.receive
    /// (subscription:)` handshake completing before any value arrives,
    /// which is what most Combine subscribers expect.
    func start() {
        let downstreamRef: AnySubscriber<F, Never>?
        let unsub: (() -> Void)
        lock.lock()
        downstreamRef = downstream
        lock.unlock()

        guard let downstream = downstreamRef else { return }
        _ = downstream.receive(initial)

        // Install the underlying subscription. The forward closure
        // re-reads `downstream` under the lock on every emission so
        // cancellation correctly stops propagating values without
        // requiring a separate atomic flag.
        unsub = subscribe { [weak self] newValue in
            guard let self = self else { return }
            self.lock.lock()
            let active = self.downstream
            self.lock.unlock()
            _ = active?.receive(newValue)
        }

        // Stash the unsubscribe handle. If we were cancelled between
        // releasing the lock above and now, fire it immediately so we
        // don't leak a live subscription. Reads `self.downstream` (the
        // shared optional state), not the locally-shadowed `downstream`
        // which is the guard-let unwrap above.
        lock.lock()
        if self.downstream == nil {
            lock.unlock()
            unsub()
        } else {
            unsubscribe = unsub
            lock.unlock()
        }
    }

    func request(_ demand: Subscribers.Demand) {
        // Hot publisher; demand is ignored. See type-level docs.
    }

    func cancel() {
        lock.lock()
        let toFire = unsubscribe
        unsubscribe = nil
        downstream = nil
        lock.unlock()
        toFire?()
    }

    deinit {
        unsubscribe?()
    }
}

extension ReduxPublisher {
    /// Convenience: erase to `AnyPublisher<F, Never>` at the type-system
    /// boundary where API surface design typically wants the opaque
    /// publisher shape.
    public func eraseToAny() -> AnyPublisher<F, Never> { eraseToAnyPublisher() }
}
