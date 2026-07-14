package com.softlogic.kmpbuild

import androidx.navigation3.runtime.NavKey

/**
 * Typed Navigation 3 destinations. [Home] is the tile grid; [FeatureScreenKey] is any feature's
 * screen, identified by the same id as its `HomeFeature` / `AndroidFeatureScreen`. [AppRoot] resolves
 * that id to a Metro-contributed screen at render time, so no destination statically references a
 * per-store feature module. A feature screen is only reachable if its tile was enabled (the store
 * shipped it), so a screen for an unshipped feature is never navigated to.
 */
sealed interface AppKey : NavKey {
    data object Home : AppKey
    data class FeatureScreenKey(val id: String) : AppKey
}
