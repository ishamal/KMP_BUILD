import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // Exposes the storeCatalog extension (same STORES source of truth as Android).
    id("com.softlogic.store-catalog")
    // Compile-time DI — the iOS AppGraph aggregates the store's shipped features.
    alias(libs.plugins.metro)
}
// NOTE: this module holds shared *logic* only — no UI. Android UI is native Jetpack Compose in
// :androidApp; iOS UI is SwiftUI in iosApp. The per-store feature set is sourced at runtime from the
// Metro graph (Set<HomeFeature>) — see iosMain/AppGraph.kt (iOS) and :androidApp/AppGraph.kt (Android).

val storeCatalog = extensions.getByType<com.softlogic.gradle.StoreCatalogExtension>()
// iOS has no Gradle flavors, so the store is chosen with -Pstore=<store> (default = selectedStore).
// The -P value is the one inherently-string boundary; storeFor() converts it to the typed Store
// once (failing fast with the known names on a typo). orNull + isNotBlank so an empty `-Pstore=`
// (e.g. an unset GRADLE_STORE from Xcode) falls back to the default instead of failing.
val store = providers.gradleProperty("store").orNull?.takeIf { it.isNotBlank() }
    ?.let { storeCatalog.storeFor(it) } ?: storeCatalog.selectedStore
val storeFeatures = store.features

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

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Export the shared feature contracts (FeatureId/HomeFeature) — SwiftUI consumes the
            // typed ids directly (FeatureId.cart, …) via the HomeFeatures façade.
            export(project(":core:feature"))
            // Export each shipped feature's :api contract so SwiftUI can consume it.
            storeFeatures.forEach { export(project(it.apiModulePath)) }
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
            dependencies {
                // The multibound HomeFeature contract + FeatureId + AppScope the graph aggregates.
                // `api` (not implementation) because it's export()ed into the framework above.
                api(project(":core:feature"))
                // Link only this store's feature modules into the single KMP framework —
                // build-time exclusion, the iOS counterpart to Android's per-flavor deps.
                // The linked :real modules' @ContributesIntoSet contributions feed the AppGraph.
                storeFeatures.forEach {
                    api(project(it.apiModulePath))             // exported contract
                    implementation(project(it.realModulePath)) // impl, linked but not exported
                }
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}