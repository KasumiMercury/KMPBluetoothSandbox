package net.mercuryksm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.mercuryksm.device.Device

class BluetoothViewModel(
    private val bluetoothProvider: BluetoothProvider
) {
    // Bluetooth availability state
    var showBluetoothButton by mutableStateOf(bluetoothProvider.isBluetoothAvailable())
        private set

    // Scanning state
    var isScanning by mutableStateOf(false)
        private set
    
    var deviceList by mutableStateOf<List<Device>>(emptyList())
        private set

    // Error states
    var permissionErrorMessage by mutableStateOf<String?>(null)
        private set
        
    var connectionErrorMessage by mutableStateOf<String?>(null)
        private set

    // Connection state
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED
    }

    // Scanning operations
    fun startStreamingScan() {
        clearPermissionError()
        deviceList = emptyList()
        isScanning = true
        
        try {
            bluetoothProvider.startDeviceScan(
                onDeviceFound = { device ->
                    CoroutineScope(Dispatchers.Main).launch {
                        addDeviceToList(device)
                    }
                },
                onScanComplete = { devices ->
                    CoroutineScope(Dispatchers.Main).launch {
                        isScanning = false
                        deviceList = devices
                    }
                },
                onScanFailed = { errorMessage ->
                    CoroutineScope(Dispatchers.Main).launch {
                        isScanning = false
                        permissionErrorMessage = errorMessage
                    }
                }
            )
        } catch (e: BluetoothPermissionException) {
            isScanning = false
            permissionErrorMessage = e.message
        }
    }
    
    fun stopScan() {
        isScanning = false
        bluetoothProvider.stopDeviceScan()
    }

    private fun addDeviceToList(device: Device) {
        val currentList = deviceList.toMutableList()
        if (!currentList.any { it.address == device.address }) {
            currentList.add(device)
            deviceList = currentList
        }
    }

    // Connection operations
    fun connectDevice(device: Device) {
        if (connectionState == ConnectionState.CONNECTING) return

        clearConnectionError()
        connectionState = ConnectionState.CONNECTING
        
        try {
            bluetoothProvider.connect(
                device = device,
                onConnected = {
                    CoroutineScope(Dispatchers.Main).launch {
                        connectionState = ConnectionState.CONNECTED
                    }
                },
                onConnectionFailed = { errorMessage ->
                    CoroutineScope(Dispatchers.Main).launch {
                        connectionState = ConnectionState.FAILED
                        connectionErrorMessage = "Failed to connect to ${device.name}: $errorMessage"
                    }
                }
            )
        } catch (e: Exception) {
            connectionState = ConnectionState.FAILED
            connectionErrorMessage = "Failed to connect to ${device.name}: ${e.message}"
        }
    }

    // Error management
    fun clearPermissionError() {
        permissionErrorMessage = null
    }

    fun clearConnectionError() {
        connectionErrorMessage = null
    }
}
