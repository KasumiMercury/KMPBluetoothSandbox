package net.mercuryksm

import net.mercuryksm.device.Device
import platform.UIKit.UIDevice

/**
 * iOS platform implementation.
 */
class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

/**
 * iOS implementation of BluetoothProvider.
 * Currently provides stub implementations as iOS Bluetooth functionality is not implemented.
 */
class IOSBluetoothProvider : BluetoothProvider {
    
    companion object {
        private const val UNSUPPORTED_MESSAGE = "Bluetooth functionality is not supported on iOS in this implementation"
    }
    
    /**
     * Bluetooth is currently not available on iOS in this implementation.
     */
    override fun isBluetoothAvailable(): Boolean = false
    
    /**
     * Device scanning is not supported on iOS in this implementation.
     */
    override fun startDeviceScan(
        onDeviceFound: (Device) -> Unit,
        onScanComplete: (List<Device>) -> Unit,
        onScanFailed: (String) -> Unit
    ) {
        throw UnsupportedOperationException(UNSUPPORTED_MESSAGE)
    }
    
    /**
     * Stopping device scan is not supported on iOS in this implementation.
     */
    override fun stopDeviceScan() {
        throw UnsupportedOperationException(UNSUPPORTED_MESSAGE)
    }

    /**
     * Device connection is not supported on iOS in this implementation.
     */
    override fun connect(
        device: Device,
        onConnected: (() -> Unit)?,
        onConnectionFailed: ((String) -> Unit)?
    ) {
        throw UnsupportedOperationException(UNSUPPORTED_MESSAGE)
    }
    
    /**
     * Device disconnection is not supported on iOS in this implementation.
     */
    override fun disconnect() {
        throw UnsupportedOperationException(UNSUPPORTED_MESSAGE)
    }
}

actual fun getBluetoothProvider(): BluetoothProvider = IOSBluetoothProvider()
