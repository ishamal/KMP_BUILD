import SwiftUI

/// Native iOS (SwiftUI) screen for the Cart feature — the counterpart of the Android `CartScreen`.
/// Pushed by `ContentView`'s `NavigationStack`; the nav bar's back button pops it. The stubbed data
/// stands in for the real `:features:cart` logic until it exists.
struct CartScreen: View {
    @State private var items: [String] = CartScreen.sampleItems

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(items, id: \.self) { Text("• \($0)") }

            Button("Refresh") { items = CartScreen.sampleItems }
                .buttonStyle(.bordered)

            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24)
        .navigationTitle("Cart")
    }

    private static let sampleItems = [
        "Milk — 2 × $1.50",
        "Bread — 1 × $2.00",
        "Eggs — 1 × $3.25",
    ]
}

#Preview {
    NavigationStack { CartScreen() }
}
