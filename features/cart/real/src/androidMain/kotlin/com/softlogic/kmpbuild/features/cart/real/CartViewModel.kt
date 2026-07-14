package com.softlogic.kmpbuild.features.cart.real

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI state the Cart screen renders. */
data class CartUiState(
    val loading: Boolean = false,
    val items: List<String> = emptyList(),
)

/** Everything the Cart screen can do. */
sealed interface CartEvent {
    data object Refresh : CartEvent
}

/**
 * One presentation class per feature (per the chosen "presenter + view model = one class" shape):
 * holds `StateFlow<CartUiState>` and handles `CartEvent`s. Android-native (androidx.lifecycle).
 * Swap the stubbed data source for the real `:features:cart` logic when it exists.
 *
 * Metro-injected and scoped to [CartScope]: `@ContributesIntoMap(CartScope)` + `@ViewModelKey`
 * register it into the [CartGraph] extension's ViewModel map, so `metroViewModel()` in [CartScreen]
 * constructs a fresh instance per screen entry (via the entry composable).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(CartScope::class)
class CartViewModel : ViewModel() {

    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: CartEvent) {
        when (event) {
            CartEvent.Refresh -> load()
        }
    }

    private fun load() {
        _state.value = CartUiState(
            loading = false,
            items = listOf(
                "Milk — 2 × $1.50",
                "Bread — 1 × $2.00",
                "Eggs — 1 × $3.25",
            ),
        )
    }
}
