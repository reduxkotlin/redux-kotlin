import SwiftUI

/// Counter UI driven entirely by the @Published bindings exposed by
/// [CounterViewModel]. Each Text view reads exactly the field it
/// displays, so when (for example) `lastAction` changes, the views that
/// render `count` / `label` / `isEven` are not re-evaluated unless
/// SwiftUI's diff also says they changed.
///
/// This mirrors the surgical-recomposition story documented for the
/// redux-kotlin-compose bridge, but in SwiftUI's view-tree shape.
struct ContentView: View {
    @ObservedObject var viewModel: CounterViewModel
    @State private var labelDraft: String = "Counter"

    var body: some View {
        VStack(spacing: 16) {
            Text(viewModel.label)
                .font(.title2)

            Text("\(viewModel.count)")
                .font(.system(size: 64, weight: .bold))
                .foregroundColor(viewModel.isEven ? .blue : .orange)

            Text(viewModel.isEven ? "even" : "odd")
                .font(.headline)
                .foregroundColor(.secondary)

            HStack(spacing: 12) {
                Button("-") { viewModel.decrement() }
                    .buttonStyle(.bordered)
                Button("+") { viewModel.increment() }
                    .buttonStyle(.borderedProminent)
                Button("+10") { viewModel.increment(by: 10) }
                    .buttonStyle(.bordered)
                Button("Reset") { viewModel.reset() }
                    .buttonStyle(.bordered)
            }
            .font(.title3)

            HStack {
                TextField("label", text: $labelDraft)
                    .textFieldStyle(.roundedBorder)
                Button("Set") { viewModel.setLabel(labelDraft) }
                    .buttonStyle(.bordered)
            }
            .padding(.horizontal)

            Text("last action: \(viewModel.lastAction)")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding()
    }
}
