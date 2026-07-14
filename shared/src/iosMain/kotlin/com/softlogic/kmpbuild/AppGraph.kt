package com.softlogic.kmpbuild

import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.FeatureId
import com.softlogic.kmpbuild.core.HomeFeature
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.createGraphFactory

/**
 * The iOS Metro graph, compiled into the Shared framework. It aggregates every
 * `@ContributesIntoSet(AppScope::class)` contribution on iosMain's classpath — which, because
 * `shared` links only the `-Pstore` store's feature `:real` modules, is exactly that store's
 * features. The Android counterpart lives in `:androidApp/AppGraph.kt`.
 */
@DependencyGraph(AppScope::class)
interface AppGraph {
    @Multibinds(allowEmpty = true)
    val features: Set<HomeFeature>

    // Factory entry point — mirrors :androidApp/AppGraph.kt. Add @Provides params to create() to
    // feed platform values into the graph.
    @DependencyGraph.Factory
    interface Factory {
        fun create(): AppGraph
    }
}

/**
 * Swift-facing façade over the graph. A Kotlin `object` bridges to Swift as `HomeFeatures.shared`.
 * We expose the tile catalog + `isEnabled` (function interop) rather than the raw `Set<HomeFeature>`,
 * which would bridge as an `NSSet` and be awkward from Swift. [FeatureId] bridges as a class with
 * static entries (`FeatureId.cart`, …), so Swift shares the same typed ids and titles — no more
 * duplicated string/title lists in ContentView. Replaces the old generated `StoreInfo`.
 */
object HomeFeatures {
    private val features: Set<HomeFeature> by lazy { createGraphFactory<AppGraph.Factory>().create().features }

    /** Every tile the home can show (whole catalog, shipped or not), in declaration order. */
    val homeTiles: List<FeatureId> get() = FeatureId.homeTiles

    fun isEnabled(id: FeatureId): Boolean = features.any { it.id == id }
}
