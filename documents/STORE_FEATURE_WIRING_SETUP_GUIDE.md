# Setup guide: per-store build-time feature wiring in a KMP project

A **copy-and-adapt, step-by-step** guide to reproduce this project's store→feature build system in
**another** Kotlin Multiplatform project. Goal: each *store* (product variant) compiles in **only** the
feature modules it ships — decided at build time from one typed source of truth — using small Gradle
**convention plugins** in an included `build-logic` build.

> Replace every `com.example…` placeholder with your own package/ids. Anything marked **(optional)** can
> be skipped for a minimal setup.

---

## 0. What you'll build

```
build-logic/                       (a separate "included build" that produces Gradle plugins)
  Stores.kt                        → STORES: the typed store→feature table (source of truth)
  StoreCatalogExtension.kt         → the `storeCatalog` object build scripts read
  StoreCatalogPlugin.kt            → registers `storeCatalog` + validates feature names
  StoreFeaturesPlugin.kt           → Android: one product flavor per store + links its features
main build:
  settings.gradle.kts              → includeBuild("build-logic")
  androidApp/build.gradle.kts      → id("com.example.store-features")   (Android flavors)
  shared/build.gradle.kts          → id("com.example.store-catalog")    (iOS framework)  (optional)
```

### Prerequisites / assumptions
- A KMP project with an **Android application module** and (optionally) a **shared KMP module** that
  produces an iOS framework.
- Features are split into per-feature Gradle modules, e.g. `:features:<name>:api` and
  `:features:<name>:real` (the plugin adds the `:real` modules per store).
- A Gradle **version catalog** at `gradle/libs.versions.toml` with an `agp` version entry.
- **Type-safe project accessors** are nice-to-have but not required.
- How a compiled-in feature *registers itself* into your app (DI, service loader, etc.) is **your app's
  concern** — this system only controls *which modules are compiled per store*. (This repo uses Metro
  `@ContributesBinding` / `@IntoSet`, so a linked feature auto-registers.)

---

## Step 1 — Create the `build-logic` included build

An **included build** is a separate little Gradle build whose only job is to produce plugins. Editing it
does **not** reconfigure your whole project (that's the advantage over `buildSrc`).

**1a. `build-logic/settings.gradle.kts`**
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    // Re-use the app's version catalog so AGP versions can't drift.
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "build-logic"
```

**1b. `build-logic/build.gradle.kts`**
```kotlin
plugins { `kotlin-dsl` }                 // write Gradle plugins in Kotlin

repositories { google(); mavenCentral(); gradlePluginPortal() }

dependencies {
    // Needed so the Android convention plugin can configure the Application DSL (product flavors).
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
}

gradlePlugin {
    plugins {
        register("storeCatalog") {
            id = "com.example.store-catalog"
            implementationClass = "com.example.gradle.StoreCatalogPlugin"
        }
        register("storeFeatures") {
            id = "com.example.store-features"
            implementationClass = "com.example.gradle.StoreFeaturesPlugin"
        }
    }
}
```

**1c. Wire it into the main build** — in your root `settings.gradle.kts`, first line inside
`pluginManagement`:
```kotlin
pluginManagement {
    includeBuild("build-logic")   // ← add this
    repositories { /* your existing repos */ }
}
```

> After this, `./gradlew help` should still work. If it can't find `com.android.tools.build:gradle`,
> check your `agp` catalog entry and that `google()` is in `build-logic`'s repositories.

---

## Step 2 — Define your stores (`Stores.kt`)

`build-logic/src/main/kotlin/com/example/gradle/Stores.kt`
```kotlin
package com.example.gradle

/** One store's build-time definition — the single source of truth for what a store ships. */
data class StoreDef(
    val name: String,                       // flavor name + (optionally) applicationId suffix
    val features: List<String>,             // bare feature names → :features:<name>:real modules
    val businessUnitDefaults: String = "",  // (optional) any per-store string you bake into BuildConfig
)

/** Every store. Add a store = add one line here. */
internal val STORES: List<StoreDef> = listOf(
    StoreDef("storeA", listOf("login", "cart", "orders", "rebate")),
    StoreDef("storeB", listOf("login", "cart", "settings")),
    // …
)
```
**Why a Kotlin table?** It's compiler-checked, needs no file parsing, and editing it recompiles
`build-logic` so Gradle's configuration cache invalidates automatically. (If you instead read external
`.properties`/JSON at configure time, you must read them through a Gradle **`ValueSource`** or the
config cache can serve stale data — avoided entirely here.)

---

## Step 3 — The catalog API (`StoreCatalogExtension.kt`)

The object your build scripts will call.

`build-logic/src/main/kotlin/com/example/gradle/StoreCatalogExtension.kt`
```kotlin
package com.example.gradle

open class StoreCatalogExtension(private val storeDefs: List<StoreDef>) {

    val selectedStore: String get() = SELECTED_STORE               // default when -Pstore not passed
    val storeNames: Set<String> get() = storeDefs.map { it.name }.toSet()

    fun stores(): Map<String, List<String>> = storeDefs.associate { it.name to it.features }
    fun featuresFor(store: String): List<String> = def(store).features
    fun businessUnitDefaults(store: String): String = def(store).businessUnitDefaults           // optional
    fun applicationId(store: String): String =                                                  // optional
        "$BASE_APPLICATION_ID.${def(store).name.lowercase()}"

    private fun def(store: String): StoreDef =
        storeDefs.firstOrNull { it.name == store }
            ?: error("Unknown store '$store'. Known stores: $storeNames")

    companion object {
        const val SELECTED_STORE = "storeA"                 // ← your default store
        const val BASE_APPLICATION_ID = "com.example.app"   // ← your app's base package (optional)
    }
}
```
> Keep the class `open` — Gradle decorates extensions and needs to subclass it. Only expose the methods
> your build scripts actually call (drop `applicationId`/`businessUnitDefaults` if you don't need them).

---

## Step 4 — The catalog plugin (`StoreCatalogPlugin.kt`)

Registers the extension and **fails fast** if a store lists a feature with no matching module.

`build-logic/src/main/kotlin/com/example/gradle/StoreCatalogPlugin.kt`
```kotlin
package com.example.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class StoreCatalogPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target.extensions.findByName("storeCatalog") != null) return   // idempotent

        STORES.forEach { store ->
            store.features.forEach { feature ->
                require(target.rootProject.findProject(":features:$feature:real") != null) {
                    "Store '${store.name}' lists feature '$feature' but there is no module " +
                        ":features:$feature:real. Fix STORES in build-logic/Stores.kt."
                }
            }
        }

        target.extensions.create("storeCatalog", StoreCatalogExtension::class.java, STORES)
    }
}
```
> Adapt `":features:$feature:real"` to **your** module path convention (check your
> `settings.gradle.kts` `include(...)` lines — colons vs dashes matter).

---

## Step 5 — The Android convention plugin (`StoreFeaturesPlugin.kt`)

Turns the catalog into **product flavors** and **per-flavor feature dependencies**.

`build-logic/src/main/kotlin/com/example/gradle/StoreFeaturesPlugin.kt`
```kotlin
package com.example.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class StoreFeaturesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(StoreCatalogPlugin::class.java)
        val catalog = target.extensions.getByType(StoreCatalogExtension::class.java)

        target.pluginManager.withPlugin("com.android.application") {   // wait for AGP
            val android = target.extensions.getByType(ApplicationExtension::class.java)

            android.flavorDimensions += "store"
            catalog.stores().keys.forEach { store ->
                android.productFlavors.create(store) {
                    dimension = "store"
                    applicationId = catalog.applicationId(store)                 // optional
                    buildConfigField("String", "BUSINESS_UNIT_DEFAULTS",         // optional
                        "\"${catalog.businessUnitDefaults(store)}\"")
                }
            }

            // Flavor configurations (storeAImplementation, …) are created as AGP processes the flavors;
            // add the feature :real modules after evaluation so those configurations exist.
            target.afterEvaluate {
                catalog.stores().forEach { (store, features) ->
                    features.forEach { feature ->
                        target.dependencies.add(
                            "${store}Implementation",
                            target.project(":features:$feature:real"),
                        )
                    }
                }
            }
        }
    }
}
```
Two portability-critical details:
- **`withPlugin("com.android.application") { … }`** — don't assume AGP is applied; run inside this so
  the `android { }` extension exists.
- **`afterEvaluate { … }` for dependencies** — the `<flavor>Implementation` buckets don't exist until
  AGP has processed the flavors; add deps after evaluation.
- If you use `buildConfigField`, enable it in the app: `android { buildFeatures { buildConfig = true } }`.

---

## Step 6 — Apply on Android

In `androidApp/build.gradle.kts`, add the plugin id (keep your existing plugins):
```kotlin
plugins {
    id("com.android.application")
    // …your other plugins…
    id("com.example.store-features")
}
```
Delete any old inline flavor/feature-dependency loops — the plugin now owns them. Product flavors
`storeADebug`, `storeBDebug`, … appear automatically (Build Variants panel or `assembleStoreADebug`).

---

## Step 7 — Apply on iOS **(optional — only if you ship an iOS framework)**

In `shared/build.gradle.kts`:
```kotlin
plugins {
    // …kotlin multiplatform etc…
    id("com.example.store-catalog")
}

val storeCatalog = extensions.getByType<com.example.gradle.StoreCatalogExtension>()
val store = providers.gradleProperty("store").getOrElse(storeCatalog.selectedStore) // -Pstore=storeB
val storeFeatures = storeCatalog.featuresFor(store)

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { it.binaries.framework {
        baseName = "Shared"
        storeFeatures.forEach { export(project(":features:$it:api")) }   // export contracts to Swift
    } }
    sourceSets {
        iosMain.dependencies {
            storeFeatures.forEach {
                api(project(":features:$it:api"))            // exported contract
                implementation(project(":features:$it:real")) // impl, linked not exported
            }
        }
    }
}
```
On iOS there are no Gradle flavors, so the store is chosen with **`-Pstore=<store>`** (default =
`selectedStore`). Only that store's feature modules are linked/exported into the single framework —
build-time exclusion, same as Android.

---

## Step 8 — Verify

```bash
./gradlew help                                                   # project configures, build-logic compiles
./gradlew :androidApp:assembleStoreADebug                        # a flavor fully assembles
./gradlew :androidApp:dependencies \
    --configuration storeBDebugRuntimeClasspath | grep features   # storeB shows ONLY its features
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Pstore=storeB   # (iOS) store selection works
```
The discriminating check is the **dependencies** one: build a *leaner* store and confirm the features it
doesn't list are **absent** from its classpath. If every store shows every feature, your `afterEvaluate`
dependency loop or the flavor configuration name (`${store}Implementation`) is wrong.

---

## Adding a store or feature later

- **New store:** add one `StoreDef` line to `STORES`. Android gets a new flavor; `-Pstore=<new>` works on
  iOS. (No other build edits.)
- **New feature:** create `:features:<name>:api` + `:real`, `include(...)` them in `settings.gradle.kts`,
  then add the bare name to a store's `features` list. Validation (Step 4) fails fast on a typo.

---

## Troubleshooting (gotchas we actually hit)

1. **`Unclosed comment` compiling build-logic.** Kotlin allows *nested* block comments, so a `/*`
   sequence inside a `/** … */` KDoc (e.g. writing a glob like `dir/*.ext`) opens a nested comment and
   the closing `*/` closes the wrong one. Don't put `/*` inside block comments.
2. **`'*Implementation' configuration not found`.** You added feature deps too early. Add them inside
   `afterEvaluate { }` (Step 5), not directly in the `withPlugin` block.
3. **Plugin id not found / `includeBuild` ignored.** `includeBuild("build-logic")` must be inside
   `pluginManagement { }` in the **root** `settings.gradle.kts`, and the ids in `gradlePlugin { register }`
   must match the ids you apply.
4. **`Extension of type 'StoreCatalogExtension' does not exist`** in `shared`/root scripts. Make sure the
   script `apply`s `id("com.example.store-catalog")` *before* it calls `extensions.getByType<…>()` (the
   `plugins { }` block runs first, so this just means: apply it, don't only reference the class).
5. **(If you scaffold files from an applied `apply(from = …)` script)** reading catalog data into
   *script-level* `val`s makes a task's `doLast` capture the whole script object → config-cache error.
   Read the values **inside** the `tasks.register { }` block so `doLast` closes over plain values only.
6. **Config cache serves stale data after editing config.** Only a risk if you read *external files* at
   configure time — use a `ValueSource` then. With the Kotlin `STORES` table there's nothing to track.

---

## Adapt checklist (rename these for your project)

- [ ] Plugin package `com.example.gradle` → yours
- [ ] Plugin ids `com.example.store-catalog` / `com.example.store-features` → yours
- [ ] `BASE_APPLICATION_ID` and `SELECTED_STORE` in `StoreCatalogExtension`
- [ ] Module path `":features:$feature:real"` (and `:api`) → your convention
- [ ] The `STORES` list → your real stores and feature names
- [ ] Drop `applicationId` / `businessUnitDefaults` / the whole iOS step if you don't need them
- [ ] `agp` version entry present in `gradle/libs.versions.toml`

---

## One-paragraph recap

Put your store→feature table in a typed Kotlin list (`STORES`) inside an included `build-logic` build.
A `store-catalog` plugin exposes it as a `storeCatalog` extension and validates feature names; a
`store-features` plugin turns it into Android product flavors that each link only their store's feature
`:real` modules (build-time exclusion). Apply `store-features` in the Android app; on iOS, apply
`store-catalog` in the shared module and select the store with `-Pstore`. Adding a store or feature is a
one-line edit, checked by the compiler, with no external config files to keep in sync.
