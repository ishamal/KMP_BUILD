package com.softlogic.kmpbuild.features.cart.api

/**
 * Public contract for the "cart" feature. Exported to Swift via the Shared framework,
 * so only stores that ship "cart" expose it.
 */
interface CartFeature {
    val id: String
    fun describe(): String
}
