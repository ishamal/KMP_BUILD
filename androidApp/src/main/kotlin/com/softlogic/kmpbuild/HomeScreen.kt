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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Nav tiles the home can show. `login` is the auth gate, not a home tile. */
private val HOME_ITEMS: List<Pair<String, String>> = listOf(
    "cart" to "Cart",
    "settings" to "Settings",
    "orders" to "Orders",
)

/**
 * Native Android Jetpack Compose home. [enabledFeatures] are the feature ids compiled into this
 * store's flavor (from BuildConfig.STORE_FEATURES) — a tile is tappable only if its feature shipped.
 */
@Composable
fun HomeScreen(enabledFeatures: Set<String>) {
    MaterialTheme {
        var opened by remember { mutableStateOf<String?>(null) }
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

            HOME_ITEMS.forEach { (id, title) ->
                val enabled = id in enabledFeatures
                Button(
                    onClick = { opened = id },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (enabled) title else "$title — not in this store")
                }
            }

            opened?.let { Text("Opened \"$it\"") }
        }
    }
}
