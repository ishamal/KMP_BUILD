package com.softlogic.kmpbuild

import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.HomeFeature
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.createGraph

/**
 * The Android app's Metro graph. It aggregates every `@ContributesIntoSet(AppScope::class)`
 * contribution on its compile classpath — which, because features are linked per product flavor,
 * is exactly the current store's shipped features.
 *
 * `@Multibinds(allowEmpty = true)` declares the multibinding so a store that ships none of the
 * contributing features still resolves to an empty set instead of a missing-binding error.
 */
@DependencyGraph(AppScope::class)
interface AppGraph {
    @Multibinds(allowEmpty = true)
    val features: Set<HomeFeature>
}

fun createAppGraph(): AppGraph = createGraph<AppGraph>()
