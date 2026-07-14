package com.softlogic.gradle

/**
 * Every feature that can ship in a store. The compiler owns feature-name correctness:
 * a typo can't exist in the STORES table because you can only reference these constants,
 * and the IDE autocompletes/refactors them.
 *
 * Module paths are derived from the constant name (ORDERS -> :features:orders:real), so
 * adding a feature = add a constant here + create/include the matching modules. Whether
 * those modules actually exist is still validated by StoreCatalogPlugin (an enum can
 * promise a name is well-formed, not that the module is on disk).
 */
enum class Feature {
    LOGIN, CART, ORDERS, SETTINGS;

    /** ORDERS -> "orders" — the bare name used in module paths. */
    val moduleName: String get() = name.lowercase()
    val apiModulePath: String get() = ":features:$moduleName:api"
    val realModulePath: String get() = ":features:$moduleName:real"
}

/**
 * Every store, as an enum — the single source of truth for what each store ships. This is the
 * whole "config": compiler-checked, no file parsing, and editing it recompiles build-logic so
 * the configuration cache invalidates automatically.
 *
 * Adding a store is adding one constant below; Android (product flavors) and iOS (the shared
 * framework) both pick it up. The string form Gradle/Xcode need (flavor name, `-Pstore=` value,
 * applicationId suffix) is derived once, in [storeName] — never written by hand elsewhere.
 */
enum class Store(
    val features: List<Feature>,            // compiler-checked — see [Feature]
    val businessUnitDefaults: String = "",  // surfaced to the app as BuildConfig.BUSINESS_UNIT_DEFAULTS
) {
    STORE_A(listOf(Feature.LOGIN, Feature.CART, Feature.ORDERS), "KEELS:USBL,CARGILLS:SENM"),
    STORE_B(listOf(Feature.LOGIN, Feature.CART, Feature.SETTINGS), "KEELS:USBL"),
    STORE_C(listOf(Feature.LOGIN, Feature.ORDERS), "KEELS:USBL"),
    ;

    /** STORE_A -> "storeA" — the Android flavor name, `-Pstore=` value, and applicationId suffix. */
    val storeName: String = name.split('_')
        .mapIndexed { i, part ->
            if (i == 0) part.lowercase() else part.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
        .joinToString("")

    companion object {
        /** The default store when `-Pstore=<store>` is not passed (used by iOS). */
        val DEFAULT: Store = STORE_A

        /** "storeB" (a `-Pstore=` value) -> [STORE_B], with a clear error for unknown names. */
        fun byName(name: String): Store =
            entries.firstOrNull { it.storeName == name }
                ?: error("Unknown store '$name'. Known stores: ${entries.map { it.storeName }}")
    }
}
