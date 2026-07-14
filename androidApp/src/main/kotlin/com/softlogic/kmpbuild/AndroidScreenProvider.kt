package com.softlogic.kmpbuild

import com.softlogic.kmpbuild.core.AndroidFeatureScreen
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * Android-only surface of the [AppGraph] that the composition needs but that can't live in commonMain
 * (both are Android/Compose types): the store's Metro-contributed Compose screens
 * (`Set<AndroidFeatureScreen>` via `@ContributesIntoSet`) and the [MetroViewModelFactory] that backs
 * `metroViewModel()`. Implemented by [AppGraph], surfaced by [KmpBuildApp], read in [MainActivity].
 */
interface AndroidScreenProvider {
    val androidScreens: Set<AndroidFeatureScreen>
    val metroViewModelFactory: MetroViewModelFactory
}
