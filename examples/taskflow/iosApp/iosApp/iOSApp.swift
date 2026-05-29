import SwiftUI

/// SwiftUI entry point for the TaskFlow iOS host. The whole UI is rendered by
/// Compose Multiplatform via `ContentView` → `ComposeView`.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
