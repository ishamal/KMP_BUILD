import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // Store-driven iOS wiring — the iOS counterpart of androidApp's store-features plugin.
    // Owns everything the STORES catalog decides: per-store framework exports, per-store iosMain
    // feature linking, and the generateIosStore xcconfig task. (Applies store-catalog itself.)
    id("com.softlogic.store-ios-features")
    // Compile-time DI — the iOS AppGraph aggregates the store's shipped features.
    alias(libs.plugins.metro)
}
// NOTE: this module holds shared *logic* only — no UI. Android UI is native Jetpack Compose in
// :androidApp; iOS UI is SwiftUI in iosApp. The per-store feature set is sourced at runtime from the
// Metro graph (Set<HomeFeature>) — see iosMain/AppGraph.kt (iOS) and :androidApp/AppGraph.kt (Android).

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Per-store exports (:core:feature + the shipped features' :api contracts) are added by
            // the store-ios-features plugin, as are iosMain's per-store feature dependencies.
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
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
