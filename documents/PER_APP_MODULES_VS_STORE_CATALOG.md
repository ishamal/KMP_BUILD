# Two ways to ship many apps from one codebase: per-app module trees vs a store catalog

A comparison of two module strategies for a KMP codebase that produces multiple apps:

- **Approach A — store catalog (this repo's current approach):** *one* `shared` / `androidApp` /
  `iosApp` tree; a typed catalog in `build-logic` (`Store` + `Feature` enums) decides which feature
  modules each store compiles in — Android product flavors, iOS `-Pstore` framework wiring.
- **Approach B — per-app module trees:** *one tree per app*: `shared<App>` / `androidApp<App>` /
  `iosApp<App>` (e.g. `sharedShop`, `sharedGrocery`), each explicitly depending on the feature
  modules that app ships.

Both achieve **build-time exclusion** (an app's binary never contains unshipped feature code), and
both keep features as `:api`/`:real` module pairs with DI self-registration. What differs is *where
the wiring lives* and *what kind of difference between apps each handles well*.

---

## 0. The question that decides everything

> **Are your "apps" variants of one product, or different products?**

- **Variants of one product** (white-label stores, brands, markets): same domain, same screens, same
  release train — they differ by *feature set*, branding, and config.
  → the **catalog** (A) fits: difference-as-data.
- **Different products** (a shop app and a logistics app; different domains, UX, cadences, teams):
  they differ by *code*, not by a feature list.
  → **per-app trees** (B) fit: difference-as-structure.

Everything below is this one question, examined from different angles. And note the approaches
**compose** (Section 6): per-product trees, with a catalog *inside* a product that has store variants.

---

## 1. The two shapes, concretely

**A — store catalog (current):**
```
build-logic/Stores.kt        Store.STORE_A(features=[LOGIN, CART, ORDERS])   ← difference is DATA
shared/                      one module; plugin links per-store features
androidApp/                  one module; flavors storeA/storeB/storeC
iosApp/                      one Xcode project; per-store build configurations (xcconfigs)
features/<name>/api|real     shared by all stores
```
Adding **storeD** = one enum constant. Flavor, framework wiring, xcconfig scaffold all derive.

**B — per-app trees:**
```
sharedShop/                  framework exports + iosMain deps for Shop, written out
sharedGrocery/               same again for Grocery
androidAppShop/              app shell for Shop
androidAppGrocery/           app shell for Grocery
iosAppShop/, iosAppGrocery/  one Xcode project per app
features/<name>/api|real     still shared across apps
```
Adding **Grocery** = a new `shared` + `androidApp` + Xcode project trio whose build files list its
features explicitly. Difference is **structure**: each app's wiring is ordinary, greppable Gradle.

---

## 2. Side-by-side

| Dimension | A: store catalog (one tree) | B: per-app trees |
|---|---|---|
| **Add an app/store** | 1 enum line; everything derives | new module trio + Xcode project + wiring (~6+ files); convention plugins reduce but don't remove this |
| **Add a feature to all apps** | 1 module pair + enum constant + per-store listing (one file) | 1 module pair + touch *every* app tree's build files |
| **Feature-set difference** | native: it *is* the data model | works, but the "table" is smeared across N build files |
| **Code/UX divergence between apps** | weak spot: no per-store source sets in `shared`; divergence forces new modules or runtime config | native: each tree evolves freely |
| **Consistency across apps** | by construction — one tree, apps can't drift | drifts by default; needs convention plugins + discipline |
| **Release coupling** | one train: all stores ship from the same commit | each app can pin/ship independently |
| **Ownership / teams** | shared ownership of one tree | clean per-team boundaries |
| **Android build** | flavors: `assembleStoreADebug`; all stores buildable in one invocation | N app modules; also one invocation |
| **iOS build** | one framework, store chosen by `-Pstore` → **one store per Gradle invocation** | each app's framework independent → all buildable in one invocation |
| **IDE sync cost** | variants = stores × buildTypes grows the Android variant matrix | module count grows linearly with apps |
| **Wiring duplication** | zero (plugin-generated) | N× (mitigated, not removed, by convention plugins) |
| **Where a mistake surfaces** | enum/table → compile error or fail-fast validation | a missed edit in one tree → discovered whenever that app builds |

---

## 3. The honest cons of the catalog approach (A)

Worth stating plainly, because the catalog looks "free" until these bite:

1. **`-Pstore` is build-wide.** A Gradle property applies to the whole invocation, so the `shared`
   framework can only be built for **one store per invocation**. CI needs one job per store; you
   cannot archive all iOS stores in a single Gradle run. (Android flavors don't have this problem.)
   Config cache softens it — each `-Pstore` value gets its own cached entry — but switching stores in
   Xcode still reconfigures and relinks `shared`.
2. **One release train.** All stores ship from the same commit of everything. You can't hold storeA
   on last month's cart while storeB takes the new one. If stores ever need independent cadences,
   the single tree fights you.
3. **Divergent shared logic has no home.** Flavors give Android per-store source sets, but `shared`
   has no per-store source set mechanism — if storeB needs genuinely different *shared* behaviour
   (not just a different feature list), your options are runtime config, or splitting yet another
   feature module. Fine occasionally; painful as a pattern.
4. **The variant matrix grows.** Every store multiplies Android variants (stores × buildTypes) and
   adds an Xcode build configuration. At 3 stores it's trivial; at 30 it's an IDE-sync and CI-matrix
   consideration.
5. **Central table = central coupling.** Every store change edits the same file (merge traffic), and
   the catalog is a shared dependency of every app — editing build-logic reconfigures everything.

## 4. The honest cons of per-app trees (B)

1. **N× wiring, forever.** Every framework export list, every graph, every app shell exists per app.
   Convention plugins can factor the *mechanics*, but each tree still declares its own feature list —
   the "table" exists anyway, just scattered across N build files with no single place to read it.
2. **Drift is the default.** A fix in `sharedShop`'s wiring silently doesn't happen in
   `sharedGrocery`. Consistency becomes a code-review problem instead of a compiler problem.
3. **Cross-cutting changes fan out.** Adding a feature all apps want, bumping a convention, renaming
   a contract: N edits, N PR reviews, N chances to miss one.
4. **More modules, more configuration.** Each app trio adds modules to settings; configuration/sync
   cost grows with app count (Isolated Projects parallelism helps here — see Section 7).
5. **Heavy ceremony for thin variants.** If two "apps" differ only by a feature list and a logo,
   maintaining two whole trees for them is structure with no information in it.

---

## 5. Same machinery, different homes

Note what does **not** change between the approaches — this repo's core investments survive both:

| Mechanism | In A (catalog) | In B (per-app trees) |
|---|---|---|
| `:api`/`:real` feature modules | linked per store by plugin | linked per app by that app's build file |
| Metro `@ContributesIntoSet` self-registration | per-platform graph in `shared`/`androidApp` | per-app graph in each tree — works identically |
| `FeatureId` typed identity | shared by all stores | shared by all apps |
| Build-time exclusion | flavors / `-Pstore` | plain dependency lists |
| Convention plugins | generate the wiring | enforce consistency across trees |

The feature-module + DI architecture is **topology-independent**. Choosing A today does not trap
you: migrating to B later means writing new wiring shells around the same feature modules — the
catalog's `Store` enum even documents exactly what each new tree must list.

---

## 6. The synthesis: they compose

The approaches answer different axes, so a codebase with both kinds of variation uses both:

```
products (different code)  →  per-product trees            (approach B)
  └─ stores of a product (different feature sets)  →  catalog inside that product   (approach A)

sharedShop/      + STORES_SHOP    = {storeA, storeB, storeC}   ← catalog scales the variants
sharedGrocery/   + STORES_GROCERY = {storeX, storeY}
features/…                                                     ← shared by everything
```

This is also the fair reading of the reviewer's position from the PR thread: *"one
shared/androidApp/iosApp per commerce app"* is right **across products** — and this repo's catalog
is right **within one product's store lineup**. If a true second product arrives, add its tree;
don't stretch one product's catalog to cover a different product, and don't clone whole trees to
cover a feature-set toggle.

---

## 7. The Gradle 10 / Isolated Projects question

(Verified against Gradle's docs and public roadmap as of mid-2026.)

- What Gradle 10 is actually slated to make default is the **Configuration Cache** — this repo
  already runs with it enabled and green.
- **Isolated Projects** is a separate, stricter, still-incubating feature; the roadmap does **not**
  commit to it being mandatory in Gradle 10.
- Under IP's own rules, the catalog mechanism is the *recommended* shape, not a banned one:
  convention plugins applied per-project, project *dependencies* chosen from compiled-in data, no
  `allprojects`/`subprojects`, no cross-project mutable-state access. "Referencing a build-logic
  manifest to select project dependencies" is not what IP restricts — configuring or reading
  *another project's model* is.
- One footnote either way: the fail-fast check in `StoreCatalogPlugin`
  (`rootProject.findProject(...) != null`) reaches across projects read-only; if IP ever flags it,
  the validation moves to `settings.gradle.kts` — a trivial change that doesn't affect the
  architecture.
- Per-app trees are neither required nor advantaged by CC; their genuine Gradle benefit is under
  IP's **parallel configuration**, where more, smaller, decoupled projects parallelize well — an
  optimization, not a correctness requirement.

So: choose between A and B on the product/variant question and the trade-offs above — not on Gradle
10 compliance, which both approaches meet.

---

## 8. Decision rubric

Choose the **catalog (A)** when most of these hold:
- [ ] The apps are one product: same domain, same screens, same backend family
- [ ] They differ by feature set, branding, config — not by divergent code
- [ ] One release train is acceptable (or desirable)
- [ ] One team (or tightly coordinated teams) owns the tree
- [ ] You expect *more variants* over time and want adding one to be a one-line change

Choose **per-app trees (B)** when most of these hold:
- [ ] The apps are different products with different domains/UX
- [ ] They need independent release cadences or version pinning
- [ ] Separate teams own separate apps
- [ ] Shared code between them is a *library* relationship, not a *variant* relationship
- [ ] The N× wiring cost buys real freedom you'll actually use

And if both lists are half-checked: per-product trees with a catalog inside the product that has
store variants (Section 6).
