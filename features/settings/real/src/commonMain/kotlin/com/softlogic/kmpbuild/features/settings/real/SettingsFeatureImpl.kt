package com.softlogic.kmpbuild.features.settings.real

import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.HomeFeature
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * The "settings" feature. `@ContributesIntoSet` registers it into the app graph's
 * `Set<HomeFeature>` — but only when this module is compiled in (i.e. the store ships "settings").
 */
@ContributesIntoSet(AppScope::class)
@Inject
class SettingsFeatureImpl : HomeFeature {
    override val id: String = "settings"
    override val title: String = "Settings"
}
