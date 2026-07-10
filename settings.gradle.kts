rootProject.name = "KmpBuild"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Produces the store-catalog / store-features convention plugins. Must be the first
    // line inside pluginManagement so the plugin ids resolve.
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")

// Shared contracts consumed by every feature + the app graphs.
include(":core:feature")

// Feature modules — each ships an :api contract and a :real implementation.
// The STORES table in build-logic decides which :real modules each store compiles in.
include(":features:login:api")
include(":features:login:real")
include(":features:cart:api")
include(":features:cart:real")
include(":features:settings:api")
include(":features:settings:real")
include(":features:orders:api")
include(":features:orders:real")