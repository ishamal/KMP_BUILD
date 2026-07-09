package com.softlogic.kmpbuild.features.settings.real

import com.softlogic.kmpbuild.features.settings.api.SettingsFeature

/**
 * Real implementation of the "settings" feature. Compiled in only for stores whose STORES entry
 * lists "settings" — build-time exclusion, driven by the storeCatalog.
 */
class SettingsFeatureImpl : SettingsFeature {
    override val id: String = "settings"
    override fun describe(): String = "Settings feature (real implementation)"
}
