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
    }
}
