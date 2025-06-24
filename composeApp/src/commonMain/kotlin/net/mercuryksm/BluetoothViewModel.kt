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

    fun loadDeviceList() {
        permissionErrorMessage = null
        try {
            bluetoothProvider.getDeviceList { devices ->
                CoroutineScope(Dispatchers.Main).launch {
                    deviceList = devices
                }
            }
        } catch (e: BluetoothPermissionException) {
            permissionErrorMessage = e.message
        }
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
