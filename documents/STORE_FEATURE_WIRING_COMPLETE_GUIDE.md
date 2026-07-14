# Per-store build-time feature wiring in a KMP project — complete guide

A **generic, copy-and-adapt** guide for building a store→feature build system in any Kotlin
Multiplatform project. Goal: each *store* (product variant / white-label app) compiles in **only** the
feature modules it ships — decided at build time from one typed source of truth — and each compiled-in
feature **registers itself** at runtime through compile-time DI, so the app can enumerate exactly what
it shipped with no hand-maintained list.

**Assumed project shape: shared *logic*, native *UI*.** The shared KMP module holds business logic
only; Android UI is native Jetpack Compose in the Android app module, iOS UI is native SwiftUI in the
Xcode project. Nothing UI is shared. This assumption shapes two design decisions you'll meet below:
the feature identity contract carries **data, not UI**, and there is **one DI graph per platform**.

> Replace every `com.example…` placeholder with your own package/ids. Store names (`storeA`,
> `storeB`, …) and feature names (`login`, `cart`, `orders`, …) are illustrative — use your own.
> Anything marked **(optional)** can be skipped for a minimal setup.

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
  core/feature/                    → HomeFeature + AppScope (the cross-feature identity contract)
  features/<name>/api + real       → one module pair per feature; :real self-registers via Metro
```

The system has two halves that meet in the middle:

- **Part I (build time):** convention plugins in `build-logic` turn a typed `STORES` table into Android
  product flavors and per-store dependency wiring, so a store's build *never sees* the feature modules
  it doesn't ship.
- **Part II (compile/runtime):** every feature `:real` module carries a Metro `@ContributesIntoSet`
  annotation; a per-platform `AppGraph` aggregates whatever contributions are on its classpath — which,
  because of Part I, is exactly the store's shipped features — into one `Set<HomeFeature>` the native
  UI reads.

### Prerequisites / assumptions

- A KMP project with an **Android application module** and (optionally) a **shared KMP module** that
  produces an iOS framework consumed by a native SwiftUI app.
- Features are split into per-feature Gradle modules, e.g. `:features:<name>:api` and
  `:features:<name>:real` (the plugin adds the `:real` modules per store).
- A Gradle **version catalog** at `gradle/libs.versions.toml` with an `agp` version entry.
- **Type-safe project accessors** are nice-to-have but not required.

---

# Part I — Build-time wiring: which modules each store compiles

## Step 1 — Create the `build-logic` included build

An **included build** is a separate little Gradle build whose only job is to produce plugins. Editing
it does **not** reconfigure your whole project (that's the advantage over `buildSrc`).

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

/**
 * Every feature that can ship in a store. An enum, not strings, so the compiler owns
 * feature-name correctness: a typo can't exist in the STORES table, and the IDE
 * autocompletes/refactors the names. Module paths are derived from the constant name.
 */
enum class Feature {
    LOGIN, CART, ORDERS, SETTINGS;

    /** ORDERS -> "orders" — the bare name used in module paths. */
    val moduleName: String get() = name.lowercase()
    val apiModulePath: String get() = ":features:$moduleName:api"
    val realModulePath: String get() = ":features:$moduleName:real"
}

/**
 * Every store, as an enum — the single source of truth for what each store ships.
 * Add a store = add one constant. The string form Gradle/Xcode need (flavor name,
 * `-Pstore=` value, applicationId suffix) is derived once in [storeName].
 */
enum class Store(
    val features: List<Feature>,            // compiler-checked — see Feature
    val businessUnitDefaults: String = "",  // (optional) any per-store string for BuildConfig
) {
    STORE_A(listOf(Feature.LOGIN, Feature.CART, Feature.ORDERS)),
    STORE_B(listOf(Feature.LOGIN, Feature.CART, Feature.SETTINGS)),
    STORE_C(listOf(Feature.LOGIN, Feature.ORDERS)),
    ;

    /** STORE_A -> "storeA" — the Android flavor name, `-Pstore=` value, and appId suffix. */
    val storeName: String = name.split('_')
        .mapIndexed { i, part ->
            if (i == 0) part.lowercase() else part.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
        .joinToString("")

    companion object {
        /** The default store when `-Pstore=<store>` is not passed (used by iOS). */
        val DEFAULT: Store = STORE_A

        /** "storeB" (a `-Pstore=` value) -> [STORE_B], with a clear error for unknown names. */
        fun byName(name: String): Store =
            entries.firstOrNull { it.storeName == name }
                ?: error("Unknown store '$name'. Known stores: ${entries.map { it.storeName }}")
    }
}
```
**Why a Kotlin table?** It's compiler-checked, needs no file parsing, and editing it recompiles
`build-logic` so Gradle's configuration cache invalidates automatically. (If you instead read external
`.properties`/JSON at configure time, you must read them through a Gradle **`ValueSource`** or the
config cache can serve stale data — avoided entirely here.)

**Why enums (not strings) for both stores and features?** With strings, a typo like `"ordres"` or
`"stroeB"` is only caught by runtime validation — at best. With enums, the typo **cannot be written**:
the table only accepts declared constants, the IDE autocompletes them, and renaming is a
compiler-checked refactor. The enums also centralize every derived string in one place — the
feature→module-path convention (`apiModulePath`/`realModulePath`) and the store→flavor-name/`-Pstore`
convention (`storeName`) — instead of string-templating at every call site. Strings survive only at
the boundaries where Gradle/Xcode inherently speak text (flavor names, `-P` values), and each is
*derived from* an enum, never typed by hand twice. Note what enums can *not* guarantee: that a
feature's module actually exists on disk and is `include()`d — that stays with the plugin validation
in Step 4.

> **This enum is build-time only — it is not `HomeFeature` (Part II).** The enum lives on Gradle's
> classpath and decides which modules to link; `HomeFeature` lives in `:core:feature`, ships in the
> app, and is how linked features self-register at runtime. They can't share a type (`build-logic`
> compiles before the app's modules exist) and don't need to: the build itself keeps them in sync.

---

## Step 3 — The catalog API (`StoreCatalogExtension.kt`)

The object your build scripts will call.

`build-logic/src/main/kotlin/com/example/gradle/StoreCatalogExtension.kt`
```kotlin
package com.example.gradle

open class StoreCatalogExtension {

    val selectedStore: Store get() = Store.DEFAULT                 // default when -Pstore not passed
    val storeNames: Set<String> get() = Store.entries.map { it.storeName }.toSet()

    /** Every store, in declaration order. */
    fun stores(): List<Store> = Store.entries

    /** "-Pstore=storeB" -> Store.STORE_B — the one string→enum boundary. */
    fun storeFor(name: String): Store = Store.byName(name)

    fun applicationId(store: Store): String =                                       // optional
        "$BASE_APPLICATION_ID.${store.storeName.lowercase()}"

    companion object {
        const val BASE_APPLICATION_ID = "com.example.app"   // ← your app's base package (optional)
    }
}
```
> Keep the class `open` — Gradle decorates extensions and needs to subclass it. The data itself lives
> on the `Store` enum; this is just the script-facing surface. Only expose the methods your build
> scripts actually call (drop `applicationId` if you don't need it).

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

        // Store and feature *names* are compiler-checked by the enums; the remaining failure mode
        // is a Feature constant whose module was never created or never include()d — catch it here.
        Store.entries.forEach { store ->
            store.features.forEach { feature ->
                require(target.rootProject.findProject(feature.realModulePath) != null) {
                    "Store '${store.storeName}' lists Feature.${feature.name} but there is no module " +
                        "${feature.realModulePath}. Create/include the module or remove the constant."
                }
            }
        }

        target.extensions.create("storeCatalog", StoreCatalogExtension::class.java)
    }
}
```
> Adapt the path convention in the `Feature` enum's `apiModulePath`/`realModulePath` to **your**
> module layout (check your `settings.gradle.kts` `include(...)` lines — colons vs dashes matter).

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
            catalog.stores().forEach { store ->
                android.productFlavors.create(store.storeName) {
                    dimension = "store"
                    applicationId = catalog.applicationId(store)                 // optional
                    buildConfigField("String", "BUSINESS_UNIT_DEFAULTS",         // optional
                        "\"${store.businessUnitDefaults}\"")
                }
            }

            // Flavor configurations (storeAImplementation, …) are created as AGP processes the flavors;
            // add the feature :real modules after evaluation so those configurations exist.
            target.afterEvaluate {
                catalog.stores().forEach { store ->
                    store.features.forEach { feature ->
                        target.dependencies.add(
                            "${store.storeName}Implementation",
                            target.project(feature.realModulePath),
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
// The -Pstore value is the one inherently-string boundary; storeFor() converts it to the typed
// Store once (failing fast with the known names on a typo). orNull + isNotBlank so an empty
// `-Pstore=` (e.g. an unset env var from Xcode) falls back to the default instead of failing.
val store = providers.gradleProperty("store").orNull?.takeIf { it.isNotBlank() }
    ?.let { storeCatalog.storeFor(it) } ?: storeCatalog.selectedStore
val storeFeatures = store.features

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { it.binaries.framework {
        baseName = "Shared"
        storeFeatures.forEach { export(project(it.apiModulePath)) }   // export contracts to Swift
    } }
    sourceSets {
        iosMain.dependencies {
            storeFeatures.forEach {
                api(project(it.apiModulePath))             // exported contract
                implementation(project(it.realModulePath)) // impl, linked not exported
            }
        }
    }
}
```
On iOS there are no Gradle flavors, so the store is chosen with **`-Pstore=<store>`** (default =
`selectedStore`). Only that store's feature modules are linked/exported into the single framework —
build-time exclusion, same as Android. (From Xcode, pass the store through an xcconfig variable into
the Gradle invocation of your framework-embedding build phase.)

---

## Part I checkpoint — verify the build wiring

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

# Part II — Feature identity & self-registration with Metro DI

Part I makes each store **compile in only its own feature modules**. It deliberately says nothing about
how a compiled-in feature becomes visible to the running app. This part fills that gap: how each
feature **identifies and registers itself** so the app can enumerate exactly the features its store
shipped — using **Metro** compile-time DI (`@ContributesIntoSet`).

## The idea in one sentence

> Each feature `:real` module says "**I am a feature**" once, with an annotation
> (`@ContributesIntoSet`). A per-platform **`AppGraph`** collects every such declaration **on its
> compile classpath** — which, because of per-store linking, is exactly the store's shipped features —
> into a single `Set<HomeFeature>` the UI reads. No hand-maintained list; add/remove a feature and the
> set changes automatically.

**Why DI instead of a string list?** Part I already decides *which modules compile*. A
`BuildConfig`/generated-file string list of feature names is a *second, parallel* source of the same
truth that you must keep in sync. Letting features **self-register** makes the compiled set itself the
single source — a typo or a dropped module can't desync it.

---

## Anatomy of a feature — what "a feature" actually is here

A "feature" isn't a single class. It's a small **module pair** plus a shared **identity**, each with a
distinct job (using `orders` as the running example):

```
:features:orders:api    → OrdersFeature        the feature's BEHAVIOUR contract (what Orders can do)
:features:orders:real   → OrdersFeatureImpl    the IMPLEMENTATION + self-registration
:core:feature           → HomeFeature          the cross-feature IDENTITY (how the app lists any feature)
```

Three layers, three reasons:

1. **`:api` — the behaviour contract.** A tiny interface describing *what the feature does*
   (`OrdersFeature`). It's the stable, public face: other modules and Swift depend on this, never on the
   implementation. On iOS it's `export`ed into the framework so Swift can see the type. Changing the
   *impl* never breaks callers as long as the contract holds.
2. **`:real` — the implementation.** The actual code. It is the module that gets **linked per store**
   (Android flavor dependency / iOS `-Pstore` loop from Part I). Because only shipping stores link it,
   its very presence in a build *is* the store's decision to ship the feature. This is also the module
   that **self-registers** (Step 9).
3. **`HomeFeature` — the identity.** The one thing *every* feature has in common: an `id` and a display
   `title`. It lives in `:core:feature` so all features and both app graphs can share it. This is what
   the home screen enumerates — it lets the app treat every feature uniformly without knowing each
   feature's specific type.

### Why identity is separate from behaviour

The home screen doesn't care what a feature *does* — it only needs to **list** it (id, title,
shipped?). If listing were tied to each feature's own `:api` interface, the app would have to know
about `OrdersFeature`, `CartFeature`, `SettingsFeature`, … individually — defeating the point.
`HomeFeature` is the **common denominator** that makes "enumerate the shipped features" a single,
type-uniform operation.

| | `HomeFeature` (identity) | `<Name>Feature` (`:api`, behaviour) |
|---|---|---|
| Lives in | `:core:feature` (shared by all) | each `:features:<name>:api` |
| Answers | "list me on the home screen" | "here's what THIS feature does" |
| Shape | `id`, `title` | feature-specific (e.g. `OrdersFeature.placeOrder()`) |
| Read by | the app graph → home tiles | code that actually invokes the feature |
| To Swift | via a Kotlin `object` façade | via `export(:api)` in the framework |

> A feature can implement **both** once it grows real behaviour
> (`class OrdersFeatureImpl : HomeFeature, OrdersFeature`); with two supertypes you must then name the
> `@ContributesIntoSet` binding explicitly so Metro knows it's contributing as `HomeFeature`.

> **Opting out of a surface.** A feature can be shipped (linked) yet absent from a particular UI
> surface. A `login` feature, for instance, is a real per-store module like the others, but it isn't a
> home *tile* — so its `:real` simply doesn't implement `HomeFeature` and never joins the set. A
> feature opts **in** to a listing by implementing that listing's identity interface; opting out is
> just not implementing it.

---

## The life of a feature — end to end

Follow **orders** from a line in the catalog to a tile on the screen:

1. **Declared** in the source of truth: `StoreDef("storeC", listOf("login", "orders"))` — storeC ships
   orders; storeB does not.
2. **Linked** at build time: Part I's plugin adds `:features:orders:real` to a build **only** for
   stores that list it (Android: `storeCImplementation(...)`; iOS: the `-Pstore` loop). storeB's build
   never sees the module.
3. **Self-registers**: `OrdersFeatureImpl` carries `@ContributesIntoSet(AppScope::class)` — a fact
   compiled into its klib. No central list is touched.
4. **Aggregated**: when the app graph compiles, Metro scans the classpath, finds the orders
   contribution *only where the module was linked*, and generates
   `features = setOf(OrdersFeatureImpl(), …)`. For storeB it generates `emptySet()` (nothing
   contributed).
5. **Consumed**: the UI reads `graph.features`; the "Orders" tile is enabled for storeC and absent (or
   greyed) for storeB — which never linked orders, so it could never register.

The feature never appears in a list you maintain by hand — **its presence in the compiled set *is* the
registration.** Add a store to the catalog, or drop a feature from a store, and the set changes with no
other edit.

---

## Step 8 — The shared contract: what a feature's "identity" is

Create one small common module (KMP, all your targets) that both the feature `:real` modules and the
app graphs depend on. It holds two tiny things.

`:core:feature/src/commonMain/kotlin/com/example/core/FeatureId.kt`
```kotlin
package com.example.core

/**
 * The runtime identity of every feature, as a closed enum — the runtime counterpart of the
 * build-time Feature enum in build-logic (two worlds, deliberately separate types). Typed ids
 * mean tile↔screen dispatch can't be broken by a typo, on either platform.
 *
 * [title] lives here (not only on HomeFeature) because the home screens need a title even for
 * features NOT compiled into this store, to render the disabled tile. [isHomeTile] lets a
 * shipped-but-not-a-tile feature (an auth gate like login) opt out of the home grid.
 */
enum class FeatureId(val title: String, val isHomeTile: Boolean = true) {
    LOGIN("Login", isHomeTile = false),
    CART("Cart"),
    SETTINGS("Settings"),
    ORDERS("Orders"),
    ;

    companion object {
        /** Declaration-ordered home-grid tiles (the whole catalog, shipped or not). */
        val homeTiles: List<FeatureId> = entries.filter { it.isHomeTile }
    }
}
```

`:core:feature/src/commonMain/kotlin/com/example/core/HomeFeature.kt`
```kotlin
package com.example.core

/** The multibound feature "identity" — the minimum the app needs to list a feature. */
interface HomeFeature {
    val id: FeatureId
    val title: String get() = id.title
}
```

`:core:feature/src/commonMain/kotlin/com/example/core/AppScope.kt`
```kotlin
package com.example.core

/** DI scope marker. The per-platform AppGraph aggregates contributions to this scope. */
abstract class AppScope private constructor()
```

Notes:
- `HomeFeature` is intentionally **data, not UI** — with native UIs (Compose on Android, SwiftUI on
  iOS) the shared feature module can't carry a `@Composable`/`View`. Keep `id`/`title` (and any route
  metadata) here; each platform maps `id → its own native screen`.
- `AppScope` can be any class; Metro also ships `dev.zacsweers.metro.AppScope` if you'd rather not
  define your own. Defining your own avoids depending on a framework detail.
- This module needs **no** Metro plugin — it declares no Metro annotations.

---

## Step 9 — Make each feature self-register

Apply the Metro plugin to every feature `:real` module and have its implementation contribute itself.

`:features:<name>:real/build.gradle.kts` (add to the plugins + deps you already have)
```kotlin
plugins {
    // …kotlinMultiplatform, android KMP library…
    alias(libs.plugins.metro)               // dev.zacsweers.metro
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:feature"))
        }
    }
}
```

`:features:orders:real/…/OrdersFeatureImpl.kt`
```kotlin
package com.example.features.orders.real

import com.example.core.AppScope
import com.example.core.HomeFeature
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@ContributesIntoSet(AppScope::class)   // "add me to the Set<HomeFeature> for AppScope"
@Inject                                // Metro can construct me
class OrdersFeatureImpl : HomeFeature {
    override val id: FeatureId = FeatureId.ORDERS   // typed — a typo can't compile
}
```
- **Single supertype** (`HomeFeature`) → Metro infers the bound type implicitly. If your impl
  implements *two* interfaces, the bound type is ambiguous — either implement only `HomeFeature`, or
  name the binding explicitly.
- `@Inject` with a no-arg constructor is enough; if a feature needs dependencies, add constructor
  params and Metro provides them from the graph.

The contribution is a **build-time fact baked into the module's klib** — it only exists in the app when
the module is compiled in (i.e. when the store ships that feature). That's the whole trick.

---

## Step 10 — The app graph (one **per platform** — this is the key insight)

A Metro graph aggregates contributions **on the graph module's compile classpath**. Part I links
features differently on each platform:

| Platform | Where the `:real` modules are linked | So the graph must live in… |
|---|---|---|
| Android | the **app** module, per product flavor | `:androidApp` |
| iOS | the **shared** module's `iosMain`, per `-Pstore` | `shared/src/iosMain` |

No single graph sees both. **You define one `AppGraph` per platform.** They're identical in shape;
each aggregates the features visible in its own compilation. (This is also the natural consequence of
not sharing UI: each platform's app entry point owns its own graph.)

`:androidApp/…/AppGraph.kt`  and  `shared/src/iosMain/…/AppGraph.kt`
```kotlin
import com.example.core.AppScope
import com.example.core.HomeFeature
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.createGraph

@DependencyGraph(AppScope::class)
interface AppGraph {
    @Multibinds(allowEmpty = true)     // ← REQUIRED (see gotcha below)
    val features: Set<HomeFeature>
}

fun createAppGraph(): AppGraph = createGraph<AppGraph>()
```
Apply the Metro plugin to **both** the `:androidApp` module and the `shared` module (the graph
modules), and make each depend on `:core:feature`.

> **Gotcha — `@Multibinds(allowEmpty = true)`.** If a store ships **none** of the contributing
> features, the multibinding has no contributor and Metro fails at compile time with
> `[Metro/MissingBinding] … Set<HomeFeature>`. Declaring the accessor with
> `@Multibinds(allowEmpty = true)` lets it resolve to an empty set instead. This bites on a lean store
> and is easy to miss until then.

---

## Step 11 — Consume the set in the native UIs

**Android (Jetpack Compose):**
```kotlin
val enabled = createAppGraph().features.map { it.id }.toSet()
setContent { HomeScreen(enabled) }   // tile enabled = id in the DI set
```

**iOS (SwiftUI) — expose a Swift-friendly façade.** A raw `Set<HomeFeature>` bridges to Swift as
`NSSet`, which is awkward. Wrap the graph in a Kotlin `object` (bridges to `Foo.shared`) and expose
functions:
```kotlin
// shared/src/iosMain/…/AppGraph.kt (alongside the iOS AppGraph)
object HomeFeatures {
    private val features: Set<HomeFeature> by lazy { createGraph<AppGraph>().features }
    /** Every tile the home can show (whole catalog, shipped or not). */
    val homeTiles: List<FeatureId> get() = FeatureId.homeTiles
    fun isEnabled(id: FeatureId): Boolean = features.any { it.id == id }
}
```
A Kotlin enum bridges to Swift as a class with static entries (`FeatureId.cart`, `FeatureId.orders`,
…), so Swift shares the same typed ids and titles — no duplicated string/title list on the iOS side:
```swift
// ContentView.swift — the tile catalog comes from the shared enum
private let homeTiles: [FeatureId] = HomeFeatures.shared.homeTiles

private func isEnabled(_ id: FeatureId) -> Bool {
    HomeFeatures.shared.isEnabled(id: id)   // function interop is the robust path
}

// FeatureRoute.swift — typed mapping, no string matching
init?(featureId: FeatureId) {
    switch featureId {
    case .cart: self = .cart
    case .orders: self = .orders
    case .settings: self = .settings
    default: return nil        // required: a Kotlin enum bridges as a class, not a Swift enum
    }
}
```
Because `FeatureId` appears in the façade's public API, `:core:feature` must be **exported** into the
framework: `export(project(":core:feature"))` in the framework block, and the iosMain dependency
declared as `api(...)` (export requires an api dependency).

Because UI is native per platform, each platform maps `id → screen` itself: Android in its navigation
graph/composable registry, iOS in a Swift `switch` (or registry) over the ids. The shared code never
references a screen.

---

## Step 12 — Toolchain: Metro ↔ Kotlin version

Metro is a **Kotlin compiler plugin**, so its version must match your Kotlin version (a compiler plugin
built for one Kotlin release won't load on a mismatched one). Check Metro's compatibility table before
adding it, and pin the plugin version in your catalog:
```toml
[versions]
metro = "<version matching your Kotlin>"
[plugins]
metro = { id = "dev.zacsweers.metro", version.ref = "metro" }
```
Apply it to: every feature `:real` module (contributors) **and** each graph module (`:androidApp`,
`shared`). The `:core:feature` module doesn't need it. Metro resolves from Maven Central, so keep
`mavenCentral()` in `pluginManagement` repositories.

---

## Step 13 — Verify (the discriminating checks)

Compilation succeeding only proves the plugin loaded — it does **not** prove the set has the right
contents (an empty set also compiles). Inspect the generated graph per store:

**Android — read the generated graph bytecode:**
```bash
./gradlew :androidApp:assembleStore<X>Debug
javap -c -p <…>/AppGraph\$Impl.class | grep -A5 getSetOfHomeFeature
```
A store that ships the feature shows `new …OrdersFeatureImpl … setOf(…)`; a store that doesn't shows
`emptySet()` / omits it. Do this for a **lean** store and a **full** one — the difference is the proof.

**iOS — read the generated framework header:**
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Pstore=store<X>
grep -E "bindIntoSetAsHomeFeature|FeatureImpl" <…>/Shared.framework/Headers/Shared.h
```
A store shows only its own `…FeatureImpl` bindings; features it doesn't ship are absent from the
header entirely.

**Tests:** applying a compiler plugin affects **every** source set in that module, including tests. Run
them (e.g. `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`) so a plugin regression
doesn't ship silently.

---

## Step 14 — Replace the string list; don't run both

Once the DI set drives the UI, **remove** any earlier string-based mechanism for the same fact
(`BuildConfig.STORE_FEATURES`, a generated `StoreInfo`, etc.). Two live sources of "which features are
here" will eventually disagree. The Metro `Set<HomeFeature>` is now the single source on both
platforms.

---

# Reference

## Adding a store or feature later

- **New store:** add one constant to the `Store` enum. Android gets a new flavor; `-Pstore=<new>`
  works on iOS. (No other build edits.)
- **New feature:** create `:features:<name>:api` + `:real`, `include(...)` them in
  `settings.gradle.kts`, add the Metro annotation to its impl, add a constant to the `Feature` enum,
  then add `Feature.<NAME>` to a store's list. The compiler catches name typos; validation (Step 4)
  catches a constant whose module is missing.

---

## Common gotchas

**Build-time wiring (Part I):**

1. **`Unclosed comment` compiling build-logic.** Kotlin allows *nested* block comments, so a `/*`
   sequence inside a `/** … */` KDoc (e.g. writing a glob like `dir/*.ext`) opens a nested comment and
   the closing `*/` closes the wrong one. Don't put `/*` inside block comments.
2. **`'*Implementation' configuration not found`.** You added feature deps too early. Add them inside
   `afterEvaluate { }` (Step 5), not directly in the `withPlugin` block.
3. **Plugin id not found / `includeBuild` ignored.** `includeBuild("build-logic")` must be inside
   `pluginManagement { }` in the **root** `settings.gradle.kts`, and the ids in
   `gradlePlugin { register }` must match the ids you apply.
4. **`Extension of type 'StoreCatalogExtension' does not exist`** in `shared`/root scripts. Make sure
   the script applies `id("com.example.store-catalog")` *before* it calls `extensions.getByType<…>()`
   (the `plugins { }` block runs first, so this just means: apply it, don't only reference the class).
5. **Config-cache capture in scaffolding tasks.** If a task's `doLast` closes over script-level `val`s
   holding catalog data, it can capture the whole script object → config-cache error. Read the values
   **inside** the `tasks.register { }` block so `doLast` closes over plain values only.
6. **Config cache serves stale data after editing config.** Only a risk if you read *external files*
   at configure time — use a `ValueSource` then. With the Kotlin `STORES` table there's nothing to
   track.

**Feature registration (Part II):**

7. **`[Metro/MissingBinding] Set<HomeFeature>`** on a store shipping none of the features → add
   `@Multibinds(allowEmpty = true)` on the graph accessor (Step 10).
8. **Compiler-plugin/Kotlin mismatch** — the plugin won't load; check Metro's compat table and match
   versions. Verify with a trivial one-module smoke graph first.
9. **Ambiguous bound type** — an impl implementing two interfaces makes `@ContributesIntoSet`'s
   implicit binding ambiguous. Implement a single supertype (`HomeFeature`) or specify the binding.
10. **One graph won't cover both platforms** — because Android links features into the app and iOS
    into `shared`. Define the graph per platform (Step 10); don't try to share one.
11. **`Set` interop from Swift** — expose an `isEnabled(id:)`/`ids` façade instead of the raw
    `Set<HomeFeature>` (which imports as `NSSet`).

---

## Adapt checklist

**Part I — build wiring:**
- [ ] Plugin package `com.example.gradle` → yours
- [ ] Plugin ids `com.example.store-catalog` / `com.example.store-features` → yours
- [ ] `BASE_APPLICATION_ID` in `StoreCatalogExtension` and `Store.DEFAULT` in the `Store` enum
- [ ] The `Feature` enum's constants + `apiModulePath`/`realModulePath` convention → yours
- [ ] The `Store` enum's constants (+ `storeName` derivation) → your real stores and feature lists
- [ ] Drop `applicationId` / `businessUnitDefaults` / the whole iOS step if you don't need them
- [ ] `agp` version entry present in `gradle/libs.versions.toml`

**Part II — feature registration:**
- [ ] `:core:feature` module with `FeatureId` (enum) + `HomeFeature` + `AppScope` (your package)
- [ ] Metro plugin on every `:real` module and on each graph module (`:androidApp`, `shared`)
- [ ] `metro` version in the catalog matches your Kotlin version
- [ ] Each `:real` impl: `@ContributesIntoSet(AppScope::class) @Inject class … : HomeFeature`
- [ ] `AppGraph` in `:androidApp` **and** `shared/iosMain`, with `@Multibinds(allowEmpty = true)`
- [ ] Native UIs read the set (Android `createAppGraph().features`; iOS `HomeFeatures` façade)
- [ ] Removed any prior string-based feature list

---

## One-paragraph recap

Put your store→feature table in typed Kotlin enums (`Store`, `Feature`) inside an included
`build-logic` build — every name is compiler-checked, and the strings Gradle needs are derived once.
A `store-catalog` plugin exposes it as a `storeCatalog` extension and validates feature modules; a
`store-features` plugin turns it into Android product flavors that each link only their store's feature
`:real` modules — build-time exclusion (on iOS, the shared module's `-Pstore` loop does the same for
the framework). Then let those modules **announce themselves**: each `:real` contributes an
implementation of a tiny `HomeFeature` identity contract into an `AppScope` multibinding via Metro's
`@ContributesIntoSet`, and a per-platform `AppGraph` (one in `:androidApp`, one in `shared/iosMain`,
because that's where features are linked on each platform) aggregates every contribution on its
classpath — exactly the store's shipped features — into a single `Set<HomeFeature>` the native UI
reads. Because UI isn't shared, the contract carries data (id, title), and each platform maps ids to
its own native screens. Adding a store or feature is a one-line edit, checked by the compiler, with no
external config files and no hand-maintained list to keep in sync.
