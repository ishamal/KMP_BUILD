package com.softlogic.kmpbuild.features.settings.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.softlogic.kmpbuild.core.AndroidFeatureScreen
import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.FeatureScreenEntry
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * The Settings "entry composable" (the Metro DI pattern): [Content] creates the screen-scoped
 * [SettingsGraph] in `remember` and hands it to [FeatureScreenEntry], which provides a per-entry
 * ViewModel store + the graph's scoped `metroViewModelFactory` (so [SettingsScreen]'s
 * `metroViewModel()` resolves the SettingsScope-scoped [SettingsViewModel]) and cancels
 * [SettingsSession] on exit.
 *
 * Registered into the app's `Set<AndroidFeatureScreen>` via `@ContributesIntoSet` — but only when
 * this `:real` module is compiled in (the store ships "settings"). The app graph injects
 * [SettingsGraph.Factory] (contributed to AppScope).
 */
@ContributesIntoSet(AppScope::class)
@Inject
class SettingsScreenContribution(
    private val graphFactory: SettingsGraph.Factory,
) : AndroidFeatureScreen {
    override val id: String = "settings"

    @Composable
    override fun Content(onBack: () -> Unit) {
        val graph = remember { graphFactory.createSettingsGraph() }
        FeatureScreenEntry(
            viewModelFactory = graph.metroViewModelFactory,
            onExit = { graph.session.close() },
        ) {
            SettingsScreen(onBack = onBack)
        }
    }
}
