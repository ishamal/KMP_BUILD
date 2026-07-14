package com.softlogic.kmpbuild.core

/**
 * The runtime identity of every feature, as a closed enum — the runtime counterpart of the
 * build-time `Feature` enum in build-logic (two worlds, deliberately separate types: build-logic
 * classes live on Gradle's classpath and never ship in the app).
 *
 * Replaces the magic-string ids ("cart", "orders", …) that were previously duplicated across
 * feature impls, screen contributions, HomeScreen's tile list, and the Swift side: a typo now
 * fails to compile instead of silently breaking tile↔screen dispatch.
 *
 * [title] is the display name (one place, both platforms — the home screens need it even for
 * features NOT compiled into this store, to render the disabled tile). [isHomeTile] marks whether
 * the feature appears on the home grid at all — LOGIN is the auth gate, not a tile.
 */
enum class FeatureId(val title: String, val isHomeTile: Boolean = true) {
    LOGIN("Login", isHomeTile = false),
    CART("Cart"),
    SETTINGS("Settings"),
    ORDERS("Orders"),
    ;

    companion object {
        /** Declaration-ordered home-grid tiles (the whole catalog, shipped or not). */
        val homeTiles: List<FeatureId> = entries.filter { it.isHomeTile }
    }
}
