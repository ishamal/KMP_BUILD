package com.softlogic.kmpbuild.core

/**
 * The app-facing surface of the DI graph — the "components" (app-level dependencies) the graph
 * exposes to the app, decoupled from *how* Metro builds them.
 *
 * Each platform's `@DependencyGraph AppGraph` implements this, so app/UI code (and tests) can depend
 * on this stable contract instead of the concrete Metro graph type. Add an accessor here when the app
 * needs a new app-wide dependency; the graph satisfies it via `@ContributesIntoSet` / `@Provides`.
 */
interface MetroAppComponentProvider {
    /** Every feature shipped in this store's build (aggregated by Metro from `@ContributesIntoSet`). */
    val features: Set<HomeFeature>
}
