package com.softlogic.kmpbuild.features.cart.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.softlogic.kmpbuild.core.AndroidFeatureScreen
import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.FeatureScreenEntry
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * The Cart "entry composable" (the Metro DI pattern): [Content] creates the screen-scoped [CartGraph]
 * in `remember` and hands it to [FeatureScreenEntry], which provides a per-entry ViewModel store +
 * the graph's scoped `metroViewModelFactory` (so [CartScreen]'s `metroViewModel()` resolves the
 * CartScope-scoped [CartViewModel]) and cancels [CartSession] on exit.
 *
 * Registered into the app's `Set<AndroidFeatureScreen>` via `@ContributesIntoSet` — but only when
 * this `:real` module is compiled in (the store ships "cart"). The app graph injects
 * [CartGraph.Factory] (contributed to AppScope).
 */
@ContributesIntoSet(AppScope::class)
@Inject
class CartScreenContribution(
    private val graphFactory: CartGraph.Factory,
) : AndroidFeatureScreen {
    override val id: String = "cart"

    @Composable
    override fun Content(onBack: () -> Unit) {
        val graph = remember { graphFactory.createCartGraph() }
        FeatureScreenEntry(
            viewModelFactory = graph.metroViewModelFactory,
            onExit = { graph.session.close() },
        ) {
            CartScreen(onBack = onBack)
        }
    }
}
