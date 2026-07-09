package com.softlogic.kmpbuild.features.login.real

import com.softlogic.kmpbuild.features.login.api.LoginFeature

/**
 * Real implementation of the "login" feature. Compiled in only for stores whose STORES entry
 * lists "login" — build-time exclusion, driven by the storeCatalog.
 */
class LoginFeatureImpl : LoginFeature {
    override val id: String = "login"
    override fun describe(): String = "Login feature (real implementation)"
}
