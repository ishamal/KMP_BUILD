package com.softlogic.kmpbuild

import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.HomeFeature
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.createGraph

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
}

/**
 * Swift-facing façade over the graph. A Kotlin `object` bridges to Swift as `HomeFeatures.shared`.
 * We expose ids + `isEnabled` (function interop) rather than the raw `Set<HomeFeature>`, which would
 * bridge as an `NSSet` and be awkward from Swift. Replaces the old generated `StoreInfo`.
 */
object HomeFeatures {
    private val features: Set<HomeFeature> by lazy { createGraph<AppGraph>().features }

    val ids: List<String> get() = features.map { it.id }.sorted()

    fun isEnabled(id: String): Boolean = features.any { it.id == id }
}
