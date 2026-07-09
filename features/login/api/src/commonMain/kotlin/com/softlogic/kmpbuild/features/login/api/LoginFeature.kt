package com.softlogic.kmpbuild.features.login.api

/**
 * Public contract for the "login" feature. Exported to Swift via the Shared framework,
 * so only stores that ship "login" expose it.
 */
interface LoginFeature {
    val id: String
    fun describe(): String
}
