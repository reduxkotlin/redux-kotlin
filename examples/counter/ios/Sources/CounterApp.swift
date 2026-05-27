import SwiftUI
import SharedCounter

/// SwiftUI entry point for the granular-subscriptions iOS demo.
///
/// The store is created once at app launch and held by the SceneDelegate-
/// equivalent root. ``CounterViewModel`` wires Redux state into SwiftUI's
/// `@Published` ergonomics via a single `subscribeFields` block — the
/// canonical pattern this sample is designed to validate.
@main
struct CounterApp: App {
    @StateObject private var viewModel = CounterViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
        }
    }
}
