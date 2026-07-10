package com.softlogic.kmpbuild.core

/**
 * The multibound feature contract. Each shipped feature's `:real` module contributes one
 * implementation into the app's Metro graph via `@ContributesIntoSet(AppScope::class)`, so the
 * running app's `Set<HomeFeature>` contains exactly the features compiled into its store build.
 */
interface HomeFeature {
    val id: String
    val title: String
}
