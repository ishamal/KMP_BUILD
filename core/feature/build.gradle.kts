import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // Compose only for the androidMain `AndroidFeatureScreen` contract (its `@Composable fun Content`).
    // Kept out of commonMain/iosMain so the Android UI stays native-per-platform.
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.softlogic.kmpbuild.core.feature"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        // The Compose *compiler* plugin runs on every target (incl. iOS) and version-checks for the
        // Compose runtime on the classpath, so the multiplatform `compose.runtime` must be visible to
        // all targets — even though the only `@Composable` code (AndroidFeatureScreen) lives in
        // androidMain. It's a small MPP artifact and is dead-code-eliminated from the iOS framework.
        commonMain.dependencies {
            implementation(libs.compose.runtime)
        }
        // Android-only: FeatureScreenEntry needs a per-entry ViewModelStoreOwner + the Metro
        // ViewModel factory CompositionLocal. Kept in androidMain so iOS/common stay Compose-UI-free.
        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.metrox.viewmodel)
            implementation(libs.metrox.viewmodel.compose)
        }
    }
}
