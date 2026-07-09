# Convention plugins (and why not buildSrc) — from zero

This explains **Option B** from our discussion: how to keep the store→feature build logic
**data-driven** but move it out of `buildSrc` into a *convention plugin*, so it's stable instead of
risky. Beginner-friendly — every idea is built up from scratch.

---

## 0. The one sentence version

> A **convention plugin** is a reusable "build setup recipe" you write once and each module *applies*,
> instead of copy-pasting the same Gradle config everywhere. We put it in a small side-build called
> `build-logic` so that editing it doesn't force the **whole** project to rebuild — which is exactly
> `buildSrc`'s weakness.

---

## 1. What is "build logic"?

When Gradle builds your app, it first **configures** the build: it reads every `build.gradle.kts`
file and runs the code inside to decide *what to build and how* — which plugins to apply, which
dependencies to include, which product flavors (storeA/storeB/…) exist.

> **Key idea:** a `build.gradle.kts` is **code that runs before your app builds.** "Build logic" is
> that code. If it's wrong, you don't get a runtime crash — you get a **broken build**.

---

## 2. The problem this solves: repeating yourself

Imagine every app/wiring module needs the same 30 lines of setup — the same flavors, the same
"pull in each store's feature modules" loop, the same `applicationId` rules.

If you **copy-paste** those 30 lines into each module's `build.gradle.kts`, they drift apart: someone
fixes a bug in one copy and forgets the others. That's fragile.

You want to write that setup **once** and have each module say "use the standard setup." That
"write once, apply everywhere" tool is a **convention plugin**.

> **Analogy:** instead of every chef re-inventing the house dressing from memory (and each making it
> slightly differently), you print **one recipe card**. Every chef follows the same card.

---

## 3. First attempt: `buildSrc` (and why it's risky)

`buildSrc` is a special folder Gradle treats as "shared build helper code." Our current
`StoreManifest.kt` lives there. It works — but it has one big downside:

> **Everything depends on `buildSrc`.** Change *one line* in it, and Gradle must recompile the build
> logic for the **entire project** and re-configure **every** module. One small edit ripples across
> the whole build.

That's why the review called adding more logic there "risky": a mistake in `buildSrc` doesn't break
one module — it can break **every** app and **every** store at once. It's also **hard to test** (it
only runs as part of a real build).

> **Analogy:** `buildSrc` is a **shared toolbox bolted to the middle of the workshop.** Swap one tool
> and everyone has to stop and re-check their bench before continuing.

---

## 4. The fix: a convention plugin

A **convention plugin** is a plugin *you* write that bundles up standard build setup. Modules then
apply it by name, just like they apply Google's Android plugin:

```kotlin
// in a module's build.gradle.kts
plugins {
    id("com.isharaw.store-features")   // ← our own convention plugin
}
```

That one line pulls in all the store/flavor/feature wiring the plugin defines. No copy-paste. Fix a
bug once, in the plugin, and every module that applies it is fixed.

The plugin itself is usually just a Kotlin build file with a special name, e.g.:

```
build-logic/src/main/kotlin/com.isharaw.store-features.gradle.kts
```

Everything you'd normally write in a `build.gradle.kts` goes **inside** it — but now it's reusable.

> **Analogy:** the printed recipe card from Section 2. Written once, followed everywhere.

---

## 5. Where it lives: an "included build" called `build-logic`

Here's the part that makes it **stable** instead of risky. We don't put the convention plugin in
`buildSrc`. We put it in its **own little separate Gradle build**, wired in from settings:

```kotlin
// settings.gradle.kts
includeBuild("build-logic")
```

`build-logic` is a self-contained build that *produces plugins*, which the main build then *consumes*.
Why that's better than `buildSrc`:

| | `buildSrc` | `build-logic` (included build) |
|---|---|---|
| Edit one line → what rebuilds? | the **whole** project reconfigures | only the plugin + who uses it |
| Can you write tests for it? | not really | yes |
| Recommended by Gradle? | legacy | ✅ the modern way |

> **Analogy:** instead of the shared toolbox bolted into the middle of the workshop (`buildSrc`),
> `build-logic` is a **small tool shed next door.** You can reorganize the shed without making the
> whole workshop stop and re-check.

---

## 6. Applying it to *our* store→feature problem

Today, spread across `androidApp/build.gradle.kts` and `shared/build.gradle.kts`, we:
1. create a product flavor per store, and
2. pull in exactly the `:real` feature modules that store ships (so unshipped features are removed
   from the build).

With a convention plugin, that logic lives in **one** place (`com.isharaw.store-features`), and each
app/wiring module just does:

```kotlin
plugins {
    id("com.isharaw.store-features")   // creates the flavors + wires each store's features
}
```

The **mechanism that removes features is unchanged** — it's still Gradle product flavors and
per-flavor dependencies (`storeAImplementation(...)`). We've only moved *where the recipe lives*, from
the risky shared toolbox to the safe tool shed.

---

## 7. Where the store data lives — and why it's *not* a file

Our store list lives in a **typed Kotlin table** (`STORES` in `build-logic/.../Stores.kt`), not in
external `.properties` files. That's a deliberate simplification:

- **No file reading, nothing to track.** Because the data is *compiled into* `build-logic`, editing
  `Stores.kt` recompiles it and Gradle invalidates the **configuration cache** automatically. There's
  no risk of the classic footgun where you edit a config file and the cache silently serves old values.
- **Compiler-checked.** A malformed entry won't compile — no stringly-typed keys to fat-finger.

> **Why even mention files?** If you *did* read an external file at configuration time, you'd have to
> read it through a Gradle **`ValueSource`** so the cache tracks the file — otherwise edits can be
> silently ignored. We sidestep that whole class of problem by not having an external file at all.
> (This project *did* use `.properties` + a `ValueSource` first, then simplified to the Kotlin table.)

Also: the plugin **validates** feature names at configure time, so a typo fails **fast** with a clear
message ("…no module `:features:rebatte:real`") instead of a confusing error deep in the build.

---

## 8. buildSrc → convention plugin, at a glance

| | Before (`buildSrc`) | After (convention plugin in `build-logic`) |
|---|---|---|
| Where the logic lives | shared toolbox, whole project depends on it | side build, applied by name |
| Edit blast radius | reconfigures everything | only the plugin + its users |
| Testable | no | yes |
| Feature-removal mechanism | product flavors | **same** product flavors |
| Store data | `.properties` read with plain `File` (can go stale) | typed Kotlin table, compiled in (compiler-checked, no stale reads) |

---

## 9. One-paragraph recap

"Build logic" is the code in your `build.gradle.kts` files that runs *before* your app builds. When
many modules need the same setup, you don't copy-paste it — you write a **convention plugin** (a
reusable recipe) and each module `apply`s it by name. We keep that plugin in an **included build**
called `build-logic` rather than in `buildSrc`, because editing `buildSrc` forces the *whole* project
to reconfigure (its main risk), while an included build only affects the plugin and its users, and can
be tested. The plugin doesn't change *how* features are removed — that's still Gradle product flavors
and per-flavor dependencies — it just moves that recipe to a safer home. The store data itself is a
compiled Kotlin table (no external file), so there's nothing for the configuration cache to mis-track,
and the plugin validates feature names so typos fail fast.

---

## Glossary

- **Build logic** — code in `build.gradle.kts` that runs at *configure time* to shape the build.
- **`buildSrc`** — a special folder for shared build code; changes reconfigure the whole project.
- **Convention plugin** — a plugin you write to bundle reusable build setup; modules `apply` it.
- **Included build (`build-logic`)** — a separate side-build that produces plugins for the main build.
- **Product flavor** — a Gradle build variant (storeA/storeB/…) that can have its own dependencies.
- **Configuration cache** — Gradle caching of the configure phase; speeds builds but can serve stale
  data if inputs aren't declared.
- **`ValueSource`** — the supported way to read an external input (like a file) so the cache tracks it.
