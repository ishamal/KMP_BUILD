package com.softlogic.kmpbuild.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * Shared infrastructure for a feature "entry composable" (the Metro DI pattern). A feature's
 * [AndroidFeatureScreen.Content] creates its screen-scoped Metro graph extension, then wraps its
 * screen in this so that:
 *
 *  - a **per-entry** [ViewModelStoreOwner] backs `metroViewModel()`, so the screen-scoped ViewModel
 *    is created fresh for each navigation entry and cleared when the entry is removed — rather than
 *    cached in the Activity store (which would hand a later entry a stale ViewModel from an already
 *    disposed graph);
 *  - the screen-scoped [MetroViewModelFactory] is provided via [LocalMetroViewModelFactory];
 *  - [onExit] runs when the entry leaves composition (pop), to close the graph's scoped resources.
 */
@Composable
fun FeatureScreenEntry(
    viewModelFactory: MetroViewModelFactory,
    onExit: () -> Unit,
    content: @Composable () -> Unit,
) {
    val storeOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            storeOwner.viewModelStore.clear()
            onExit()
        }
    }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides storeOwner,
        LocalMetroViewModelFactory provides viewModelFactory,
    ) {
        content()
    }
}
