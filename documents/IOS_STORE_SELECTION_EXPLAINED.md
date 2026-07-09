# How iOS selects a store — the slow walkthrough

Beginner-friendly companion to `STORE_CONVENTION_PLUGIN_FROM_ZERO.md`. Android's per-store
mechanism is obvious (a build variant you click). iOS is less obvious — this doc unpacks it from
first principles, using this project's real files and values (`com.softlogic.kmpbuild`,
stores `storeA/storeB/storeC`, features `login/cart/settings/orders`).

---

## 0. The one-sentence version

> On Android, "which store" is a **build variant** you pick in the IDE. On iOS there are no build
> variants, so "which store" is just a **Gradle property** (`-Pstore=…`) that the framework build
> reads — and right now nothing tells Xcode to pass it, so Xcode always builds the default (`storeA`).

Everything below unpacks that sentence.

---

## 1. Why iOS is different at all

Android's build system (AGP) has a native concept of **product flavors** — `storeA/storeB/storeC`
are first-class build variants you see in a dropdown. Each flavor has its own dependency bucket
(`storeCImplementation`), so "pick storeC" and "link only storeC's features" are the *same action*.

iOS has **none of that machinery**. Kotlin Multiplatform produces exactly **one** artifact —
`Shared.framework`, a single compiled bundle SwiftUI imports. There is no "flavor of a framework."
So we need a *different knob* for "which store's features go into this one framework?" That knob is a
**Gradle property**.

---

## 2. The cast of characters

Pressing ▶ in Xcode for an iOS run involves three separate programs:

| Actor | Role | Analogy |
|---|---|---|
| **Xcode** | You press Run. Knows Swift, *not* Kotlin. | The customer |
| **Run Script build phase** | A shell script Xcode runs before compiling Swift; shells out to Gradle. | The waiter carrying the order |
| **Gradle (`:shared`)** | Compiles Kotlin → `Shared.framework`, reading the store catalog. | The kitchen |

The store decision must travel **customer → waiter → kitchen**. We follow it in reverse, starting in
the kitchen, because that part already works.

---

## 3. The kitchen: how Gradle decides the store (line by line)

`shared/build.gradle.kts`, lines 12–15:

```kotlin
val storeCatalog = extensions.getByType<com.softlogic.gradle.StoreCatalogExtension>()   // (a)
val store = providers.gradleProperty("store").getOrElse(storeCatalog.selectedStore)     // (b)
val storeFeatures = storeCatalog.featuresFor(store)                                      // (c)
```

**(a)** Get the catalog — the same `STORES` table Android uses. One source of truth.

**(b)** The whole selection mechanism, in two halves:
- `providers.gradleProperty("store")` — *"did someone pass `-Pstore=…`?"* A **Gradle property** is a
  key/value handed to a build with `-P`. `-Pstore=storeB` sets property `store` to `"storeB"`.
- `.getOrElse(storeCatalog.selectedStore)` — *"…and if nobody did, fall back to the default."* The
  default is `SELECTED_STORE = "storeA"` in `StoreCatalogExtension.kt`.

So `store` = `"storeB"` when you pass `-Pstore=storeB`, and `"storeA"` when you pass nothing.

**(c)** `featuresFor("storeB")` looks up that store's row in `STORES` and returns its feature list —
`["login","cart","settings"]` (no `orders`).

Two loops turn that list into "what physically links into the framework":

```kotlin
// framework block (line 26):
storeFeatures.forEach { export(project(":features:$it:api")) }      // make :api visible to Swift

// iosMain (lines 53–56):
storeFeatures.forEach {
    api(project(":features:$it:api"))               // contract, exported
    implementation(project(":features:$it:real"))   // implementation, linked but hidden
}
```

**This is the build-time exclusion.** If `store = "storeB"`, the loop never touches `orders`, so
`:features:orders:real` is never compiled into the framework. Verified: linking storeB runs
`login/cart/settings` tasks and **not** `orders`; the default storeA additionally runs `orders`.

> **Kitchen summary:** give Gradle `-Pstore=X` and it links exactly store X's features. No property →
> `storeA`. This part is done and verified.

---

## 4. The waiter: how Xcode calls Gradle today (line by line)

The **Run Script build phase**, `iosApp/iosApp.xcodeproj/project.pbxproj:155`, unescaped:

```sh
if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
  echo "Skipping Gradle build task invocation ..."
  exit 0
fi
cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

- The `if` block is an escape hatch (skip when another tool already built). Ignore it.
- `cd "$SRCROOT/.."` — `SRCROOT` is `iosApp/`; `..` moves to the project root where `./gradlew` lives.
- `./gradlew :shared:embedAndSignAppleFrameworkForXcode` — build the framework, copy + sign it into
  the app. **The waiter placing the order.**

Look hard at that last line: **there is no `-Pstore=…` on it.** The waiter never names a store, so the
kitchen falls back to its default — **`storeA`, every time**, regardless of which Xcode scheme or
configuration you pick. Not a bug — the wire was simply never connected.

---

## 5. Why the command line *can* select, but Xcode can't

- **Command line:** *you* are the waiter; you type the store:
  ```sh
  ./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pstore=storeB
  ```
- **Xcode:** the waiter is a **fixed script** with the store name missing, and there's nowhere to type
  it — the Debug/Release configs and the single scheme don't carry a store anywhere.

To let Xcode choose, give the waiter a note it can read and append to the order: an **xcconfig
variable**.

---

## 6. The missing bridge: xcconfig → environment variable → `-Pstore`

**What is an xcconfig?** A plain-text file of Xcode build settings. Yours is
`iosApp/Configuration/Config.xcconfig`:

```
PRODUCT_NAME=KmpBuild
PRODUCT_BUNDLE_IDENTIFIER=com.softlogic.kmpbuild.KmpBuild$(TEAM_ID)
...
```

Two facts make it the perfect bridge:
1. **Xcode exports every build setting as an environment variable** into Run Script phases. A setting
   named `GRADLE_STORE` becomes `$GRADLE_STORE` inside the script — automatically.
2. xcconfig values can differ **per build configuration** (Debug vs Release, or custom ones like
   `Debug-storeB`).

So the wiring is a two-line idea:

```
# 1. in an xcconfig — declare the store:
GRADLE_STORE = storeB
```
```sh
# 2. in the Run Script — pass it through:
./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pstore=$GRADLE_STORE
```

Now the note reaches the kitchen, and the same `providers.gradleProperty("store")` from §3 picks it
up. **The kitchen doesn't change** — Android and iOS still read the same catalog; we only taught the
waiter to carry the store name.

---

## 7. The bundle-id piece

`Config.xcconfig:4`:
```
PRODUCT_BUNDLE_IDENTIFIER=com.softlogic.kmpbuild.KmpBuild$(TEAM_ID)
```

A subtlety people miss. On **Android**, `catalog.applicationId("storeC")` →
`com.softlogic.kmpbuild.storec` is applied by our Gradle plugin as the flavor's `applicationId`. On
**iOS**, the installed app's identity is the **Xcode bundle id**, which Gradle does *not* control —
it's set here in the xcconfig.

So Gradle's `applicationId(store)` value is currently **Android-only**. For storeB's iOS app to install
as its own app (`com.softlogic.kmpbuild.storeb`, distinct from storeA), set it in the xcconfig too:

```
PRODUCT_BUNDLE_IDENTIFIER = com.softlogic.kmpbuild.$(GRADLE_STORE)
```

That's why "select a store" and "which bundle id" are *both* xcconfig concerns on iOS — they live
together.

---

## 8. The full chain, once wired

```
You pick a scheme/config in Xcode
        │   (each config's xcconfig sets: GRADLE_STORE = storeB,
        │    PRODUCT_BUNDLE_IDENTIFIER = com.softlogic.kmpbuild.storeb)
        ▼
Xcode exports GRADLE_STORE as an env var into the Run Script phase
        ▼
Run Script:  ./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pstore=$GRADLE_STORE
        ▼
shared/build.gradle.kts:  providers.gradleProperty("store")  →  "storeB"
        ▼
storeCatalog.featuresFor("storeB")  →  [login, cart, settings]
        ▼
only those :api/:real modules link into Shared.framework   ← build-time exclusion
        ▼
SwiftUI imports Shared, sees only storeB's exported contracts
```

The **only** currently-missing links are the top two boxes (the xcconfig variable and
`-Pstore=$GRADLE_STORE` on the script). Everything from the third box down already works and is
verified.

---

## 9. Android vs iOS, side by side

| | Android | iOS |
|---|---|---|
| "Store" is a… | product flavor (build variant) | Gradle property `-Pstore` |
| You pick it by… | Build Variants dropdown / `assembleStoreBDebug` | (today) command line only; (wired) Xcode scheme → xcconfig |
| Features linked via | `storeBImplementation(project(...))` per flavor | `api`/`implementation` loop in `iosMain` |
| App identity | `applicationId` set by our Gradle plugin | `PRODUCT_BUNDLE_IDENTIFIER` in xcconfig (Gradle can't set it) |
| Source of truth | `STORES` table | **same** `STORES` table |
| Build-time exclusion | ✅ per flavor | ✅ per framework build |

---

## 10. The one thing to remember

The store selection **logic** is identical on both platforms — both call
`storeCatalog.featuresFor(store)`. The only difference is **how the string `"storeB"` reaches that
call**: on Android the flavor system supplies it; on iOS *you* supply it via `-Pstore`, and Xcode can
only supply it if you add the xcconfig→script bridge (§6–7).

---

## 11. Current status & how to select today

- **Implemented and verified:** the Gradle side (§3). Select a store from the command line:
  ```sh
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Pstore=storeB   # verify a store links
  ./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pstore=storeC    # Xcode-style task
  ```
- **Not yet wired:** the Xcode bridge (§6–7). Until added, building from Xcode always uses `storeA`.
- **Options to wire it** (pick per taste): a minimal single `GRADLE_STORE` line in `Config.xcconfig`;
  per-store build configs + schemes (pick store = pick scheme, like Android Build Variants); or a
  Gradle `generateIosStore` task that scaffolds the per-store xcconfig/scheme from the `STORES`
  catalog (config-cache-safe: read catalog values *inside* the task's `doLast`).
