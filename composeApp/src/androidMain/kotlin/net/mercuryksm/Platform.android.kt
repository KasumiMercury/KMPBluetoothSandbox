package net.mercuryksm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import net.mercuryksm.device.Device

/**
 * Android platform implementation.
 */
class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

/**
 * Android implementation of BluetoothProvider using Android's Bluetooth LE APIs.
 * Provides scanning and connection functionality for Bluetooth devices.
 */
class AndroidBluetoothProvider(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) : BluetoothProvider {
    
    companion object {
        private const val TAG = "AndroidBluetoothProvider"
        private const val SCAN_TIMEOUT_MS = 3000L
        // TODO: Make service UUID configurable
        private val SERVICE_UUID = UUID.fromString("2c081c6d-61dd-4af8-ac2f-17f2ea5e5214")
    }

    // Device management
    private val deviceCache = mutableMapOf<String, BluetoothDevice>()

    // Scanning state
    private var activeScanner: BluetoothLeScanner? = null
    private var activeScanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    
    // Callbacks for streaming scan
    private var onDeviceFoundCallback: ((Device) -> Unit)? = null
    private var onScanCompleteCallback: ((List<Device>) -> Unit)? = null
    private var onScanFailedCallback: ((String) -> Unit)? = null

    override fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Stops the currently active scan and clears all callbacks.
     */
    private fun stopActiveScan() {
        try {
            activeScanner?.stopScan(activeScanCallback ?: return)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to stop scan due to security exception", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
        }
        clearScanState()
    }

    /**
     * Clears all scanning-related state and callbacks.
     */
    private fun clearScanState() {
        activeScanner = null
        activeScanCallback = null
        onDeviceFoundCallback = null
        onScanCompleteCallback = null
        onScanFailedCallback = null
    }

    /**
     * Cleanup method to stop any active scans and clear resources.
     */
    fun cleanup() {
        scanRunnable?.let { handler.removeCallbacks(it) }
        activeScanCallback?.let {
            try {
                activeScanner?.stopScan(it)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to stop scan on cleanup", e)
            }
        }
        activeScanner = null
        activeScanCallback = null
        scanRunnable = null
    }

    /**
     * Checks if required Bluetooth permissions are granted.
     * @return error message if permissions are missing, null if all permissions are granted
     */
    private fun checkBluetoothPermissions(): String? {
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return "BLUETOOTH_SCAN permission is not granted"
        }
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return "BLUETOOTH_CONNECT permission is not granted"
        }
        return null
    }


    override fun startDeviceScan(
        onDeviceFound: (Device) -> Unit,
        onScanComplete: (List<Device>) -> Unit,
        onScanFailed: (String) -> Unit
    ) {
        stopActiveScan()

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onScanFailed("Bluetooth is not enabled or not available.")
            return
        }

        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(tag, "BLUETOOTH_SCAN permission is not granted.")
            onScanFailed("BLUETOOTH_SCAN permission is not granted.")
            return
        }

        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(tag, "BLUETOOTH_CONNECT permission is not granted. Device names may not be available.")
            onScanFailed("BLUETOOTH_CONNECT permission is not granted.")
            return
        }

        val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(tag, "BluetoothLeScanner is not available.")
            onScanFailed("BluetoothLeScanner is not available.")
            return
        }

        // Store callbacks
        onDeviceFoundCallback = onDeviceFound
        onScanCompleteCallback = onScanComplete
        onScanFailedCallback = onScanFailed

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = mutableListOf<ScanFilter>()
        val serviceFilter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .build()
        filters.add(serviceFilter)

        val foundDevices = mutableMapOf<String, BluetoothDevice>()

        val scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                Log.d(
                    TAG,
                    "onScanResult: Found device -> Name: ${device.name ?: "N/A"}, Address: ${device.address}, RSSI: ${result.rssi}"
                )
                if (!foundDevices.containsKey(device.address)) {
                    foundDevices[device.address] = device
                    deviceCache[device.address] = device
                    
                    val discoveredDevice = Device(
                        name = device.name ?: "Unknown Device",
                        address = device.address
                    )
                    // Stream the device immediately to UI
                    onDeviceFoundCallback?.invoke(discoveredDevice)
                }
            }

            @SuppressLint("MissingPermission")
            override fun onBatchScanResults(results: List<ScanResult>) {
                Log.d(TAG, "onBatchScanResults: ${results.size} results")
                for (result in results) {
                    val device = result.device
                    if (!foundDevices.containsKey(device.address)) {
                        Log.d(
                            TAG,
                            "onBatchScanResults: Found device -> Name: ${device.name ?: "N/A"}, Address: ${device.address}"
                        )
                        foundDevices[device.address] = device
                        deviceCache[device.address] = device
                        
                        val discoveredDevice = Device(
                            name = device.name ?: "Unknown Device",
                            address = device.address
                        )
                        // Stream the device immediately to UI
                        onDeviceFoundCallback?.invoke(discoveredDevice)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorMessage = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
                    SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    else -> "Unknown error"
                }
                Log.e(TAG, "Scan failed with error code: $errorCode - $errorMessage")
                onScanFailedCallback?.invoke(errorMessage)
            }
        }

        activeScanner = scanner
        activeScanCallback = scanCallback

        Log.d(TAG, "Starting Bluetooth streaming scan...")
        scanner.startScan(
            filters,
            settings,
            scanCallback
        )

        scanRunnable = Runnable {
            Log.d(TAG, "Stopping Bluetooth scan after timeout...")
            try {
                scanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to stop scan due to security exception", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop scan", e)
            }

            activeScanner = null
            activeScanCallback = null

            val deviceList = foundDevices.values.map { device ->
                Device(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            }
            Log.d(TAG, "Scan completed. Found ${deviceList.size} devices.")
            onScanCompleteCallback?.invoke(deviceList)
            clearScanState()
        }

        handler.postDelayed(scanRunnable!!, SCAN_TIMEOUT_MS)
    }

    override fun stopDeviceScan() {
        scanRunnable?.let { 
            handler.removeCallbacks(it)
            scanRunnable = null
        }
        stopActiveScan()
    }

    override fun connect(
        device: Device,
        onConnected: (() -> Unit)?,
        onConnectionFailed: ((String) -> Unit)?
    ) {
        // Check permissions first
        val permissionError = checkBluetoothPermissions()
        if (permissionError != null) {
            throw BluetoothPermissionException(permissionError)
        }

        // Validate Bluetooth state
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            throw UnsupportedOperationException("Bluetooth is not enabled or not available")
        }

        // Get device from cache
        val bluetoothDevice = deviceCache[device.address]
            ?: throw IllegalArgumentException("Device not found in cache: ${device.address}")

        // Start connection
        connectToGattServer(bluetoothDevice, onConnected, onConnectionFailed)
    }

    /**
     * Connects to the GATT server on the specified device.
     */
    private fun connectToGattServer(
        bluetoothDevice: BluetoothDevice,
        onConnected: (() -> Unit)?,
        onConnectionFailed: ((String) -> Unit)?
    ) {
        bluetoothDevice.connectGatt(
            context,
            false,
            createGattCallback(onConnected, onConnectionFailed)
        )
    }

    /**
     * Creates a GATT callback for handling connection events.
     */
    private fun createGattCallback(
        onConnected: (() -> Unit)?,
        onConnectionFailed: ((String) -> Unit)?
    ): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                handleConnectionStateChange(gatt, status, newState, onConnected, onConnectionFailed)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                handleServicesDiscovered(gatt, status, onConnectionFailed)
            }
        }
    }

    /**
     * Handles GATT connection state changes.
     */
    private fun handleConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int,
        onConnected: (() -> Unit)?,
        onConnectionFailed: ((String) -> Unit)?
    ) {
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            onConnectionFailed?.invoke("BLUETOOTH_CONNECT permission is not granted")
            return
        }

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.d(TAG, "Connected to GATT server")
                gatt.discoverServices()
                onConnected?.invoke()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.d(TAG, "Disconnected from GATT server")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onConnectionFailed?.invoke("Connection failed with status: $status")
                }
            }
        }
    }

    /**
     * Handles GATT service discovery results.
     */
    private fun handleServicesDiscovered(
        gatt: BluetoothGatt,
        status: Int,
        onConnectionFailed: ((String) -> Unit)?
    ) {
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            onConnectionFailed?.invoke("BLUETOOTH_CONNECT permission is not granted")
            return
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Services discovered successfully")
        } else {
            Log.e(TAG, "Failed to discover services, status: $status")
            onConnectionFailed?.invoke("Failed to discover services, status: $status")
        }
    }

    override fun disconnect() {
        // TODO: Implement disconnect logic - store and manage GATT connections
        Log.w(TAG, "Disconnect functionality not yet implemented")
    }
}

// TODO: fix this
@SuppressLint("StaticFieldLeak")
object ContextHolder {
    private lateinit var _context: Context
    val context: Context
        get() = _context

    val isInitialized: Boolean
        get() = ::_context.isInitialized

    fun initialize(context: Context) {
        _context = context.applicationContext
    }
}

actual fun getBluetoothProvider(): BluetoothProvider {
    if (!ContextHolder.isInitialized) {
        throw IllegalStateException("ContextHolder is not initialized. Call ContextHolder.initialize(context) first.")
    }

    val context = ContextHolder.context

    val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    return AndroidBluetoothProvider(
        context.applicationContext,
        bluetoothAdapter,
    )
}
