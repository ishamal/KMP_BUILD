import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Exposes the storeCatalog extension (same STORES source of truth as Android).
    id("com.softlogic.store-catalog")
}

val storeCatalog = extensions.getByType<com.softlogic.gradle.StoreCatalogExtension>()
// iOS has no Gradle flavors, so the store is chosen with -Pstore=<store> (default = selectedStore).
val store = providers.gradleProperty("store").getOrElse(storeCatalog.selectedStore)
val storeFeatures = storeCatalog.featuresFor(store)

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
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        iosMain.dependencies {
            // Link only this store's feature modules into the single KMP framework —
            // build-time exclusion, the iOS counterpart to Android's per-flavor deps.
            storeFeatures.forEach {
                api(project(":features:$it:api"))             // exported contract
                implementation(project(":features:$it:real")) // impl, linked but not exported
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}