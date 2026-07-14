package com.softlogic.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * iOS counterpart of [StoreFeaturesPlugin]: turns catalog data into the shared module's per-store
 * framework wiring, so the single KMP framework compiles in **only** the selected store's feature
 * `:real` modules (build-time exclusion — same principle as Android's per-flavor dependencies).
 *
 * iOS has no Gradle flavors, so the store is chosen with `-Pstore=<store>` (default =
 * [Store.DEFAULT]) — the one inherently-string boundary, converted to the typed [Store] once here.
 *
 * The module's build script keeps only module-specific facts (targets, baseName, sdk versions);
 * this plugin owns everything the STORES catalog decides:
 *  - exports `:core:feature` (FeatureId/HomeFeature for Swift) + the store's `:api` contracts
 *    into every framework binary the script defines,
 *  - links the store's `:api`/`:real` modules into `iosMain`,
 *  - registers `generateIosStore` (per-store xcconfig scaffolding for Xcode).
 */
class StoreIosFeaturesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(StoreCatalogPlugin::class.java) // ensure catalog
        val catalog = target.extensions.getByType(StoreCatalogExtension::class.java)

        // orNull + isNotBlank so an empty `-Pstore=` (e.g. an unset GRADLE_STORE from Xcode)
        // falls back to the default instead of failing with "Unknown store ''".
        val store = target.providers.gradleProperty("store").orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { catalog.storeFor(it) }
            ?: catalog.selectedStore

        // Run once the KMP plugin is present, so the kotlin { } extension exists. configureEach is
        // lazy — it applies to the targets/frameworks whenever the module's script defines them.
        target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)

            kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach {
                binaries.withType(Framework::class.java).configureEach {
                    // Export the shared contracts so SwiftUI sees FeatureId/HomeFeatures, plus each
                    // shipped feature's :api contract.
                    export(target.project(":core:feature"))
                    store.features.forEach { export(target.project(it.apiModulePath)) }
                }
            }

            kotlin.sourceSets.matching { it.name == "iosMain" }.configureEach {
                dependencies {
                    // export() above requires these to be api dependencies.
                    api(target.project(":core:feature"))
                    store.features.forEach {
                        api(target.project(it.apiModulePath))              // exported contract
                        implementation(target.project(it.realModulePath))  // impl, linked not exported
                    }
                }
            }
        }

        // Scaffold one xcconfig per store (catalog-driven) so Xcode can point a per-store build
        // configuration at each. Run: ./gradlew generateIosStore  (see documents/IOS_PER_STORE_APPS.md).
        // doLast captures only plain values (dir + names), keeping the task config-cache safe.
        val xcconfigDir = target.rootDir.resolve("iosApp/Configuration")
        val storeNames = catalog.storeNames.sorted()
        target.tasks.register("generateIosStore") {
            doLast {
                xcconfigDir.mkdirs()
                storeNames.forEach { s ->
                    xcconfigDir.resolve("store-$s.xcconfig").writeText(
                        """
                            // Generated from the STORES catalog by ./gradlew generateIosStore — do not edit.
                            // Point a per-store Xcode build configuration at this file (see IOS_PER_STORE_APPS.md).
                            #include "Config.xcconfig"

                            GRADLE_STORE = $s

                            // Kotlin can't infer debug/release from a custom configuration name (e.g. "Debug-$s"),
                            // so state it explicitly. Defaults to debug (for Run). For a Release/Archive per-store
                            // configuration, set KOTLIN_FRAMEWORK_BUILD_TYPE = release on that configuration.
                            KOTLIN_FRAMEWORK_BUILD_TYPE = debug
                        """.trimIndent() + "\n",
                    )
                }
                logger.lifecycle("generateIosStore: wrote ${storeNames.size} xcconfig(s) to $xcconfigDir")
            }
        }
    }
}
