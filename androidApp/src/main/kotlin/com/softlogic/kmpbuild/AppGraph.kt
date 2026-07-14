package com.softlogic.kmpbuild

import com.softlogic.kmpbuild.core.AndroidFeatureScreen
import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.HomeFeature
import com.softlogic.kmpbuild.core.MetroAppComponentProvider
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The Android app's Metro graph, and the concrete [MetroAppComponentProvider] for Android. It
 * aggregates every `@ContributesIntoSet(AppScope::class)` contribution on its compile classpath —
 * which, because features are linked per product flavor, is exactly the current store's shipped
 * features.
 *
 * `@Multibinds(allowEmpty = true)` declares the multibinding so a store that ships none of the
 * contributing features still resolves to an empty set instead of a missing-binding error.
 */
@DependencyGraph(AppScope::class)
interface AppGraph : MetroAppComponentProvider, AndroidScreenProvider, ViewModelGraph {
    // The multibinding itself. Declared fresh here — Metro forbids a @Multibinds accessor from being
    // an `override`, so it can't directly be MetroAppComponentProvider.features.
    @Multibinds(allowEmpty = true)
    val featureBindings: Set<HomeFeature>

    // Satisfy the provider contract with a plain delegating getter (a concrete member, not a binding).
    override val features: Set<HomeFeature> get() = featureBindings

    // The per-feature Android screens, contributed from each shipped :real module's androidMain via
    // @ContributesIntoSet(AppScope::class). Declared fresh for the same reason as featureBindings.
    // allowEmpty because a store shipping only non-screen features (e.g. just login) has no screens.
    @Multibinds(allowEmpty = true)
    val androidScreenBindings: Set<AndroidFeatureScreen>

    override val androidScreens: Set<AndroidFeatureScreen> get() = androidScreenBindings

    // Factory entry point — build the graph via createGraphFactory<AppGraph.Factory>().create().
    // Add @Provides parameters to create() here when the app needs to feed values in (e.g. a Context).
    @DependencyGraph.Factory
    interface Factory {
        fun create(): AppGraph
    }
}

fun createAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>().create()
