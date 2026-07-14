plugins {
    `kotlin-dsl` // lets us write Gradle plugins in Kotlin
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // The AGP API — needed so StoreFeaturesPlugin can configure the Android
    // Application DSL (create product flavors). Uses the same version as the app.
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    // The KGP API — needed so StoreIosFeaturesPlugin can configure the KMP DSL
    // (framework exports + iosMain dependencies). Same version as the app's Kotlin.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        register("storeCatalog") {
            id = "com.softlogic.store-catalog"
            implementationClass = "com.softlogic.gradle.StoreCatalogPlugin"
        }
        register("storeFeatures") {
            id = "com.softlogic.store-features"
            implementationClass = "com.softlogic.gradle.StoreFeaturesPlugin"
        }
        register("storeIosFeatures") {
            id = "com.softlogic.store-ios-features"
            implementationClass = "com.softlogic.gradle.StoreIosFeaturesPlugin"
        }
    }
}
