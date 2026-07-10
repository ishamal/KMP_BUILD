# Setup guide — Part 2: feature identity & self-registration with Metro DI

This continues `STORE_FEATURE_WIRING_SETUP_GUIDE.md`. That guide (Steps 0–8) makes each store
**compile in only its own feature modules**. It deliberately stops at "*how a compiled-in feature
registers itself into your app is your app's concern*". This part fills that gap: how each feature
**identifies and registers itself** so the running app can enumerate exactly the features its store
shipped — using **Metro** compile-time DI (`@ContributesIntoSet`), the mechanism the base guide
alludes to.

> Prereq: you've done Steps 1–7 (an included `build-logic`, a `STORES` table, per-store product
> flavors on Android, and — if you ship iOS — the `-Pstore` feature loop in `shared`). Replace
> `com.example…` with your package.

---

## 0. The idea in one sentence

> Each feature `:real` module says "**I am a feature**" once, with an annotation
> (`@ContributesIntoSet`). A per-platform **`AppGraph`** collects every such declaration **on its
> compile classpath** — which, because of per-store linking, is exactly the store's shipped features —
> into a single `Set<HomeFeature>` the UI reads. No hand-maintained list; add/remove a feature and the
> set changes automatically.

**Why DI instead of a string list?** The base guide's mechanism already decides *which modules
compile*. A `BuildConfig`/generated-file string list of feature names is a *second, parallel* source
of the same truth that you must keep in sync. Letting features **self-register** makes the compiled
set itself the single source — a typo or a dropped module can't desync it.

---

## A. Anatomy of a feature — what "a feature" actually is here

A "feature" isn't a single class. It's a small **module pair** plus a shared **identity**, each with a
distinct job:

```
:features:orders:api    → OrdersFeature       the feature's BEHAVIOUR contract (what Orders can do)
:features:orders:real   → OrdersFeatureImpl    the IMPLEMENTATION + self-registration
:core:feature           → HomeFeature          the cross-feature IDENTITY (how the app lists any feature)
```

Three layers, three reasons:

1. **`:api` — the behaviour contract.** A tiny interface describing *what the feature does*
   (`OrdersFeature`). It's the stable, public face: other modules and Swift depend on this, never on the
   implementation. On iOS it's `export`ed into the framework so Swift can see the type. Changing the
   *impl* never breaks callers as long as the contract holds.
2. **`:real` — the implementation.** The actual code. It is the module that gets **linked per store**
   (Android flavor dependency / iOS `-Pstore` loop from the base guide). Because only shipped stores
   link it, its very presence in a build *is* the store's decision to ship the feature. This is also the
   module that **self-registers** (Step 9).
3. **`HomeFeature` — the identity.** The one thing *every* feature has in common: an `id` and a display
   `title`. It lives in `:core:feature` so all features and both app graphs can share it. This is what
   the home screen enumerates — it lets the app treat every feature uniformly without knowing each
   feature's specific type.

### Why identity is separate from behaviour

The home screen doesn't care what a feature *does* — it only needs to **list** it (id, title, shipped?).
If listing were tied to each feature's own `:api` interface, the app would have to know about
`OrdersFeature`, `CartFeature`, `SettingsFeature`, … individually — defeating the point. `HomeFeature`
is the **common denominator** that makes "enumerate the shipped features" a single, type-uniform
operation.

| | `HomeFeature` (identity) | `<Name>Feature` (`:api`, behaviour) |
|---|---|---|
| Lives in | `:core:feature` (shared by all) | each `:features:<name>:api` |
| Answers | "list me on the home screen" | "here's what THIS feature does" |
| Shape | `id`, `title` | feature-specific (e.g. `OrdersFeature.placeOrder()`) |
| Read by | the app graph → home tiles | code that actually invokes the feature |
| To Swift | via the `HomeFeatures` object façade | via `export(:api)` in the framework |

> **Current state, stated honestly.** The home tiles only need **identity**, so each `:real` implements
> `HomeFeature`. The per-feature `:api` interfaces are still built and exported to Swift but carry no
> behaviour yet — they're the *seam* where each feature's real screen/logic plugs in later. When you add
> real behaviour, a `:real` can implement **both** (`class OrdersFeatureImpl : HomeFeature,
> OrdersFeature`); with two supertypes you then name the `@ContributesIntoSet` binding explicitly so
> Metro knows it's contributing as `HomeFeature`.

---

## B. The life of a feature — end to end

Follow **orders** from a line in the catalog to a tile on the screen:

1. **Declared** in the source of truth: `StoreDef("storeC", listOf("login", "orders"))` — storeC ships
   orders; storeB does not.
2. **Linked** at build time: the base guide's plugin adds `:features:orders:real` to a build **only**
   for stores that list it (Android: `storeCImplementation(...)`; iOS: the `-Pstore` loop). storeB's
   build never sees the module.
3. **Self-registers**: `OrdersFeatureImpl` carries `@ContributesIntoSet(AppScope::class)` — a fact
   compiled into its klib. No central list is touched.
4. **Aggregated**: when the app graph compiles, Metro scans the classpath, finds the orders contribution
   *only where the module was linked*, and generates `features = setOf(OrdersFeatureImpl(), …)`. For
   storeB it generates `emptySet()` (nothing contributed).
5. **Consumed**: the UI reads `graph.features`; the "Orders" tile is enabled for storeC and greyed for
   storeB — which never linked orders, so it could never register.

The feature never appears in a list you maintain by hand — **its presence in the compiled set *is* the
registration.** Add a store to the catalog, or drop a feature from a store, and the set changes with no
other edit.

---

## C. The features in this repo (and their roles)

| Feature | Ships in (current `STORES`) | Role |
|---|---|---|
| `login` | storeA, storeB, storeC | auth gate — **not** a home tile (see below) |
| `cart` | storeA, storeB | home tile |
| `settings` | storeB | home tile |
| `orders` | storeA, storeC | home tile |

`login` shows the identity/behaviour split from another angle. It's a real feature module, compiled per
store like the others — but it isn't a *home tile*, so its `:real` simply **doesn't implement**
`HomeFeature` and therefore never joins the `Set<HomeFeature>`. A feature opts **in** to a listing by
implementing that listing's identity interface; opting out is just *not* implementing it. The same
module can be "shipped" (linked) yet absent from a particular UI surface.

---

## Step 8 — The shared contract: what a feature's "identity" is

Create one small common module (KMP, all your targets) that both the feature `:real` modules and the
app graphs depend on. It holds two tiny things.

`:core:feature/src/commonMain/kotlin/com/example/core/HomeFeature.kt`
```kotlin
package com.example.core

/** The multibound feature "identity" — the minimum the app needs to list a feature. */
interface HomeFeature {
    val id: String
    val title: String
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
  iOS) the feature can't carry a `@Composable`/`View`. Keep `id`/`title` (and any route metadata) here;
  each platform maps `id → its own native screen`.
- `AppScope` can be any class; Metro also ships `dev.zacsweers.metro.AppScope` if you'd rather not
  define your own. We define our own to avoid depending on a framework detail.
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
    override val id: String = "orders"
    override val title: String = "Orders"
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

A Metro graph aggregates contributions **on the graph module's compile classpath**. The base guide
links features differently on each platform:

| Platform | Where the `:real` modules are linked | So the graph must live in… |
|---|---|---|
| Android | the **app** module, per product flavor | `:androidApp` |
| iOS | the **shared** module's `iosMain`, per `-Pstore` | `shared/src/iosMain` |

No single graph sees both. **You define one `AppGraph` per platform.** They're identical in shape;
each aggregates the features visible in its own compilation.

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
Apply the Metro plugin to **both** the `:androidApp` module and the `shared` module (the graph modules),
and make each depend on `:core:feature`.

> **Gotcha — `@Multibinds(allowEmpty = true)`.** If a store ships **none** of the contributing
> features, the multibinding has no contributor and Metro fails at compile time with
> `[Metro/MissingBinding] … Set<HomeFeature>`. Declaring the accessor with
> `@Multibinds(allowEmpty = true)` lets it resolve to an empty set instead. We hit this exact error on
> a lean store and it's easy to miss until then.

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
    val ids: List<String> get() = features.map { it.id }.sorted()
    fun isEnabled(id: String): Boolean = features.any { it.id == id }
}
```
```swift
// ContentView.swift
private func isEnabled(_ id: String) -> Bool {
    HomeFeatures.shared.isEnabled(id: id)   // function interop is the robust path
}
```
The `object` compiles into the Shared framework, so Swift sees it automatically — no extra export.

---

## Step 12 — Toolchain: Metro ↔ Kotlin version

Metro is a **Kotlin compiler plugin**, so its version must match your Kotlin version (a compiler plugin
built for one Kotlin release won't load on a mismatched one). Check Metro's compatibility table before
adding it, and pin the plugin version in your catalog:
```toml
[versions]
metro = "1.1.1"                 # must support your Kotlin version
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
storeB shows `bindIntoSetAsHomeFeature(CartFeatureImpl/SettingsFeatureImpl)` and no orders; storeC
shows `OrdersFeatureImpl` only.

**Tests:** applying a compiler plugin affects **every** source set in that module, including tests. Run
them (`:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`) so a plugin regression doesn't
ship silently.

---

## Step 14 — Replace the string list; don't run both

Once the DI set drives the UI, **remove** any earlier string-based mechanism for the same fact
(`BuildConfig.STORE_FEATURES`, a generated `StoreInfo`, etc.). Two live sources of "which features are
here" will eventually disagree. The Metro `Set<HomeFeature>` is now the single source on both platforms.

---

## Gotchas we actually hit

1. **`[Metro/MissingBinding] Set<HomeFeature>`** on a store shipping none of the features → add
   `@Multibinds(allowEmpty = true)` on the graph accessor (Step 10).
2. **Compiler-plugin/Kotlin mismatch** — the plugin won't load; this is a "check the compat table and
   stop," not something to fix by pinning Kotlin. Verify with a trivial one-module smoke graph first.
3. **Ambiguous bound type** — an impl implementing two interfaces makes `@ContributesIntoSet`'s implicit
   binding ambiguous. Implement a single supertype (`HomeFeature`) or specify the binding.
4. **One graph won't cover both platforms** — because Android links features into the app and iOS into
   `shared`. Define the graph per platform (Step 10); don't try to share one.
5. **`Set` interop from Swift** — expose an `isEnabled(id:)`/`ids` façade instead of the raw
   `Set<HomeFeature>` (which imports as `NSSet`).

---

## Adapt checklist

- [ ] `:core:feature` module with `HomeFeature` + `AppScope` (your package)
- [ ] Metro plugin on every `:real` module and on each graph module (`:androidApp`, `shared`)
- [ ] `metro` version in the catalog matches your Kotlin version
- [ ] Each `:real` impl: `@ContributesIntoSet(AppScope::class) @Inject class … : HomeFeature`
- [ ] `AppGraph` in `:androidApp` **and** `shared/iosMain`, with `@Multibinds(allowEmpty = true)`
- [ ] Native UIs read the set (Android `createAppGraph().features`; iOS `HomeFeatures` façade)
- [ ] Removed any prior string-based feature list

---

## One-paragraph recap

Steps 1–7 make each store compile in only its own feature modules. This part makes those modules
**announce themselves**: each `:real` module contributes an implementation of a tiny `HomeFeature`
contract into an `AppScope` multibinding via Metro's `@ContributesIntoSet`. A per-platform `AppGraph`
(one in `:androidApp`, one in `shared/iosMain`, because that's where features are linked on each
platform) aggregates every contribution on its classpath — exactly the store's shipped features — into
a single `Set<HomeFeature>` the native UI reads. The set is the single source of truth: no
hand-maintained list, checked by the compiler, and self-updating when you add or drop a feature.
