package net.mercuryksm

/**
 * Exception thrown when Bluetooth permissions are not granted.
 * This is a cross-platform exception that can be used in both Android and iOS.
 */
class BluetoothPermissionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
