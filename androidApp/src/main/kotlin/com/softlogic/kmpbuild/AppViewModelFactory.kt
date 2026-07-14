package com.softlogic.kmpbuild

import androidx.lifecycle.ViewModel
import com.softlogic.kmpbuild.core.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * The app-scoped [MetroViewModelFactory]: a `ViewModelProvider.Factory` backed by the Metro graph's
 * ViewModel multibindings instead of reflection. Each feature ViewModel is contributed into
 * `viewModelProviders` from its `:real/androidMain` via `@ContributesIntoMap(AppScope::class)` +
 * `@ViewModelKey`, so `metroViewModel<T>()` in a screen constructs a DI-wired instance.
 *
 * `@ContributesBinding(AppScope::class)` binds it as the graph's `metroViewModelFactory`
 * ([com.softlogic.kmpbuild.core]'s `AppGraph : ViewModelGraph`). Mirrors Metro's `compose-viewmodels`
 * sample `InjectedViewModelFactory`.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AppViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()
