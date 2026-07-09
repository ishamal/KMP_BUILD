package com.softlogic.kmpbuild.features.settings.api

/**
 * Public contract for the "settings" feature. Exported to Swift via the Shared framework,
 * so only stores that ship "settings" expose it.
 */
interface SettingsFeature {
    val id: String
    fun describe(): String
}
