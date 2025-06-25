package net.mercuryksm

import net.mercuryksm.device.Device

/**
 * Platform abstraction interface providing platform-specific information.
 */
interface Platform {
    val name: String
}

/**
 * Expected function to get the platform-specific implementation.
 */
expect fun getPlatform(): Platform

/**
 * Bluetooth functionality provider interface.
 * Abstracts Bluetooth operations across different platforms.
 */
interface BluetoothProvider {
    
    /**
     * Checks if Bluetooth is available on the current platform.
     * @return true if Bluetooth is available, false otherwise
     */
    fun isBluetoothAvailable(): Boolean
    
    /**
     * Starts scanning for Bluetooth devices.
     * @param onDeviceFound Callback invoked when a device is discovered during scanning
     * @param onScanComplete Callback invoked when scanning completes with all found devices
     * @param onScanFailed Callback invoked when scanning fails with error message
     */
    fun startDeviceScan(
        onDeviceFound: (Device) -> Unit,
        onScanComplete: (List<Device>) -> Unit,
        onScanFailed: (String) -> Unit
    )
    
    /**
     * Stops the current device scan operation.
     */
    fun stopDeviceScan()

    /**
     * Attempts to connect to the specified Bluetooth device.
     * Connection is asynchronous and results are provided via callbacks.
     * 
     * @param device The device to connect to
     * @param onConnected Optional callback invoked when connection succeeds
     * @param onConnectionFailed Optional callback invoked when connection fails with error message
     * @throws IllegalArgumentException if device is invalid or not found in cache
     * @throws SecurityException if required Bluetooth permissions are not granted
     * @throws UnsupportedOperationException if Bluetooth is not enabled or available
     */
    fun connect(
        device: Device,
        onConnected: (() -> Unit)? = null,
        onConnectionFailed: ((String) -> Unit)? = null
    )

    /**
     * Disconnects from the currently connected device.
     * Implementation is platform-specific.
     */
    fun disconnect()
}

/**
 * Expected function to get the platform-specific BluetoothProvider implementation.
 */
expect fun getBluetoothProvider(): BluetoothProvider
