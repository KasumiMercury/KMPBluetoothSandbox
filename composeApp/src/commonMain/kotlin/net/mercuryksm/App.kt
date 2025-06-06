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
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
                Button(onClick = {
                    try {
                        viewModel.loadDeviceList()
                    } catch (e: Exception) {
                        // TODO: improve error display e.g. show a snackbar or dialog
                        println("Error loading device list: ${e.message}")
                    }
                }) {
                    Text("Bluetooth is available on this device.")
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
                                        try {
                                            viewModel.connectDevice(device)
                                        } catch (e: Exception) {
                                            // TODO: improve error display e.g. show a snackbar or dialog
                                            println("Error connecting device: ${e.message}")
                                        }
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
        }
    }
}
