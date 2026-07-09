import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // Exposes the storeCatalog extension (same STORES source of truth as Android).
    id("com.softlogic.store-catalog")
}
// NOTE: this module holds shared *logic* only — no UI. Android UI is native Jetpack Compose in
// :androidApp; iOS UI is SwiftUI in iosApp. Both read the per-store feature set (STORE_FEATURES /
// StoreInfo) from here.

val storeCatalog = extensions.getByType<com.softlogic.gradle.StoreCatalogExtension>()
// iOS has no Gradle flavors, so the store is chosen with -Pstore=<store> (default = selectedStore).
// orNull + isNotBlank so an empty `-Pstore=` (e.g. an unset GRADLE_STORE from Xcode) falls back to
// the default instead of failing with "Unknown store ''".
val store = providers.gradleProperty("store").orNull?.takeIf { it.isNotBlank() } ?: storeCatalog.selectedStore
val storeFeatures = storeCatalog.featuresFor(store)

// Scaffold one xcconfig per store (catalog-driven) so Xcode can point a per-store build
// configuration at each. Run: ./gradlew generateIosStore  (see documents/IOS_PER_STORE_APPS.md).
val perStoreXcconfigDir = rootDir.resolve("iosApp/Configuration")
val allStoreNames = storeCatalog.storeNames.sorted()
tasks.register("generateIosStore") {
    val dir = perStoreXcconfigDir
    val names = allStoreNames
    doLast {
        dir.mkdirs()
        names.forEach { s ->
            dir.resolve("store-$s.xcconfig").writeText(
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
        logger.lifecycle("generateIosStore: wrote ${names.size} xcconfig(s) to $dir")
    }
}

// iOS has no BuildConfig, so generate a tiny StoreInfo.kt exposing the compiled-in feature set.
// Config-cache-safe: the task action closes over plain values (List<String>, String, the output
// Provider) — never the storeCatalog extension.
val generateIosStoreInfo = tasks.register("generateIosStoreInfo") {
    val outDir = layout.buildDirectory.dir("generated/storeInfo/ios")
    val features = storeFeatures
    val storeName = store
    // Declare inputs so the task re-runs when -Pstore changes (outputs alone would go stale).
    inputs.property("store", storeName)
    inputs.property("features", features)
    outputs.dir(outDir)
    doLast {
        val pkgDir = outDir.get().asFile.resolve("com/softlogic/kmpbuild")
        pkgDir.mkdirs()
        val set = features.joinToString(", ") { "\"$it\"" }
        pkgDir.resolve("StoreInfo.kt").writeText(
            """
                package com.softlogic.kmpbuild

                // Generated for store '$storeName' — do not edit.
                // Mirrors Android's BuildConfig.STORE_FEATURES. Consumed by SwiftUI.
                object StoreInfo {
                    val enabledFeatures: Set<String> = setOf($set)

                    // Function interop is the most robust across the Kotlin/Swift bridge.
                    fun isEnabled(feature: String): Boolean = feature in enabledFeatures
                }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Export each shipped feature's :api contract so SwiftUI can consume it.
            storeFeatures.forEach { export(project(":features:$it:api")) }
        }
    }
    
    androidLibrary {
       namespace = "com.softlogic.kmpbuild.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        iosMain {
            // Compile the generated StoreInfo.kt into the framework (only iOS needs it).
            kotlin.srcDir(generateIosStoreInfo)
            dependencies {
                // Link only this store's feature modules into the single KMP framework —
                // build-time exclusion, the iOS counterpart to Android's per-flavor deps.
                storeFeatures.forEach {
                    api(project(":features:$it:api"))             // exported contract
                    implementation(project(":features:$it:real")) // impl, linked but not exported
                }
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}