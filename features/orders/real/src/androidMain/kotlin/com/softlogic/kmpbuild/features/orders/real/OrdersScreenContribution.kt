package com.softlogic.kmpbuild.features.orders.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.softlogic.kmpbuild.core.AndroidFeatureScreen
import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.FeatureScreenEntry
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * The Orders "entry composable" (the Metro DI pattern): [Content] creates the screen-scoped
 * [OrdersGraph] in `remember` and hands it to [FeatureScreenEntry], which provides a per-entry
 * ViewModel store + the graph's scoped `metroViewModelFactory` (so [OrdersScreen]'s `metroViewModel()`
 * resolves the OrdersScope-scoped [OrdersViewModel]) and cancels [OrdersSession] on exit.
 *
 * Registered into the app's `Set<AndroidFeatureScreen>` via `@ContributesIntoSet` — but only when
 * this `:real` module is compiled in (the store ships "orders"). The app graph injects
 * [OrdersGraph.Factory] (contributed to AppScope).
 */
@ContributesIntoSet(AppScope::class)
@Inject
class OrdersScreenContribution(
    private val graphFactory: OrdersGraph.Factory,
) : AndroidFeatureScreen {
    override val id: String = "orders"

    @Composable
    override fun Content(onBack: () -> Unit) {
        val graph = remember { graphFactory.createOrdersGraph() }
        FeatureScreenEntry(
            viewModelFactory = graph.metroViewModelFactory,
            onExit = { graph.session.close() },
        ) {
            OrdersScreen(onBack = onBack)
        }
    }
}
