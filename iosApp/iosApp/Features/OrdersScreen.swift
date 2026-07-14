import SwiftUI

/// Native iOS (SwiftUI) screen for the Orders feature — the counterpart of the Android `OrdersScreen`.
/// Pushed by `ContentView`'s `NavigationStack`; the nav bar's back button pops it. The stubbed data
/// stands in for the real `:features:orders` logic until it exists.
struct OrdersScreen: View {
    @State private var orders: [String] = OrdersScreen.sampleOrders

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(orders, id: \.self) { Text("• \($0)") }

            Button("Refresh") { orders = OrdersScreen.sampleOrders }
                .buttonStyle(.bordered)

            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24)
        .navigationTitle("Orders")
    }

    private static let sampleOrders = [
        "Order #1001 — 3 items",
        "Order #1002 — 1 item",
        "Order #1003 — 5 items",
    ]
}

#Preview {
    NavigationStack { OrdersScreen() }
}
