import SwiftUI
import Shared

/// The home tile catalog, from the shared `FeatureId` enum (single source of ids + titles for both
/// platforms — no more duplicated string list here). `login` is the auth gate, not a tile, so
/// `homeTiles` already excludes it.
private let homeTiles: [FeatureId] = HomeFeatures.shared.homeTiles

struct ContentView: View {
    // The SwiftUI navigation back stack (the iOS counterpart of Android's Navigation 3 back stack).
    @State private var path: [FeatureRoute] = []

    // The features compiled into this store's Shared framework build, aggregated by the Metro
    // graph (HomeFeatures wraps createGraph<AppGraph>().features). A tile is enabled only if its
    // feature shipped in this store.
    private func isEnabled(_ id: FeatureId) -> Bool {
        HomeFeatures.shared.isEnabled(id: id)
    }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 12) {
                Text("Home (iOS)")
                    .font(.title)
                    .padding(.bottom, 8)

                ForEach(homeTiles, id: \.self) { tile in
                    let enabled = isEnabled(tile)
                    Button {
                        // A route only exists for a feature with a screen, and the tile is tappable
                        // only when the feature shipped — so an unshipped feature is never pushed.
                        if let route = FeatureRoute(featureId: tile) {
                            path.append(route)
                        }
                    } label: {
                        Text(enabled ? tile.title : "\(tile.title) — not in this store")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!enabled)
                }

                Spacer()
            }
            .padding(24)
            .navigationDestination(for: FeatureRoute.self) { route in
                switch route {
                case .cart: CartScreen()
                case .orders: OrdersScreen()
                case .settings: SettingsScreen()
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
