package com.softlogic.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers the `storeCatalog` extension and fails fast if a store lists a feature that has
 * no matching `:features:<name>:real` module. No file I/O — the data is the [Store] enum,
 * compiled in.
 */
class StoreCatalogPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Idempotent: if two plugins both need the catalog, register it only once.
        if (target.extensions.findByName("storeCatalog") != null) return

        // Fail-fast validation. Store and feature *names* are compiler-checked (both are enums),
        // so the remaining failure mode is a Feature constant whose module was never created
        // or never include()d in settings.gradle.kts — catch that here with a clear message
        // instead of a confusing "project not found" deep in the build.
        Store.entries.forEach { store ->
            store.features.forEach { feature ->
                require(target.rootProject.findProject(feature.realModulePath) != null) {
                    "Store '${store.storeName}' lists Feature.${feature.name}, but there is no " +
                        "module ${feature.realModulePath}. Create the module and include() it in " +
                        "settings.gradle.kts, or remove the constant from Stores.kt."
                }
            }
        }

        target.extensions.create("storeCatalog", StoreCatalogExtension::class.java)
    }
}
