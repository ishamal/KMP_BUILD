package com.softlogic.kmpbuild.features.orders.real

import com.softlogic.kmpbuild.core.AppScope
import com.softlogic.kmpbuild.core.FeatureId
import com.softlogic.kmpbuild.core.HomeFeature
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * The "orders" feature. `@ContributesIntoSet` registers it into the app graph's
 * `Set<HomeFeature>` — but only when this module is compiled in (i.e. the store ships "orders").
 * Single supertype (HomeFeature) → Metro infers the bound type implicitly.
 */
@ContributesIntoSet(AppScope::class)
@Inject
class OrdersFeatureImpl : HomeFeature {
    override val id: FeatureId = FeatureId.ORDERS
}
