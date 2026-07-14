package com.softlogic.kmpbuild

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softlogic.kmpbuild.core.FeatureId

/**
 * Native Android Jetpack Compose home. The tile catalog is [FeatureId.homeTiles] (every tile the app
 * knows about, shipped or not — `login` is the auth gate, not a tile). [enabledFeatures] are the ids
 * compiled into this store's flavor, aggregated at runtime from the Metro graph (Set<HomeFeature> via
 * @ContributesIntoSet) — a tile is tappable only if its feature shipped. [onOpen] navigates to a
 * feature's screen (see AppRoot).
 */
@Composable
fun HomeScreen(
    enabledFeatures: Set<FeatureId>,
    onOpen: (FeatureId) -> Unit,
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeDrawingPadding()
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Home (Android)", style = MaterialTheme.typography.headlineMedium)

            FeatureId.homeTiles.forEach { id ->
                val enabled = id in enabledFeatures
                Button(
                    onClick = { onOpen(id) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (enabled) id.title else "${id.title} — not in this store")
                }
            }
        }
    }
}
