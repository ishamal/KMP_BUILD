package com.softlogic.kmpbuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.softlogic.kmpbuild.core.MetroAppComponentProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The app-scoped graph is created in KmpBuildApp; read the store's features + contributed
        // screens + ViewModel factory from it (aggregated by Metro) rather than rebuilding per Activity.
        val enabledFeatures = (application as MetroAppComponentProvider).features.map { it.id }.toSet()
        val androidProvider = application as AndroidScreenProvider
        setContent {
            AppRoot(
                enabledFeatures = enabledFeatures,
                screens = androidProvider.androidScreens,
                viewModelFactory = androidProvider.metroViewModelFactory,
            )
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen(setOf("cart", "settings", "orders"), onOpen = {})
}
