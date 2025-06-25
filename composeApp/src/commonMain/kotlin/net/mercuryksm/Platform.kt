package net.mercuryksm

import net.mercuryksm.device.Device

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

interface BluetoothProvider {
    fun isBluetoothAvailable(): Boolean
    fun startDeviceScan(
        onDeviceFound: (Device) -> Unit,
        onScanComplete: (List<Device>) -> Unit,
        onScanFailed: (String) -> Unit
    )
    fun stopDeviceScan()

    /**
     * Attempts to connect to the specified device.
     * @param device The device to connect to
     * @param onConnected Callback when connection succeeds
     * @param onConnectionFailed Callback when connection fails
     * @throws IllegalArgumentException if device is invalid
     * @throws SecurityException if permissions are insufficient
     */
    fun connect(
        device: Device,
        onConnected: (() -> Unit)? = null,
        onConnectionFailed: ((String) -> Unit)? = null
    )

    /**
     * Disconnects from the currently connected device.
     */
    fun disconnect()
}

expect fun getBluetoothProvider(): BluetoothProvider
