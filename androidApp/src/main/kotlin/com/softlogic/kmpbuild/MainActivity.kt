package com.softlogic.kmpbuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Features compiled into this flavor, injected per-store by StoreFeaturesPlugin. */
private fun enabledFeatures(): Set<String> =
    BuildConfig.STORE_FEATURES.split(",").filter { it.isNotBlank() }.toSet()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            HomeScreen(enabledFeatures())
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen(setOf("cart", "settings", "orders"))
}
