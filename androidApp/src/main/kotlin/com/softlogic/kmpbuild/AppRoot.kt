package com.softlogic.kmpbuild

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.softlogic.kmpbuild.core.AndroidFeatureScreen
import com.softlogic.kmpbuild.core.FeatureId
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * Jetpack Navigation 3 host. The back stack is a [mutableStateListOf] of typed [AppKey]s starting at
 * [AppKey.Home]; [NavDisplay] renders the top entry and each screen mutates the stack directly (push
 * to open, pop to go back).
 *
 * [screens] is the store's set of Metro-contributed feature screens (one per shipped feature). The
 * single [AppKey.FeatureScreenKey] entry dispatches by id, so the host renders a feature screen
 * without a compile-time reference to its (per-flavor) `:real` module. A screen is only reachable if
 * its tile was enabled — [HomeScreen] only opens enabled tiles and `onOpen` pushes only ids present
 * in [screens] — so a screen for an unshipped feature is never navigated to.
 *
 * [viewModelFactory] is the Metro-backed [MetroViewModelFactory]; providing it through
 * [LocalMetroViewModelFactory] here lets each screen obtain its ViewModel with `metroViewModel()`
 * (DI-constructed) instead of Compose's reflective `viewModel()`.
 */
@Composable
fun AppRoot(
    enabledFeatures: Set<FeatureId>,
    screens: Set<AndroidFeatureScreen>,
    viewModelFactory: MetroViewModelFactory,
) {
    val backStack = remember { mutableStateListOf<AppKey>(AppKey.Home) }

    CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<AppKey.Home> {
                    HomeScreen(
                        enabledFeatures = enabledFeatures,
                        onOpen = { id ->
                            if (screens.any { it.id == id }) backStack.add(AppKey.FeatureScreenKey(id))
                        },
                    )
                }
                entry<AppKey.FeatureScreenKey> { key ->
                    screens.firstOrNull { it.id == key.id }
                        ?.Content(onBack = { backStack.removeLastOrNull() })
                }
            },
        )
    }
}
