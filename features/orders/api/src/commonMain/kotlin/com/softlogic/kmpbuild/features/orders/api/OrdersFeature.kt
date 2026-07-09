package com.softlogic.kmpbuild.features.orders.api

/**
 * Public contract for the "orders" feature. Exported to Swift via the Shared framework,
 * so only stores that ship "orders" expose it.
 */
interface OrdersFeature {
    val id: String
    fun describe(): String
}
