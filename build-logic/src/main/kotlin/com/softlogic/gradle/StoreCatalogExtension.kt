package com.softlogic.gradle

/**
 * The `storeCatalog` object build scripts read — the public menu of the catalog.
 *
 * Kept `open` because Gradle decorates extensions and needs to subclass it. Every function
 * is a plain list lookup over [storeDefs].
 */
open class StoreCatalogExtension(private val storeDefs: List<StoreDef>) {

    /** The default store when `-Pstore=<store>` is not passed (used by iOS). */
    val selectedStore: String get() = SELECTED_STORE

    val storeNames: Set<String> get() = storeDefs.map { it.name }.toSet()

    /** store -> its feature list. */
    fun stores(): Map<String, List<String>> = storeDefs.associate { it.name to it.features }

    fun featuresFor(store: String): List<String> = def(store).features

    fun businessUnitDefaults(store: String): String = def(store).businessUnitDefaults

    /** e.g. applicationId("storeB") -> "com.softlogic.kmpbuild.storeb". */
    fun applicationId(store: String): String =
        "$BASE_APPLICATION_ID.${def(store).name.lowercase()}"

    private fun def(store: String): StoreDef =
        storeDefs.firstOrNull { it.name == store }
            ?: error("Unknown store '$store'. Known stores: $storeNames")

    companion object {
        const val SELECTED_STORE = "storeA"
        const val BASE_APPLICATION_ID = "com.softlogic.kmpbuild"
    }
}
