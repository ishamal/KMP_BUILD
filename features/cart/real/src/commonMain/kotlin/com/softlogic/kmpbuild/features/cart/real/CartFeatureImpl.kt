package com.softlogic.kmpbuild.features.cart.real

import com.softlogic.kmpbuild.features.cart.api.CartFeature

/**
 * Real implementation of the "cart" feature. Compiled in only for stores whose STORES entry
 * lists "cart" — build-time exclusion, driven by the storeCatalog.
 */
class CartFeatureImpl : CartFeature {
    override val id: String = "cart"
    override fun describe(): String = "Cart feature (real implementation)"
}
