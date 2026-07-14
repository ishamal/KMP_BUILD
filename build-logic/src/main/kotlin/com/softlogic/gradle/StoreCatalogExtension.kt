package com.softlogic.gradle

/**
 * The `storeCatalog` object build scripts read — the public menu of the catalog.
 *
 * Kept `open` because Gradle decorates extensions and needs to subclass it. The data itself
 * lives on the [Store] enum; this extension is the script-facing surface plus the string
 * boundary (`-Pstore=` values are inherently strings — [storeFor] converts them once).
 */
open class StoreCatalogExtension {

    /** The default store when `-Pstore=<store>` is not passed (used by iOS). */
    val selectedStore: Store get() = Store.DEFAULT

    /** The `-Pstore=` / flavor-name strings, e.g. for scaffolding per-store xcconfigs. */
    val storeNames: Set<String> get() = Store.entries.map { it.storeName }.toSet()

    /** Every store, in declaration order. */
    fun stores(): List<Store> = Store.entries

    /** "-Pstore=storeB" -> [Store.STORE_B], failing fast with the known names on a typo. */
    fun storeFor(name: String): Store = Store.byName(name)

    /** e.g. applicationId(Store.STORE_B) -> "com.softlogic.kmpbuild.storeb". */
    fun applicationId(store: Store): String =
        "$BASE_APPLICATION_ID.${store.storeName.lowercase()}"

    companion object {
        const val BASE_APPLICATION_ID = "com.softlogic.kmpbuild"
    }
}
