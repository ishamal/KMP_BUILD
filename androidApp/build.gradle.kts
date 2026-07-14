import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Creates one product flavor per store and links only that store's feature :real modules.
    id("com.softlogic.store-features")
    // Compile-time DI — features self-register into the app graph via @ContributesIntoSet.
    alias(libs.plugins.metro)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)
    implementation(projects.core.feature)

    implementation(libs.androidx.activity.compose)

    // Native Android (Jetpack) Compose UI — this app owns its UI; nothing is shared from :shared.
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Per-feature ViewModels (one presentation class per feature) + Compose viewModel() helper.
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    // Jetpack Navigation 3 — typed back stack + NavDisplay for home <-> feature-screen navigation.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // Metro ViewModel factory + LocalMetroViewModelFactory (provided at the nav-host root).
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}

android {
    namespace = "com.softlogic.kmpbuild"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.softlogic.kmpbuild"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    // Required because the store-features plugin writes BuildConfig.BUSINESS_UNIT_DEFAULTS per flavor.
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}