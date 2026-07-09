package com.softlogic.gradle

/**
 * One store's build-time definition — the single source of truth for what a store ships.
 *
 * Adding a store is adding one [StoreDef] line below; Android (product flavors) and iOS
 * (the shared framework) both pick it up.
 */
data class StoreDef(
    val name: String,                       // flavor name + applicationId suffix
    val features: List<String>,             // bare feature names -> :features:<name>:real modules
    val businessUnitDefaults: String = "",  // surfaced to the app as BuildConfig.BUSINESS_UNIT_DEFAULTS
)

/**
 * Every store. This is the whole "config": a plain, typed Kotlin list — compiler-checked,
 * no file parsing, and editing it recompiles build-logic so the configuration cache
 * invalidates automatically.
 */
internal val STORES: List<StoreDef> = listOf(
    StoreDef("storeA", listOf("login", "cart", "settings", "orders"), "KEELS:USBL,CARGILLS:SENM"),
    StoreDef("storeB", listOf("login", "cart", "settings"), "KEELS:USBL"),
    StoreDef("storeC", listOf("login", "cart", "orders"), "KEELS:USBL"),
)
