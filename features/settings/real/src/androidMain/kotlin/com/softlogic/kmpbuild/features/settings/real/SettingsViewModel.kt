package com.softlogic.kmpbuild.features.settings.real

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI state the Settings screen renders. */
data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val darkTheme: Boolean = false,
)

/** Everything the Settings screen can do. */
sealed interface SettingsEvent {
    data object ToggleNotifications : SettingsEvent
    data object ToggleDarkTheme : SettingsEvent
}

/**
 * One presentation class per feature (per the chosen "presenter + view model = one class" shape):
 * holds `StateFlow<SettingsUiState>` and handles `SettingsEvent`s. Android-native
 * (androidx.lifecycle). Swap the stubbed state for the real `:features:settings` logic when it exists.
 *
 * Metro-injected and scoped to [SettingsScope]: `@ContributesIntoMap(SettingsScope)` + `@ViewModelKey`
 * register it into the [SettingsGraph] extension's ViewModel map, so `metroViewModel()` in
 * [SettingsScreen] constructs a fresh instance per screen entry (via the entry composable).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(SettingsScope::class)
class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onEvent(event: SettingsEvent) {
        _state.value = when (event) {
            SettingsEvent.ToggleNotifications ->
                _state.value.copy(notificationsEnabled = !_state.value.notificationsEnabled)
            SettingsEvent.ToggleDarkTheme ->
                _state.value.copy(darkTheme = !_state.value.darkTheme)
        }
    }
}
