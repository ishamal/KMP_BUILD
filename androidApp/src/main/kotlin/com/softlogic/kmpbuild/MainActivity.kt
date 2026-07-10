package com.softlogic.kmpbuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The features compiled into this store's build, aggregated by Metro (@ContributesIntoSet).
        val enabledFeatures = createAppGraph().features.map { it.id }.toSet()
        setContent {
            HomeScreen(enabledFeatures)
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen(setOf("cart", "settings", "orders"))
}
