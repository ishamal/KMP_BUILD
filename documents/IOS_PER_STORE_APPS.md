# Building the iOS app per store ("true per-store apps")

How to build the iOS app for a specific store — the iOS counterpart to Android's product flavors.
Grounded in this project's real files. Companion to `IOS_STORE_SELECTION_EXPLAINED.md` (which explains
*why* iOS selects stores with a Gradle property).

> **Status in one line:** the Gradle↔Xcode **bridge is wired and verified** — you can build any store
> from Xcode by setting one value. The **toolbar scheme-picker** (pick store like Android's Build
> Variants) needs one **manual Xcode step** (§6); the pieces for it are scaffolded but inert until you
> do that step.

---

## 1. What's wired & verified vs. what's manual

| Capability | State |
|---|---|
| Build a chosen store's framework from CLI (`-Pstore=storeB`) | ✅ works |
| Build a chosen store **from Xcode** (via `GRADLE_STORE`) | ✅ wired — Run Script passes `-Pstore=$GRADLE_STORE` |
| Per-store **bundle id** so stores install side-by-side | ✅ wired — `PRODUCT_BUNDLE_IDENTIFIER` follows `GRADLE_STORE` |
| Blank/unset store falls back to default (no build break) | ✅ verified in `shared/build.gradle.kts` |
| `generateIosStore` scaffolds one xcconfig per store | ✅ works — writes `iosApp/Configuration/store-*.xcconfig` |
| **Pick store from the Xcode toolbar (scheme picker)** | ⚠️ **manual** — needs per-store build configs + schemes (§6) |

The generated `store-*.xcconfig` files are **inert** until §6 is done — nothing references them yet.
This split is deliberate: the config-file + Run Script edits are safe and verifiable; hand-editing
`project.pbxproj` to add build configurations/schemes is not (a malformed entry stops Xcode opening
the project), so that part is left as explicit Xcode UI steps.

---

## 2. The mechanism (the chain, once §6 is also done)

```
Pick store  →  GRADLE_STORE (xcconfig)  →  exported as env var into the Run Script
                                        →  PRODUCT_BUNDLE_IDENTIFIER = com.softlogic.kmpbuild.$(GRADLE_STORE)
        ▼
Run Script:  ./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pstore=$GRADLE_STORE
        ▼
shared/build.gradle.kts:  providers.gradleProperty("store")  →  that store
        ▼
only that store's feature :api/:real modules link; StoreInfo.enabledFeatures = its features
        ▼
app installs as com.softlogic.kmpbuild.<store>; SwiftUI greys out unshipped tiles
```

One choice drives **both** things that must vary per store: which **features** compile (Gradle
`-Pstore`) and the app **identity** (Xcode bundle id).

---

## 3. Files that make this work

- **`iosApp/Configuration/Config.xcconfig`** — adds `GRADLE_STORE=storeA` (default) and makes
  `PRODUCT_BUNDLE_IDENTIFIER=com.softlogic.kmpbuild.$(GRADLE_STORE)$(TEAM_ID)`.
- **`iosApp/iosApp.xcodeproj/project.pbxproj`** — the Run Script build phase now runs
  `./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pstore=$GRADLE_STORE` (and echoes
  `GRADLE_STORE` into the build log).
- **`shared/build.gradle.kts`** — resolves the store from `-Pstore` (blank falls back to the default);
  the `generateIosStore` task scaffolds the per-store xcconfigs.
- **`iosApp/Configuration/store-<name>.xcconfig`** — generated; each `#include`s `Config.xcconfig` and
  sets `GRADLE_STORE = <name>`.

---

## 4. Build a specific store from Xcode today (single active store)

The fastest path, no project surgery:

1. Set the store in **`iosApp/Configuration/Config.xcconfig`**:
   ```
   GRADLE_STORE=storeB
   ```
2. Open `iosApp/iosApp.xcodeproj` in Xcode → pick a simulator → **Run**.

You get storeB's features (unshipped tiles greyed) and bundle id
`com.softlogic.kmpbuild.storeB`. Switch stores by editing that one line.

> Equivalent without editing xcconfig: put `store=storeB` in the root `gradle.properties` — the Run
> Script's Gradle build reads it too. (Bundle id won't change that way, though.)

---

## 5. `generateIosStore` — catalog-driven scaffolding

```
./gradlew :shared:generateIosStore
```
Writes one `iosApp/Configuration/store-<name>.xcconfig` per store in the `STORES` table, e.g.
`store-storeB.xcconfig`:
```
#include "Config.xcconfig"
GRADLE_STORE = storeB
```
Re-run it after adding a store to `STORES`. These files exist so §6's build configurations have
something catalog-consistent to point at. **Commit them** — the manual build configs reference them.

---

## 6. Manual step for the toolbar scheme-picker (per-store configs + schemes)

Do this once in Xcode to get the "pick store like Android Build Variants" experience. (Xcode's UI
makes this safe; hand-editing `project.pbxproj` for it is not, which is why it isn't automated.)

1. **Duplicate build configurations, one set per store.** Project ▸ Info ▸ Configurations: duplicate
   `Debug` → `Debug-storeB`, `Release` → `Release-storeB`, etc.
2. **Point each store's configs at its xcconfig.** For `Debug-storeB`/`Release-storeB`, set the
   configuration file (baseConfiguration) to `Configuration/store-storeB.xcconfig`.
   That file's `GRADLE_STORE = storeB` now flows into the build.
3. **Create one scheme per store.** Product ▸ Scheme ▸ New Scheme → name it `storeB`; in Edit Scheme,
   set every action (Run/Test/Profile/Archive) to the matching `*-storeB` configuration. Mark the
   schemes **Shared** so they live in the repo (`xcshareddata/xcschemes/`).
4. **Pick the store from the toolbar** by choosing its scheme, then Run. Each store installs as its
   own app (distinct bundle id), side-by-side.

Skipping a piece: no per-store config → the toolbar can't switch stores; no per-store bundle id →
stores overwrite each other on device.

### 6a. Gotcha: "Unable to detect Kotlin framework build type for CONFIGURATION=Debug-<store>"

`embedAndSignAppleFrameworkForXcode` infers debug/release by matching the Xcode `CONFIGURATION`
name **exactly** against `Debug`/`Release`. A custom name like `Debug-storeB` (or `Debug-AppB`)
doesn't match, so Kotlin can't tell and fails with:

```
error: Unable to detect Kotlin framework build type for CONFIGURATION=Debug-storeB automatically.
Specify 'KOTLIN_FRAMEWORK_BUILD_TYPE' to 'debug' or 'release'
```

Fix: state it explicitly. `generateIosStore` now writes `KOTLIN_FRAMEWORK_BUILD_TYPE = debug` into each
`store-*.xcconfig`, so a **Debug**-based per-store config just works. For a **Release/Archive** per-store
config, set `KOTLIN_FRAMEWORK_BUILD_TYPE = release` on that configuration (or point it at a separate
release xcconfig). After changing it, Clean Build Folder (⇧⌘K) if Xcode reused a cached build.

---

## 7. Verifying it worked

- The Run Script echoes `GRADLE_STORE=[…]` — check **Xcode's build log** (Report navigator) to confirm
  the store that was actually passed to Gradle.
- **Always test with a non-default store** (`storeB`/`storeC`), never `storeA`: storeA is the fallback
  for every failure path, so it hides a broken bridge. Confirm an unshipped tile is greyed.
- CLI sanity checks:
  ```
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Pstore=storeB   # links that store
  ./gradlew :shared:generateIosStoreInfo -Pstore=storeB                  # inspect the generated set
  ```

---

## 8. Bundle-id note (not a bug)

iOS keeps the store's **camelCase** in the bundle id (`com.softlogic.kmpbuild.storeB`), while Android's
`applicationId` is **lowercase** (`com.softlogic.kmpbuild.storeb`). Same store, cosmetic difference —
Xcode variables can't lowercase a value. If exact parity matters, set an explicit
`PRODUCT_BUNDLE_IDENTIFIER` in each `store-*.xcconfig`.

---

## 9. Adding a store later

1. Add one `StoreDef` line to `build-logic/.../Stores.kt`.
2. `./gradlew :shared:generateIosStore` → new `store-<name>.xcconfig` appears.
3. (For the scheme picker) repeat §6 for the new store.

Android gets its flavor automatically; iOS gets CLI/`GRADLE_STORE` selection immediately, and the
toolbar entry after §6.


In Xcode (safe, UI-driven):
1. Project ▸ Info ▸ Configurations → duplicate Debug → name it Debug-storeA (repeat Release-storeA if you archive).
2. Select Debug-storeA → set its Configuration File to Configuration/store-storeA.xcconfig. (Now GRADLE_STORE = storeA from that file flows into the build.)
3. Product ▸ Scheme ▸ New Scheme → name it storeA; in Edit Scheme, set each action (Run/Test/Profile/Archive) to the matching *-storeA configuration. Tick Shared so it's
   committed.
4. Repeat for storeB, storeC. Now the toolbar scheme dropdown is your store picker.                                                                                      
                                                                                       