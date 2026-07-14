package com.softlogic.kmpbuild.features.settings.real

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

/** Scope marker for the Settings screen's Metro graph extension (lives only while the screen is shown). */
abstract class SettingsScope private constructor()

/**
 * A screen-lifetime resource for Settings: a [CoroutineScope] that exists only while the Settings
 * screen is on-screen. Created with the [SettingsGraph] extension (screen entered) and cancelled by
 * the entry composable's `onDispose` (screen left) — the concrete thing the entry-composable pattern
 * manages. Swap in real screen-scoped work (a socket, a session) as the feature grows.
 */
@Inject
@SingleIn(SettingsScope::class)
class SettingsSession {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun close() {
        scope.cancel()
    }
}

/** SettingsScope-scoped [MetroViewModelFactory] backing `metroViewModel()` inside the Settings screen. */
@ContributesBinding(SettingsScope::class)
@SingleIn(SettingsScope::class)
@Inject
class SettingsViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()

/**
 * Screen-scoped Metro graph extension for Settings. The entry composable
 * ([SettingsScreenContribution]) creates it on entry and tears it down on exit, so [SettingsSession]
 * and the SettingsScope-scoped [SettingsViewModel] live exactly as long as the screen.
 * `: ViewModelGraph` gives it its own `metroViewModelFactory` + ViewModel map. [Factory] is
 * `@ContributesTo(AppScope::class)`, so the app `AppGraph` exposes it for injection.
 */
@GraphExtension(scope = SettingsScope::class)
interface SettingsGraph : ViewModelGraph {
    val session: SettingsSession

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        // Distinct name per feature: all these factories are merged into the app graph via
        // @ContributesTo, so a shared `create()` (differing only by return type) would clash.
        fun createSettingsGraph(): SettingsGraph
    }
}
