package com.softlogic.kmpbuild.features.orders.real

import com.softlogic.kmpbuild.features.orders.api.OrdersFeature

/**
 * Real implementation of the "orders" feature. Compiled in only for stores whose STORES entry
 * lists "orders" — build-time exclusion, driven by the storeCatalog.
 */
class OrdersFeatureImpl : OrdersFeature {
    override val id: String = "orders"
    override fun describe(): String = "Orders feature (real implementation)"
}
