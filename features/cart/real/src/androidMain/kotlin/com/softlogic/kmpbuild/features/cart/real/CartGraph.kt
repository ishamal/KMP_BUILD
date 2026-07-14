package com.softlogic.kmpbuild.features.cart.real

import androidx.lifecycle.ViewModel
import com.softlogic.kmpbuild.core.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.reflect.KClass

/** Scope marker for the Cart screen's Metro graph extension (lives only while the screen is shown). */
abstract class CartScope private constructor()

/**
 * A screen-lifetime resource for Cart: a [CoroutineScope] that exists only while the Cart screen is
 * on-screen. Created with the [CartGraph] extension (screen entered) and cancelled by the entry
 * composable's `onDispose` (screen left) — the concrete thing the entry-composable pattern manages.
 * Swap in real screen-scoped work (a socket, a session) as the feature grows.
 */
@Inject
@SingleIn(CartScope::class)
class CartSession {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun close() {
        scope.cancel()
    }
}

/** CartScope-scoped [MetroViewModelFactory] backing `metroViewModel()` inside the Cart screen. */
@ContributesBinding(CartScope::class)
@SingleIn(CartScope::class)
@Inject
class CartViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()

/**
 * Screen-scoped Metro graph extension for Cart. The entry composable ([CartScreenContribution])
 * creates it on entry and tears it down on exit, so [CartSession] and the CartScope-scoped
 * [CartViewModel] live exactly as long as the screen. `: ViewModelGraph` gives it its own
 * `metroViewModelFactory` + ViewModel map. [Factory] is `@ContributesTo(AppScope::class)`, so the app
 * `AppGraph` exposes it for injection into the contribution.
 */
@GraphExtension(scope = CartScope::class)
interface CartGraph : ViewModelGraph {
    val session: CartSession

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        // Distinct name per feature: all these factories are merged into the app graph via
        // @ContributesTo, so a shared `create()` (differing only by return type) would clash.
        fun createCartGraph(): CartGraph
    }
}
