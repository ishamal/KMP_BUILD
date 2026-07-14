import SwiftUI

/// Native iOS (SwiftUI) screen for the Settings feature — the counterpart of the Android
/// `SettingsScreen`. Pushed by `ContentView`'s `NavigationStack`; the nav bar's back button pops it.
/// The local toggle state stands in for the real `:features:settings` logic until it exists.
struct SettingsScreen: View {
    @State private var notificationsEnabled = true
    @State private var darkTheme = false

    var body: some View {
        Form {
            Toggle("Notifications", isOn: $notificationsEnabled)
            Toggle("Dark theme", isOn: $darkTheme)
        }
        .navigationTitle("Settings")
    }
}

#Preview {
    NavigationStack { SettingsScreen() }
}
