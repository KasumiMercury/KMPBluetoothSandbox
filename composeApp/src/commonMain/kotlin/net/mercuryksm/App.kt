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

@Composable
@Preview
fun App(viewModel: BluetoothViewModel) {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BluetoothSection(viewModel)
            DemoSection(
                showContent = showContent,
                onToggleContent = { showContent = !showContent }
            )
            ErrorDialogs(viewModel)
        }
    }
}

@Composable
private fun BluetoothSection(viewModel: BluetoothViewModel) {
    if (viewModel.showBluetoothButton) {
        ScanButton(viewModel)
        DeviceList(viewModel)
    } else {
        Text("Bluetooth is not available on this device.")
    }
}

@Composable
private fun ScanButton(viewModel: BluetoothViewModel) {
    if (!viewModel.isScanning) {
        Button(onClick = { viewModel.startStreamingScan() }) {
            Text("Start Bluetooth Scan")
        }
    } else {
        Button(onClick = { viewModel.stopScan() }) {
            Text("Stop Scanning...")
        }
    }
}

@Composable
private fun DeviceList(viewModel: BluetoothViewModel) {
    if (viewModel.deviceList.isNotEmpty()) {
        LazyColumn {
            items(viewModel.deviceList) { device ->
                DeviceListItem(
                    device = device,
                    onDeviceClick = { viewModel.connectDevice(device) }
                )
            }
        }
    }
}

@Composable
private fun DeviceListItem(
    device: net.mercuryksm.device.Device,
    onDeviceClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text("Name: ${device.name}") },
        supportingContent = { Text("Address: ${device.address}") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceClick() }
    )
}

@Composable
private fun DemoSection(
    showContent: Boolean,
    onToggleContent: () -> Unit
) {
    Button(onClick = onToggleContent) {
        Text("Click me!")
    }
    
    AnimatedVisibility(showContent) {
        val greeting = remember { Greeting().greet() }
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(Res.drawable.compose_multiplatform), null)
            Text("Compose: $greeting")
        }
    }
}

@Composable
private fun ErrorDialogs(viewModel: BluetoothViewModel) {
    PermissionErrorDialog(viewModel)
    ConnectionErrorDialog(viewModel)
}

@Composable
private fun PermissionErrorDialog(viewModel: BluetoothViewModel) {
    viewModel.permissionErrorMessage?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPermissionError() },
            title = { Text("Bluetooth Permission Required") },
            text = { 
                Text(
                    "This app needs Bluetooth permissions to scan for devices. " +
                    "Please grant the required permissions in your device settings.\n\n" +
                    "Error: $errorMessage"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPermissionError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ConnectionErrorDialog(viewModel: BluetoothViewModel) {
    viewModel.connectionErrorMessage?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearConnectionError() },
            title = { Text("Connection Failed") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearConnectionError() }) {
                    Text("OK")
                }
            }
        )
    }
}
