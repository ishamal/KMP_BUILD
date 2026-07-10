package com.softlogic.kmpbuild.core

/**
 * DI scope marker. The per-platform `AppGraph` aggregates every `@ContributesIntoSet(AppScope::class)`
 * contribution on its compile classpath — which, thanks to per-store linking, is exactly the store's
 * shipped features.
 */
abstract class AppScope private constructor()
