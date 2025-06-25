package net.mercuryksm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import kmpbluetoothpoc.composeapp.generated.resources.Res
import kmpbluetoothpoc.composeapp.generated.resources.compose_multiplatform
import kotlin.time.Clock

@Composable
@Preview
fun App(
    viewModel: BluetoothViewModel
) {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (viewModel.showBluetoothButton) {
                if (!viewModel.isScanning) {
                    Button(onClick = {
                        viewModel.startStreamingScan()
                    }) {
                        Text("Start Bluetooth Scan")
                    }
                } else {
                    Button(onClick = {
                        viewModel.stopScan()
                    }) {
                        Text("Stop Scanning...")
                    }
                }
                if (viewModel.deviceList.isNotEmpty()) {
                    LazyColumn {
                        items(viewModel.deviceList) { device ->
                            ListItem(
                                headlineContent = { Text("Name: ${device.name}") },
                                supportingContent = { Text("Address: ${device.address}") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.connectDevice(device)
                                    }
                            )
                        }
                    }
                }
            } else {
                Text("Bluetooth is not available on this device.")
            }
            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
            
            // Permission error dialog
            viewModel.permissionErrorMessage?.let { errorMessage ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearPermissionError() },
                    title = { Text("Bluetooth Permission Required") },
                    text = { 
                        Text("This app needs Bluetooth permissions to scan for devices. Please grant the required permissions in your device settings.\n\nError: $errorMessage")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearPermissionError() }) {
                            Text("OK")
                        }
                    }
                )
            }
            
            // Connection error dialog
            viewModel.connectionErrorMessage?.let { errorMessage ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearConnectionError() },
                    title = { Text("Connection Failed") },
                    text = { 
                        Text(errorMessage)
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearConnectionError() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}
