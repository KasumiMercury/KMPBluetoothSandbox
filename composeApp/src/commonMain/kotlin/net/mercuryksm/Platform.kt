package net.mercuryksm

import net.mercuryksm.device.Device

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

interface BluetoothProvider {
    fun isBluetoothAvailable(): Boolean
    fun getDeviceList(callback: (List<Device>) -> Unit)
    fun startDeviceScan(
        onDeviceFound: (Device) -> Unit,
        onScanComplete: (List<Device>) -> Unit,
        onScanFailed: (String) -> Unit
    )
    fun stopDeviceScan()

    /**
     * Attempts to connect to the specified device.
     * @param device The device to connect to
     * @throws IllegalArgumentException if device is invalid
     * @throws SecurityException if permissions are insufficient
     */
    fun connect(device: Device)

    /**
     * Disconnects from the currently connected device.
     */
    fun disconnect()
}

expect fun getBluetoothProvider(): BluetoothProvider
