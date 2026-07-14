package com.softlogic.kmpbuild.core

import androidx.compose.runtime.Composable

/**
 * The Android-only counterpart of [HomeFeature]: a feature's native Jetpack Compose screen,
 * contributed into the app's Metro graph from the feature's `:real` module (androidMain) via
 * `@ContributesIntoSet(AppScope::class)`. The app's `Set<AndroidFeatureScreen>` therefore contains
 * exactly the screens for features compiled into this store's build — letting the shared nav host
 * render them without a compile-time reference to each (per-flavor) screen class.
 *
 * Lives in `:core:feature` (androidMain) because both `:androidApp` and every feature `:real` module
 * depend on it; it can't live in `:androidApp` (the `:real` modules would then depend back on the app).
 * It stays out of commonMain so `@Composable` never leaks into the iOS/common targets.
 */
interface AndroidFeatureScreen {
    /** Matches the corresponding [HomeFeature.id]; the nav host dispatches by this. */
    val id: FeatureId

    /** The feature's screen. [onBack] pops it off the nav back stack. */
    @Composable
    fun Content(onBack: () -> Unit)
}
