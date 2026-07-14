import Foundation

/// Typed SwiftUI navigation routes — the iOS counterpart of Android's `AppKey`. One case per feature
/// screen the app can push onto the `NavigationStack`. `login` is intentionally absent: it's the auth
/// gate, not a home tile.
///
/// This project is deliberately native-per-platform, so iOS navigates with SwiftUI's `NavigationStack`
/// rather than sharing Android's Jetpack Navigation 3 host.
enum FeatureRoute: Hashable {
    case cart
    case orders
    case settings

    /// Maps a home tile's feature id (aggregated from the Metro graph via `HomeFeatures`) to its route,
    /// or `nil` if the id has no screen.
    init?(featureId: String) {
        switch featureId {
        case "cart": self = .cart
        case "orders": self = .orders
        case "settings": self = .settings
        default: return nil
        }
    }
}
