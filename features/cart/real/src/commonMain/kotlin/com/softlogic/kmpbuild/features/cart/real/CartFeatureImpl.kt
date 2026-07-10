package com.softlogic.kmpbuild.features.cart.real

import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.HomeFeature
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * The "cart" feature. `@ContributesIntoSet` registers it into the app graph's
 * `Set<HomeFeature>` — but only when this module is compiled in (i.e. the store ships "cart").
 */
@ContributesIntoSet(AppScope::class)
@Inject
class CartFeatureImpl : HomeFeature {
    override val id: String = "cart"
    override val title: String = "Cart"
}
