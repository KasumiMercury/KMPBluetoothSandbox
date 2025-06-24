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
    var showBluetoothButton by mutableStateOf(bluetoothProvider.isBluetoothAvailable())
        private set

    var deviceList by mutableStateOf<List<Device>>(emptyList())
        private set

    var permissionErrorMessage by mutableStateOf<String?>(null)
        private set
        
    var isScanning by mutableStateOf(false)
        private set

    
    fun startStreamingScan() {
        permissionErrorMessage = null
        deviceList = emptyList()
        isScanning = true
        
        try {
            bluetoothProvider.startDeviceScan(
                onDeviceFound = { device ->
                    CoroutineScope(Dispatchers.Main).launch {
                        val currentList = deviceList.toMutableList()
                        // Check if device already exists to avoid duplicates
                        if (!currentList.any { it.address == device.address }) {
                            currentList.add(device)
                            deviceList = currentList
                        }
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

    fun clearPermissionError() {
        permissionErrorMessage = null
    }

    enum class ConnectionState {
        CONNECTED, DISCONNECTED, CONNECTING, FAILED
    }

    var connectionState by mutableStateOf<ConnectionState>(ConnectionState.DISCONNECTED)
        private set

    fun connectDevice(device: Device) {
        if (connectionState == ConnectionState.CONNECTING) return

        connectionState = ConnectionState.CONNECTING
        try {
            bluetoothProvider.connect(device)
            connectionState = ConnectionState.CONNECTED
        } catch (e: Exception) {
            connectionState = ConnectionState.FAILED
            throw e
        }
    }
}
