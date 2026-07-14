package com.softlogic.kmpbuild.features.orders.real

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI state the Orders screen renders. */
data class OrdersUiState(
    val loading: Boolean = false,
    val orders: List<String> = emptyList(),
)

/** Everything the Orders screen can do. */
sealed interface OrdersEvent {
    data object Refresh : OrdersEvent
}

/**
 * One presentation class per feature (per the chosen "presenter + view model = one class" shape):
 * holds `StateFlow<OrdersUiState>` and handles `OrdersEvent`s. Android-native (androidx.lifecycle).
 * Swap the stubbed data source for the real `:features:orders` logic when it exists.
 *
 * Metro-injected and scoped to [OrdersScope]: `@ContributesIntoMap(OrdersScope)` + `@ViewModelKey`
 * register it into the [OrdersGraph] extension's ViewModel map, so `metroViewModel()` in
 * [OrdersScreen] constructs a fresh instance per screen entry (via the entry composable).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(OrdersScope::class)
class OrdersViewModel : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: OrdersEvent) {
        when (event) {
            OrdersEvent.Refresh -> load()
        }
    }

    private fun load() {
        _state.value = OrdersUiState(
            loading = false,
            orders = listOf(
                "Order #1001 — 3 items",
                "Order #1002 — 1 item",
                "Order #1003 — 5 items",
            ),
        )
    }
}
