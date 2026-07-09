import SwiftUI
import Shared

/// A home nav tile. `login` is the auth gate, not a tile.
private struct HomeItem: Identifiable {
    let id: String
    let title: String
}

private let homeItems: [HomeItem] = [
    HomeItem(id: "cart", title: "Cart"),
    HomeItem(id: "settings", title: "Settings"),
    HomeItem(id: "orders", title: "Orders"),
]

struct ContentView: View {
    @State private var opened: String? = nil

    // The features compiled into this store's Shared framework build (StoreInfo is generated
    // per -Pstore in shared/build.gradle.kts). A tile is enabled only if its feature shipped.
    private func isEnabled(_ id: String) -> Bool {
        StoreInfo.shared.isEnabled(feature: id)
    }

    var body: some View {
        VStack(spacing: 12) {
            Text("Home (iOS)")
                .font(.title)
                .padding(.bottom, 8)

            ForEach(homeItems) { item in
                let enabled = isEnabled(item.id)
                Button {
                    opened = item.id
                } label: {
                    Text(enabled ? item.title : "\(item.title) — not in this store")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!enabled)
            }

            if let opened {
                Text("Opened \"\(opened)\"")
                    .padding(.top, 8)
            }

            Spacer()
        }
        .padding(24)
    }
}

#Preview {
    ContentView()
}
