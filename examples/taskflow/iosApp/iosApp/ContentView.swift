import UIKit
import SwiftUI
import TaskFlowApp

/// Wraps the Kotlin/Compose `MainViewController` (exported by the
/// `TaskFlowApp` framework) as a SwiftUI-embeddable view controller.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Root SwiftUI view. Compose draws edge-to-edge, so the host view ignores
/// the safe area and lets Compose handle insets.
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
