package com.softlogic.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Android-only convention plugin: turns catalog data into product flavors (one per store)
 * and per-flavor feature dependencies, so each store's APK compiles in only the feature
 * `:real` modules it ships (build-time exclusion).
 */
class StoreFeaturesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(StoreCatalogPlugin::class.java) // ensure catalog
        val catalog = target.extensions.getByType(StoreCatalogExtension::class.java)

        // Run once the Android application plugin is present, so ApplicationExtension exists.
        target.pluginManager.withPlugin("com.android.application") {
            val android = target.extensions.getByType(ApplicationExtension::class.java)

            android.flavorDimensions += "store"
            catalog.stores().forEach { store ->
                android.productFlavors.create(store.storeName) {
                    dimension = "store"
                    applicationId = catalog.applicationId(store)
                    buildConfigField(
                        "String",
                        "BUSINESS_UNIT_DEFAULTS",
                        "\"${store.businessUnitDefaults}\"",
                    )
                    // NOTE: which features a store ships is now sourced at runtime from the Metro
                    // graph (Set<HomeFeature> via @ContributesIntoSet), not a BuildConfig string.
                }
            }

            // The <flavor>Implementation buckets (storeAImplementation, …) are created as AGP
            // processes the flavors — add the feature :real modules after evaluation so those
            // configurations exist.
            target.afterEvaluate {
                catalog.stores().forEach { store ->
                    store.features.forEach { feature ->
                        target.dependencies.add(
                            "${store.storeName}Implementation",
                            target.project(feature.realModulePath),
                        )
                    }
                }
            }
        }
    }
}
