# The store convention plugin — full build walkthrough, from zero

This is the **complete implementation guide** for how per-store feature wiring is built in this
project: what every file does, what every function does, and how the same catalog drives both
**Android** and **iOS**. Beginner-friendly — it assumes no Gradle plugin experience. If you only want
the *why*, read `docs/CONVENTION_PLUGINS_FROM_ZERO.md` first; this doc is the *how*, file by file.

---

## 0. The goal in one sentence

> We have several **stores** (storeA, storeB, …), each shipping a **different set of features**. We
> need each store's build to **physically exclude** the features it doesn't ship — decided at build
> time, from one source of truth (a **typed Kotlin table**, `STORES` in `Stores.kt`) — in a way that
> is stable, simple, and compiler-checked. We do this with two small **Gradle convention plugins**
> living in an included build called `build-logic`.

---

## 1. The map — every moving part

```
build-logic/                       ← an "included build": produces plugins, not app code
  Stores.kt                        → STORES: the typed list of stores (SOURCE OF TRUTH)
  StoreCatalogExtension.kt         → the `storeCatalog` object build scripts call
  StoreCatalogPlugin.kt            → registers `storeCatalog`, validates feature names
  StoreFeaturesPlugin.kt           → Android: makes flavors + links each store's features
         │  applied by
         ▼
settings.gradle.kts                → includeBuild("build-logic")
build.gradle.kts (root)            → id("com.isharaw.store-catalog")  (feeds the iOS script)
androidApp/build.gradle.kts        → id("com.isharaw.store-features") (Android flavors)
shared/build.gradle.kts            → id("com.isharaw.store-catalog")  (iOS framework)
gradle/generate-ios-store.gradle.kts → reads catalog data to scaffold iOS xcconfig/scheme
```

Two words you need:
- **Included build** — a *separate* little Gradle build (`build-logic`) whose job is to produce
  plugins the main build uses. Editing it does **not** reconfigure the whole project (that was the
  problem with the old `buildSrc`).
- **Convention plugin** — a plugin *we* wrote that bundles reusable build setup. A module "uses" it
  with `plugins { id("…") }`.

> **Note on history:** this used to read `config/stores/*.properties` files through a Gradle
> `ValueSource`. We replaced that with the `STORES` Kotlin table — simpler, compiler-checked, and with
> no config-cache file-tracking to worry about.

---

## 2. `build-logic` — the included build itself

### `build-logic/settings.gradle.kts`
Every Gradle build needs a settings file. The important line re-uses the app's version catalog:
```kotlin
dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
```
so `build-logic` reads the **same** `libs.versions.toml` the app uses — the Android Gradle Plugin
(AGP) version can't drift between the two.

### `build-logic/build.gradle.kts`
```kotlin
plugins { `kotlin-dsl` }                 // lets us write Gradle plugins in Kotlin
dependencies {
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")  // the AGP API
}
gradlePlugin {
    plugins {
        register("storeCatalog")  { id = "com.isharaw.store-catalog";  implementationClass = "com.isharaw.gradle.StoreCatalogPlugin" }
        register("storeFeatures") { id = "com.isharaw.store-features"; implementationClass = "com.isharaw.gradle.StoreFeaturesPlugin" }
    }
}
```
- `` `kotlin-dsl` `` — turns this project into a place where Kotlin files become Gradle plugins.
- The AGP dependency exists **only** so `StoreFeaturesPlugin` can call Android's DSL (create product
  flavors). Without it, the type `ApplicationExtension` wouldn't exist.
- `gradlePlugin { register(...) }` maps a **plugin id** (what you type in `plugins { id("…") }`) to the
  **Kotlin class** that implements it.

---

## 3. The source of truth — `Stores.kt`

This is the whole "config": a plain, typed Kotlin list.
```kotlin
data class StoreDef(
    val name: String,                       // flavor name + applicationId suffix
    val features: List<String>,             // bare feature names → :features:<name>:real modules
    val businessUnitDefaults: String = "",  // surfaced to the app as BuildConfig.BUSINESS_UNIT_DEFAULTS
)

internal val STORES: List<StoreDef> = listOf(
    StoreDef("storeA", listOf("login","cart","invoices","settings","orders","rebate","passwordReset"), "KEELS:USBL,CARGILLS:SENM"),
    StoreDef("storeB", listOf("login","cart","settings","rebate"), "KEELS:USBL"),
    StoreDef("storeC", listOf("login","cart","settings","orders"), "KEELS:USBL"),
    StoreDef("storeD", listOf("login","cart","settings","orders"), "KEELS:USBL"),
)
```
**Why a Kotlin table instead of `.properties` files?** Three reasons, all serving "risk-free and
simple":
1. **Compiler-checked** — no stringly-typed keys; a typo in a field name won't compile.
2. **No file reading** — nothing to parse, no `ValueSource`, no external input for the configuration
   cache to (mis)track. Editing `Stores.kt` recompiles `build-logic`, and Gradle invalidates the cache
   **automatically**.
3. **One place, both platforms** — adding a store is adding one `StoreDef` line; Android and iOS both
   pick it up.

---

## 4. The catalog API — `StoreCatalogExtension.kt`

The object build scripts call — the **public menu** of the catalog:
```kotlin
open class StoreCatalogExtension(private val storeDefs: List<StoreDef>) {
    val selectedStore: String get() = SELECTED_STORE                  // default store ("storeA")
    val storeNames: Set<String> get() = storeDefs.map { it.name }.toSet()

    fun stores(): Map<String, List<String>>                          // store -> its feature list
    fun featuresFor(store: String): List<String>                     // one store's features
    fun businessUnitDefaults(store: String): String
    fun applicationId(store: String): String                         // com.isharaw.kmpproj.<store>
    // companion: SELECTED_STORE = "storeA", BASE_APPLICATION_ID = "com.isharaw.kmpproj"
}
```
Each function is a **plain list lookup** over `storeDefs`. `applicationId("storeB")` →
`com.isharaw.kmpproj.storeb`. Ask for a store that doesn't exist and `def(store)` throws a clear
"Unknown store" error.

---

## 5. Wiring the catalog in — `StoreCatalogPlugin.kt`

```kotlin
class StoreCatalogPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target.extensions.findByName("storeCatalog") != null) return          // 1. idempotent

        STORES.forEach { store ->                                                 // 2. validate
            store.features.forEach { feature ->
                require(target.rootProject.findProject(":features:$feature:real") != null) {
                    "Store '${store.name}' lists feature '$feature', but there is no module " +
                        ":features:$feature:real. Fix the STORES table in build-logic (Stores.kt)."
                }
            }
        }

        target.extensions.create("storeCatalog", StoreCatalogExtension::class.java, STORES)  // 3. expose
    }
}
```
1. **Idempotent** — if two plugins both need the catalog, register it only once.
2. **Fail-fast validation** — every feature a store lists must have a real `:features:<name>:real`
   module. A typo like `rebatte` fails here with a clear message, instead of a confusing "project not
   found" deep in the build.
3. Register the `storeCatalog` extension so build scripts can call it.

No file I/O, no `ValueSource` — the data is `STORES`, already compiled in.

---

## 6. Making Android strip features — `StoreFeaturesPlugin.kt`

This is the Android-only convention plugin. It turns catalog data into **product flavors** and
**per-flavor dependencies**.
```kotlin
class StoreFeaturesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(StoreCatalogPlugin::class.java)                 // ensure catalog
        val catalog = target.extensions.getByType(StoreCatalogExtension::class.java)

        target.pluginManager.withPlugin("com.android.application") {               // wait for AGP
            val android = target.extensions.getByType(ApplicationExtension::class.java)

            android.flavorDimensions += "store"
            catalog.stores().keys.forEach { store ->
                android.productFlavors.create(store) {                             // one flavor / store
                    dimension = "store"
                    applicationId = catalog.applicationId(store)
                    buildConfigField("String", "BUSINESS_UNIT_DEFAULTS",
                        "\"${catalog.businessUnitDefaults(store)}\"")
                }
            }

            target.afterEvaluate {                                                 // deps, once flavors exist
                catalog.stores().forEach { (store, features) ->
                    features.forEach { feature ->
                        target.dependencies.add(
                            "${store}Implementation",                              // storeAImplementation, …
                            target.project(":features:$feature:real"),
                        )
                    }
                }
            }
        }
    }
}
```
Two subtleties:
- **`withPlugin("com.android.application") { … }`** — runs our code *once the Android plugin is
  present*, so `ApplicationExtension` (the `android { }` block) is guaranteed to exist.
- **`afterEvaluate { … }` for dependencies** — AGP creates the flavor-specific dependency buckets
  (`storeAImplementation`, …) as it processes the flavors. We add each store's feature `:real` modules
  *after* the project is evaluated so those buckets exist.

---

## 7. How the consumers use it

- **`settings.gradle.kts`** — `pluginManagement { includeBuild("build-logic") }`. Makes the two plugin
  ids resolvable.
- **root `build.gradle.kts`** — applies `id("com.isharaw.store-catalog")`, then exposes plain data for
  the iOS script:
  ```kotlin
  val storeCatalog = extensions.getByType<com.isharaw.gradle.StoreCatalogExtension>()
  extra["storeFeaturesByStore"] = storeCatalog.stores()
  extra["storeAppIds"] = storeCatalog.storeNames.associateWith { storeCatalog.applicationId(it) }
  ```
- **`androidApp/build.gradle.kts`** — `id("com.isharaw.store-features")`. One line replaced the old
  flavor loop *and* dependency loop.
- **`shared/build.gradle.kts`** — `id("com.isharaw.store-catalog")`, then
  `val storeFeatures = storeCatalog.featuresFor(store)`.
- **`gradle/generate-ios-store.gradle.kts`** — reads the `extra` maps to scaffold the iOS xcconfig +
  scheme (needs no `build-logic` types, just plain maps).

---

## 8. How it works on **Android**

1. `StoreFeaturesPlugin` creates one **product flavor** per store: `storeA`…`storeD`. With build types
   you get `storeADebug`, `storeBRelease`, etc. — pick one in Android Studio's **Build Variants** panel
   or build `:androidApp:assembleStoreCDebug`.
2. For each flavor it adds only that store's features via the flavor-specific configuration:
   `storeCImplementation(project(":features:orders:real"))`. A module added to `storeCImplementation`
   is compiled **only** into the storeC variant.
3. Result — **build-time exclusion**. storeC ships `login,cart,settings,orders`, so its APK contains
   exactly those `:real` modules; `rebate`/`invoices`/`passwordReset` aren't compiled in at all.
   (Verify: `./gradlew :androidApp:dependencies --configuration storeCDebugRuntimeClasspath`.)
4. Present features register themselves into the app's Metro DI graph
   (`@ContributesBinding` / `@IntoSet`), so the running app only knows what its flavor shipped.

> **One-line Android model:** flavor = store; `storeXImplementation` = "compile this only into store X".

---

## 9. How it works on **iOS**

iOS builds a single **Kotlin Multiplatform framework** (`Shared.framework`) that SwiftUI consumes.
There are no Gradle flavors, so the store is chosen differently:

1. **Which store?** `shared/build.gradle.kts` reads
   `val store = providers.gradleProperty("store").getOrElse(storeCatalog.selectedStore)` — so the store
   comes from `-Pstore=storeB`, defaulting to `selectedStore` (`storeA`). Xcode passes it via
   `GRADLE_STORE` in the generated xcconfig.
2. **Which features compile/export?** A loop over that store's features:
   ```kotlin
   val storeFeatures = storeCatalog.featuresFor(store)
   storeFeatures.forEach {
       api(project(":features:$it:api"))            // contract, EXPORTED to Swift
       implementation(project(":features:$it:real")) // impl, linked but NOT exported
   }
   ```
   Only the shipped features' modules link into the framework — build-time exclusion, same idea as
   Android, expressed through the iOS source set instead of a flavor.
3. **The DI graph** picks a source-set variant (`iosStoreBase` vs `iosStoreWithInvoices`) so it matches
   the store's features. (`docs/SHARED_WIRING_FROM_ZERO.md` explains how to make this variant-free.)
4. **The xcconfig + scheme** per store come from `generateIosStore`, using the same catalog data, so
   Xcode's per-store bundle id lines up with Gradle.
5. Verify: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Pstore=storeB`.

**Android vs iOS at a glance:**

| | Android | iOS |
|---|---|---|
| Store selected by | product flavor (build variant) | `-Pstore=<store>` Gradle property |
| Features linked via | `${store}Implementation(...)` per flavor | `api`/`implementation` loop in `iosMain` |
| Build-time exclusion | ✅ per flavor | ✅ per framework build |
| Same source of truth | `STORES` table via `storeCatalog` | same |

---

## 10. Two gotchas we hit (and why)

1. **Kotlin nested block comments.** `*.properties`-style text with `/*` inside a `/** … */` KDoc
   *opens a nested comment* (Kotlin allows nesting), so the closing `*/` closes the wrong one. Don't put
   `/*` in block comments.
2. **Config-cache script capture.** In the applied `generate-ios-store.gradle.kts`, reading the catalog
   into *script-level* `val`s made the task's `doLast` capture the whole script object (unserializable).
   Fix: read the maps **inside** the `tasks.register { }` block so `doLast` closes over plain values only.

---

## 11. Practical recipes

**Add a new store (e.g. storeE):**
1. Add one line to `STORES` in `build-logic/.../Stores.kt`:
   `StoreDef("storeE", listOf("login","cart","settings"), "KEELS:USBL")`.
2. That's it for Gradle — Android gets a `storeE` flavor automatically, and `-Pstore=storeE` works on
   iOS. (iOS also needs `./gradlew generateIosStore -Pstore=storeE` + the manual Xcode config step from
   `docs/IOS_FLAVORS.md`.)

**Add a new feature to a store:**
1. Create the `:features:<name>:api` + `:real` modules and `include(...)` them in `settings.gradle.kts`.
2. Add the bare name to that store's `features` list in `StoreDef`. Validation in `StoreCatalogPlugin`
   tells you immediately if the name doesn't match a real module.

---

## 12. How to verify it all works

```bash
./gradlew help                                          # whole project configures
./gradlew :androidApp:assembleStoreCDebug               # a leaner flavor fully assembles
./gradlew :androidApp:dependencies \
    --configuration storeCDebugRuntimeClasspath | grep features   # only storeC's features present
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Pstore=storeB   # iOS store selection
./gradlew generateIosStore -Pstore=storeA               # iOS scaffolding
# edit STORES in Stores.kt and re-run → build-logic recompiles, cache invalidates automatically
```

---

## 13. One-paragraph recap

Each store's features live in a typed Kotlin table, `STORES` in `build-logic/.../Stores.kt` — the
single source of truth. `StoreCatalogPlugin` exposes it as a `storeCatalog` extension and **validates**
feature names. `StoreFeaturesPlugin` turns the catalog into Android **product flavors**, each linking
only its store's feature `:real` modules — so unshipped features are compiled out. On iOS,
`shared/build.gradle.kts` reads the same catalog, and a `-Pstore` property selects which store's feature
modules the single KMP framework compiles and exports. Both platforms strip features at **build time**
from **one compiler-checked source of truth**, with no external config files and no stale-config
footgun — the whole reason we moved off `buildSrc`, made even simpler by dropping the `.properties` layer.
