package com.softlogic.kmpbuild.features.orders.real

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Native Android (Compose) screen for the Orders feature. Binds to [OrdersViewModel]; the VM is
 * obtained from the Compose `viewModel()` factory (scoped to the hosting Activity). Contributed into
 * the app's nav host via [OrdersScreenContribution].
 */
@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    viewModel: OrdersViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding()
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Orders", style = MaterialTheme.typography.headlineMedium)

            state.orders.forEach { Text("• $it") }

            Button(onClick = { viewModel.onEvent(OrdersEvent.Refresh) }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh")
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}
