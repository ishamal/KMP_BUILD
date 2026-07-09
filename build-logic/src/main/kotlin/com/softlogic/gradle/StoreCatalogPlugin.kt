package com.softlogic.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers the `storeCatalog` extension and fails fast if a store lists a feature that has
 * no matching `:features:<name>:real` module. No file I/O — the data is [STORES], compiled in.
 */
class StoreCatalogPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Idempotent: if two plugins both need the catalog, register it only once.
        if (target.extensions.findByName("storeCatalog") != null) return

        // Fail-fast validation: a typo like "rebatte" fails here with a clear message
        // instead of a confusing "project not found" deep in the build.
        STORES.forEach { store ->
            store.features.forEach { feature ->
                require(target.rootProject.findProject(":features:$feature:real") != null) {
                    "Store '${store.name}' lists feature '$feature', but there is no module " +
                        ":features:$feature:real. Fix the STORES table in build-logic (Stores.kt)."
                }
            }
        }

        target.extensions.create("storeCatalog", StoreCatalogExtension::class.java, STORES)
    }
}
