package com.softlogic.kmpbuild

import android.app.Application
import com.softlogic.kmpbuild.core.AndroidFeatureScreen
import com.softlogic.kmpbuild.core.HomeFeature
import com.softlogic.kmpbuild.core.MetroAppComponentProvider
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * Creates the app-scoped Metro [AppGraph] once per process and exposes it as the
 * [MetroAppComponentProvider]. Building the graph is not free, so it must live at Application scope,
 * not be recreated per Activity.
 *
 * Any Context can retrieve the app's components without knowing this concrete class:
 * ```
 * (context.applicationContext as MetroAppComponentProvider).features
 * ```
 */
class KmpBuildApp : Application(), MetroAppComponentProvider, AndroidScreenProvider {

    // Lazily created on first access, then reused for the whole process lifetime.
    private val appGraph: AppGraph by lazy { createAppGraph() }

    override val features: Set<HomeFeature> get() = appGraph.features

    override val androidScreens: Set<AndroidFeatureScreen> get() = appGraph.androidScreens

    override val metroViewModelFactory: MetroViewModelFactory get() = appGraph.metroViewModelFactory
}
