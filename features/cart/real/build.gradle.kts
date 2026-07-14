import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // The feature's native Android screen lives in androidMain (Jetpack Compose).
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.metro)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.softlogic.kmpbuild.features.cart.real"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:cart:api"))
            implementation(project(":core:feature"))
            // On all targets so the Compose compiler's version check passes on iOS too; the actual
            // Compose UI is androidMain-only. See core:feature/build.gradle.kts for the rationale.
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            // Native Android (Jetpack Compose) screen + ViewModel for this feature.
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Metro-injected ViewModels: @ContributesIntoMap + @ViewModelKey, retrieved via metroViewModel().
            implementation(libs.metrox.viewmodel)
            implementation(libs.metrox.viewmodel.compose)
        }
    }
}
