# The concepts behind per-store feature wiring — a beginner's guide

This document explains the **ideas** behind the store→feature build system. No setup steps, no
copy-paste — just *what* each concept is, *why* it exists, and how they fit together. Read this first;
then the `STORE_FEATURE_WIRING_COMPLETE_GUIDE.md` (the "how") will make sense.

No prior knowledge is assumed beyond: you know what an app is, and you've seen a Gradle project.

---

## 1. The problem we're solving

Imagine you build **one shopping app**, but you sell it to **several different stores** (brands,
clients, markets). Each store wants a slightly different app:

| | Store A | Store B | Store C |
|---|---|---|---|
| Login | ✅ | ✅ | ✅ |
| Cart | ✅ | ✅ | ❌ |
| Orders | ✅ | ❌ | ✅ |
| Settings | ❌ | ✅ | ❌ |

This is often called a **white-label app**: one codebase, many branded variants.

The naive solutions all hurt:

- **Copy the project three times** → three codebases to fix every bug in. Disaster.
- **One app with `if (store == "B") hide(ordersButton)` everywhere** → every store's app *contains all
  the code* of every other store. The code for features a store never bought still ships inside its
  app: bigger downloads, and a competitor can decompile Store B's app and find Store C's unreleased
  feature inside.
- **A config file the app reads at runtime** ("features: cart, orders") → same problem. The code is
  still in the app; you're only *hiding* it.

What we actually want:

> **Store B's app should not merely hide the Orders feature — the Orders code should never be
> compiled into Store B's app at all.**

That's called **build-time exclusion**, and everything in this system exists to achieve it cleanly.

---

## 2. Concept: modules — cutting the app into LEGO bricks

You can't exclude a feature at build time if all your code lives in one big folder. Step one is to cut
the codebase into **Gradle modules** — separately-compiled pieces, like LEGO bricks:

```
:androidApp            the Android app shell (screens, navigation)
:shared                shared Kotlin logic used by Android and iOS
:core:feature          tiny shared contracts (explained below)
:features:cart:api     ┐
:features:cart:real    │  one pair of modules
:features:orders:api   │  per feature
:features:orders:real  ┘
```

A module only "exists" for another module if it's declared as a **dependency**. So the whole game
becomes:

> For Store B's build, list `:features:cart:real` as a dependency and simply **don't list**
> `:features:orders:real`. Then the compiler never even sees the Orders code.

Exclusion stops being clever engineering — it's just *the absence of one line*. The rest of this
document is about making that line appear and disappear automatically, per store.

---

## 3. Concept: the `api` / `real` split — promise vs delivery

Each feature is **two** modules, not one. Why?

- **`:api` — the promise.** A tiny module containing just an interface: *"an Orders feature can do
  these things."* No real code. Anyone can safely depend on it.
- **`:real` — the delivery.** The actual implementation: screens' logic, network calls, everything.
  Only builds that ship the feature depend on this.

Think of it like a wall socket:

- `:api` is the **socket shape** — standardized, everyone can see it and build against it.
- `:real` is the **appliance you plug in** — only present if you bought one.

This split is what makes exclusion painless. Other code depends on the *promise* (which is always
there and costs nothing), so removing the *delivery* from a store's build doesn't break compilation —
it just means nothing is plugged into that socket.

---

## 4. Concept: one source of truth — the store table

Somewhere, something has to say *which store gets which features*. The worst version of this is the
information living in five places (the Android build file, the iOS build file, a README, someone's
head…) that slowly drift apart.

So the whole configuration is **one small table, written in Kotlin**:

```kotlin
enum class Feature { LOGIN, CART, ORDERS, SETTINGS }    // every feature that exists

enum class Store(val features: List<Feature>) {         // every store and what it ships
    STORE_A(listOf(Feature.LOGIN, Feature.CART, Feature.ORDERS)),
    STORE_B(listOf(Feature.LOGIN, Feature.CART, Feature.SETTINGS)),
    STORE_C(listOf(Feature.LOGIN, Feature.ORDERS)),
}
```

That's it. That's the entire "configuration" of the system. Adding a store = adding one line.

Why Kotlin and not a JSON/YAML/properties file? Because a Kotlin table is **checked by the compiler**
(a typo becomes a build error, not silent misbehavior) and needs no file-parsing code. Plain data,
strongly typed, in exactly one place. This is the **single source of truth** principle: any fact your
system depends on should have exactly one authoritative home.

Notice that both stores and features are **enums**, not strings. This is the same principle taken one
step further. With strings, `"ordres"` or `"stroeB"` is a perfectly valid string — you'd only find the
typo when the build's validation runs. With an enum, the table can only reference constants that
actually exist: `Feature.ORDRES` **doesn't compile**, your IDE autocompletes the real names, and
renaming is a safe, tool-assisted refactor. The rule of thumb: **when a value can only be one of a
known set, teach the type system that set** — then wrong values stop being "caught" and start being
*impossible to write*.

Strings can't disappear entirely, though: Gradle wants a flavor *named* `"storeA"`, and the command
line passes `-Pstore=storeB` as text. Those boundary strings are **derived from the enum in one
place** (`STORE_A` → `"storeA"`) rather than typed by hand at each spot — so even where text is
unavoidable, there's still exactly one source of it.

One honest limit: the enum guarantees the *name* is real, but it can't know whether the matching
Gradle module actually exists on disk. A small validation check in the plugin still covers that gap —
each layer catches what the previous one can't.

> **Don't confuse this `Feature` enum with `HomeFeature` (Section 9).** They live in two different
> worlds. The enum exists only at **build time**, inside Gradle, and decides *which modules get
> compiled* — it never ships in the app. `HomeFeature` exists at **runtime**, inside the app, and is
> how compiled-in features *announce themselves*. They can't be merged: `build-logic` compiles before
> the app's modules even exist, and build-machine classes can't ship to the phone. They aren't even
> the same list — `login` is in the enum (it's a linked module) but never implements `HomeFeature`
> (it's not a home tile). Nothing matches them by name; the build itself is what keeps the two worlds
> in sync: enum → linked modules → self-registered set.

---

## 5. Concept: convention plugins — teaching Gradle your rules

Now something must *read* that table and turn it into actual build wiring. Gradle build scripts
(`build.gradle.kts`) can contain logic, but stuffing loops and rules into them gets messy and
un-reusable fast.

Gradle's answer is the **convention plugin**: a small Gradle plugin *you* write, living in its own
mini-project (a folder called `build-logic`), that packages up your build rules so any module can
apply them with one line:

```kotlin
plugins {
    id("com.example.store-features")   // ← "apply our store rules to this module"
}
```

Think of it as a **recipe card**. Instead of every kitchen (module) improvising the dish (build
configuration), you write the recipe once and hand out the card. In this system there are two cards:

- **`store-catalog`** — "here is the STORES table, exposed as an object build scripts can query."
- **`store-features`** — "read the table and wire up the Android build accordingly" (next section).

The `build-logic` folder is an **included build**: a tiny, separate Gradle project whose only output
is these plugins. Your main build says `includeBuild("build-logic")` once, and the plugins become
available everywhere.

---

## 6. Concept: product flavors — one codebase, many Android apps

Android's build system (AGP) has a built-in feature designed exactly for the white-label situation:
**product flavors**. Declaring flavors `storeA`, `storeB`, `storeC` means one codebase produces three
different APKs: `assembleStoreADebug`, `assembleStoreBDebug`, and so on. Each flavor can have its own
app id, its own resources — and crucially, **its own dependencies**:

```kotlin
// pseudo-code of what the store-features plugin generates from the table:
storeAImplementation(":features:cart:real")     // Store A's build sees cart
storeAImplementation(":features:orders:real")   // ...and orders
storeBImplementation(":features:cart:real")     // Store B's build sees cart
                                                // ...but orders is simply never mentioned
```

`storeBImplementation(...)` means "a dependency **only** for the storeB flavor." This is the exact
mechanism from Section 2 — the presence or absence of a dependency line — and the convention plugin
generates those lines automatically from the STORES table.

So on Android the pipeline is:

```
STORES table  →  store-features plugin  →  one flavor per store  →  per-flavor dependencies
              (build-logic)                                        (Orders code absent from Store B's APK)
```

iOS has no flavors, so there the store is picked with a command-line switch (`-Pstore=storeB`) and the
shared framework links only that store's feature modules — different mechanism, same principle, same
table.

---

## 7. The second problem: how does the app know what it shipped with?

Build-time exclusion works now. But it creates a new question. The home screen wants to show a tile
for each available feature. How does it know which features made it into *this* build?

The tempting answer: generate a list of names at build time — `"cart,orders"` — and bake it into the
app. But notice what that is: a **second source of truth**. The compiled modules already *are* the
answer to "what shipped." A parallel string list describing the same fact can drift out of sync with
reality (a typo, a renamed feature, a module dropped but the string forgotten).

The elegant answer flips the question around:

> Don't keep a list of features. Let each feature that made it into the build **announce itself**, and
> collect the announcements.

If a feature's code isn't in the build, its announcement isn't either. The set of announcements is
*automatically* exactly the set of shipped features. It cannot be wrong, because it isn't a
*description* of the build — it *is* the build.

---

## 8. Concept: dependency injection & multibinding — the sign-in sheet

The mechanism for "announce yourself and get collected" comes from **dependency injection (DI)**.

DI in one sentence: instead of code creating the objects it needs, a central **graph** creates objects
and hands them out. For our purposes you need just one DI idea, called **multibinding**:

> Many separate classes each declare "add me to the set." The DI graph collects all such declarations
> into one `Set` that anyone can ask for.

Think of a **sign-in sheet at a conference**: the organizer doesn't keep a guest list written in
advance; whoever actually shows up signs the sheet, and the sheet *is* the attendance record.

This project uses **Metro**, a compile-time DI library for Kotlin Multiplatform. Each feature's
implementation signs the sheet with one annotation:

```kotlin
@ContributesIntoSet(AppScope::class)   // "add me to the set"
class OrdersFeatureImpl : HomeFeature {
    override val id = "orders"
    override val title = "Orders"
}
```

And the app's graph collects the signatures:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    val features: Set<HomeFeature>     // ← everyone who signed in
}
```

The critical property: Metro does this **at compile time**. When the app graph compiles, Metro scans
the code that is *actually in the build* and generates the set right there. For Store C it literally
generates `setOf(LoginFeatureImpl(), OrdersFeatureImpl())`; for a store with none, `emptySet()`.
Nothing is discovered "at runtime" — the set is frozen into the binary, and it's exactly the
build-time truth from Part I.

Both halves click together here:

```
Part I:  the store table decides which :real modules are COMPILED IN
Part II: whatever is compiled in ANNOUNCES ITSELF into one Set
Result:  the Set the UI reads  ==  what the store ships, by construction
```

---

## 9. Concept: identity vs behaviour — why `HomeFeature` is so tiny

You may wonder why the sign-in sheet uses a minimal `HomeFeature` (just `id` + `title`) rather than
each feature's own rich interface (`OrdersFeature`, `CartFeature`, …).

Because the home screen doesn't care what each feature *does* — it only needs to **list** them. If
listing required knowing every feature's specific type, the home screen would need to import all of
them, defeating the whole point of decoupling.

So there are two kinds of contracts, with different jobs:

- **Identity** (`HomeFeature`, in `:core:feature`): the lowest common denominator — "I exist, here's
  my id and display title." This is what gets multibound and enumerated.
- **Behaviour** (`OrdersFeature`, in `:features:orders:api`): what *this specific* feature can do.
  Used by code that actually invokes the feature, not by code that lists it.

And the id itself is not a string but a small **enum in `:core:feature`** (`FeatureId`), for the same
reason as in Section 4: everywhere a feature id appears — the impl that registers, the screen it maps
to, the home tile, even the Swift side (Kotlin enums bridge into Swift) — the compiler guarantees
it's a real feature. One `FeatureId.ORDERS` instead of the string `"orders"` copied into five files
on two platforms.

An analogy: identity is your **conference name badge** (uniform, everyone has one, used by the front
desk to list attendees); behaviour is your **job skills** (specific to you, used by people who
actually work with you).

Bonus subtlety: a feature can be shipped but choose *not* to appear on the home screen — `login` is a
real per-store module, but it's a flow, not a tile, so it simply doesn't implement `HomeFeature` and
never joins the set. Implementing the identity interface is how a feature *opts in* to a surface.

---

## 10. Concept: shared logic, native UI — and why there are two graphs

This project is Kotlin Multiplatform of a particular flavor: **only logic is shared**. Android's UI is
native Jetpack Compose; iOS's UI is native SwiftUI. No shared UI code at all.

Two consequences follow, and they explain design choices that otherwise look arbitrary:

1. **`HomeFeature` carries data, not screens.** It can't hold a `@Composable` or a SwiftUI `View`,
   because it's shared code and neither UI framework exists there. It carries an `id`; each platform
   keeps its own map of `id → its own native screen`.

2. **There is one `AppGraph` per platform, not one shared graph.** A Metro graph can only collect
   announcements from code *on its own compile classpath*, and the two platforms link feature modules
   in different places: Android links them into `:androidApp` (via flavors), iOS links them into
   `:shared`'s iOS half (via `-Pstore`). No single vantage point sees both — so Android and iOS each
   have their own (identical-looking) `AppGraph`. They're two sign-in sheets at two doors of the same
   building.

---

## 11. Why "build-time" is the theme of everything

Notice how every mechanism chosen here resolves things **before the app ever runs**:

| Decision | When it's resolved | By what |
|---|---|---|
| Which features a store gets | build time | the STORES table |
| Which code is in the binary | build time | flavor dependencies / framework linking |
| Which features are in the DI set | compile time | Metro code generation |
| A typo in a feature name | build time | plugin validation + Kotlin compiler |

The alternative — runtime flags, config files, reflection-based discovery — always means the app
*contains* everything and *decides* late. Build-time resolution means:

- **Smaller apps** — unshipped code isn't in the binary.
- **Real separation** — a store's build cannot even accidentally reference an unshipped feature; it
  won't compile.
- **Errors surface at build, not in production** — a misconfigured store fails on the developer's
  machine, not on a user's phone.
- **Nothing to keep in sync** — there is one table; everything else is derived from it mechanically.

---

## 12. The whole story in five sentences

1. The codebase is cut into per-feature modules, each split into an `:api` (the promise) and a
   `:real` (the delivery), so shipping a feature = depending on its `:real`.
2. One typed Kotlin table (`STORES`) says which store ships which features — the single source of
   truth.
3. Convention plugins in `build-logic` read that table and generate the wiring: one Android product
   flavor per store, each depending only on its own features' `:real` modules (iOS does the same via
   `-Pstore`).
4. Each `:real` module carries a Metro annotation that says "add me to the set," so whatever code
   actually got compiled in announces itself into a `Set<HomeFeature>` collected by a per-platform DI
   graph.
5. The native UI on each platform reads that set to build the home screen — so what the user sees is,
   by construction, exactly what the build contains.

---

## Glossary

| Term | Meaning here |
|---|---|
| **White-label app** | One codebase producing multiple branded app variants |
| **Store** | One such variant (a brand/client/market) |
| **Build-time exclusion** | Unshipped feature code is never compiled into the binary (vs hidden at runtime) |
| **Module** | A separately-compiled Gradle sub-project; the unit of inclusion/exclusion |
| **`:api` / `:real`** | A feature's public contract vs its actual implementation |
| **Single source of truth** | A fact stored in exactly one authoritative place (`STORES`) |
| **Convention plugin** | A reusable Gradle plugin you write to package your own build rules |
| **Included build (`build-logic`)** | A mini Gradle project whose only job is producing those plugins |
| **Product flavor** | AGP's mechanism for producing multiple app variants from one module |
| **`<flavor>Implementation`** | A dependency that exists only for one flavor's build |
| **Dependency injection (DI)** | A central graph constructs objects and hands them out |
| **Multibinding** | Many classes each contribute one element; DI collects them into a `Set` |
| **Metro** | The compile-time DI library used; generates the graph as Kotlin compiles |
| **`@ContributesIntoSet`** | The "sign the sheet" annotation on each feature implementation |
| **`AppGraph`** | The per-platform collector that exposes `Set<HomeFeature>` |
| **Identity vs behaviour** | `HomeFeature` (listable: id/title) vs `OrdersFeature` (what it can do) |

---

*Next step: read `STORE_FEATURE_WIRING_COMPLETE_GUIDE.md` — the hands-on version of everything above.*
