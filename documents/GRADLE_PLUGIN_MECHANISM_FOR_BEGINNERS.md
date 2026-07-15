# How the plugin mechanism actually works — an entry-level explanation

The store-catalog system rests on Gradle **convention plugins** (`build-logic`, `includeBuild`,
`id("com.softlogic.store-features")`, …). This document explains that mechanism from absolute zero,
using this repo's real files. No prior Gradle knowledge assumed beyond "I've seen a
`build.gradle.kts`".

> Related docs: `STORE_FEATURE_WIRING_CONCEPTS_FOR_BEGINNERS.md` explains *why* the system is shaped
> this way; this one explains *how the plugin machinery underneath it works*.
> `STORE_FEATURE_WIRING_COMPLETE_GUIDE.md` is the hands-on setup guide.

---

## Step 0: A build happens in two phases

When you run `./gradlew assembleStoreBDebug`, Gradle does **not** just start compiling. It works in
two phases:

1. **Configuration phase** — Gradle *reads* every `build.gradle.kts` and builds an in-memory model:
   "these modules exist, these are their dependencies, these flavors, these tasks." Nothing is
   compiled yet. It's **writing a plan**.
2. **Execution phase** — Gradle runs the tasks from that plan (compile, link, package).

Everything about plugins happens in **phase 1**. A plugin is a thing that helps *write the plan*.

---

## 1. What a build script really is

`androidApp/build.gradle.kts` is a **program that runs during configuration**. When you write:

```kotlin
android {
    productFlavors.create("storeA") { ... }
}
```

you're not *describing* something — you're *calling a function* that adds a flavor to the in-memory
model. The build script is code.

And here is the key insight the whole mechanism rests on:

> **A plugin is just that same code, moved out of the script and into a reusable class.**

Compare — these two do *exactly* the same thing:

```kotlin
// Option 1: written directly in androidApp/build.gradle.kts
android.productFlavors.create("storeA") { ... }

// Option 2: written inside a class in build-logic
class StoreFeaturesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // "target" is the module this plugin was applied to (androidApp).
        // ...same call, same effect:
        android.productFlavors.create("storeA") { ... }
    }
}
```

A plugin is a class with **one method, `apply(target)`**, and Gradle's promise is simple: *"when a
module applies your plugin, I will call `apply()` with that module during configuration."* Whatever
your method does to `target` is indistinguishable from the module having written it in its own build
script.

That's the entire magic. There is no magic.

---

## 2. Why a separate `build-logic` folder?

A class needs to be **compiled before it can run**. Build scripts run during configuration — so the
plugin class must already be compiled *before* configuration starts. Chicken and egg: the build
can't compile the thing that configures the build.

Gradle's solution: `build-logic` is a **separate mini-build** — its own `settings.gradle.kts`, its
own sources — and this line in the root `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("build-logic")   // "before configuring me, build THAT first"
}
```

tells Gradle: whenever the main build needs a plugin, compile `build-logic` first and take plugins
from there. So the full timeline is:

```
compile build-logic  →  configure the main build (using its plugins)  →  execute tasks
```

(Why not `buildSrc`? Same idea, but an *included build* like `build-logic` is more flexible —
editing it doesn't invalidate as much, and it behaves like any other Gradle build.)

---

## 3. How does a *string* find a *class*?

In `shared/build.gradle.kts` you write a string:

```kotlin
plugins {
    id("com.softlogic.store-ios-features")
}
```

How does Gradle get from that string to the `StoreIosFeaturesPlugin` class? You told it, in
`build-logic/build.gradle.kts`:

```kotlin
gradlePlugin {
    register("storeIosFeatures") {
        id = "com.softlogic.store-ios-features"                              // the public name
        implementationClass = "com.softlogic.gradle.StoreIosFeaturesPlugin"  // the class
    }
}
```

That's a **phone book: id → class**. (Under the hood it compiles to a tiny
`META-INF/gradle-plugins/com.softlogic.store-ios-features.properties` file inside build-logic's
jar — you can see it in `build-logic/build/`.) When Gradle meets the id, it looks it up,
instantiates the class, and calls `apply(thisModule)`.

---

## 4. Follow one real build, end to end

`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Pstore=storeB`:

```
1. Gradle reads settings.gradle.kts
   → sees includeBuild("build-logic")

2. Compiles build-logic
   → Stores.kt (the Store/Feature enums) and the 3 plugin classes become a jar

3. Starts configuring :shared — reads its plugins block
   → id("com.softlogic.store-ios-features") → phone book → StoreIosFeaturesPlugin

4. Calls StoreIosFeaturesPlugin.apply(shared)
   → reads -Pstore=storeB, converts it to Store.STORE_B (the one string→enum boundary)
   → for STORE_B.features = [LOGIN, CART, SETTINGS]:
        adds api/implementation dependencies to shared's iosMain
        adds export(...) to the framework
   ...exactly as if you had typed those lines in shared/build.gradle.kts yourself

5. The rest of shared/build.gradle.kts runs (targets, baseName, …)
   → the model is complete: shared depends on login+cart+settings :real — NOT orders

6. Execution phase: compile & link
   → orders code was never in the plan, so it is never compiled in
```

The same story runs on Android, with `StoreFeaturesPlugin` writing one product flavor per `Store`
constant plus that store's per-flavor feature dependencies into the plan.

---

## 5. The three "wait for it" tools

Inside the plugins you'll see three idioms that all mean *"don't do this now — do it when X
exists"*. They're needed because during configuration, things come into existence gradually, and a
plugin runs *early* (from the `plugins` block) — often before the things it wants to configure have
been created.

| Idiom | Plain meaning |
|---|---|
| `pluginManager.withPlugin("com.android.application") { }` | "when the Android plugin has been applied, then run this" — the `android { }` model doesn't exist before that |
| `configureEach { }` | "for every framework/target that *ever* gets created, do this" — works even though the module's script defines its targets *after* the plugin ran |
| `afterEvaluate { }` | "after this module's whole build script has finished, do this" — used because the `storeBImplementation` dependency buckets only exist once AGP has processed the flavors |

You don't need to memorize these — just recognize the pattern: **plugins configure things
reactively, not in reading order.**

---

## 6. Recap — one sentence each

- A build script is a **program that writes the build plan** (configuration phase), which Gradle
  then executes (execution phase).
- A plugin is that **program packaged as a class** with one method, `apply(target)`, run by Gradle
  against any module that applies it.
- `build-logic` exists because the class must be **compiled before configuration starts**;
  `includeBuild` in `pluginManagement` arranges that.
- The **plugin id is a phone-book entry** (`gradlePlugin { register }`) pointing at the class.
- The `Store`/`Feature` enums are just **data compiled into that same jar**, which the plugins read
  while writing the plan — that's the entire "catalog mechanism."

---

## 7. Feel it for yourself

Temporarily comment out the plugin in `androidApp/build.gradle.kts`:

```kotlin
plugins {
    // id("com.softlogic.store-features")
}
```

and run:

```bash
./gradlew :androidApp:tasks --all | grep -i assembleStore
```

All the `assembleStore...` tasks vanish — because nobody wrote the flavors into the plan. Put the
line back and they return. That's the plugin doing its job, at configuration time, before a single
line of app code is compiled.
