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

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

class AndroidBluetoothProvider(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
) : BluetoothProvider {
    private val tag = "AndroidBluetoothProvider"

    private val deviceCache = mutableMapOf<String, BluetoothDevice>()

    // TODO: provide a way to configure this UUID
    private val serviceUuid = UUID.fromString("2c081c6d-61dd-4af8-ac2f-17f2ea5e5214")

    private var activeScanner: BluetoothLeScanner? = null
    private var activeScanCallback: ScanCallback? = null

    // TODO: test this with private val handler
    val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    
    // Streaming scan callbacks
    private var onDeviceFoundCallback: ((Device) -> Unit)? = null
    private var onScanCompleteCallback: ((List<Device>) -> Unit)? = null
    private var onScanFailedCallback: ((String) -> Unit)? = null

    override fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private fun stopActiveScan() {
        try {
            activeScanner?.stopScan(activeScanCallback ?: return)
        } catch (e: SecurityException) {
            Log.w(tag, "Failed to stop scan due to security exception", e)
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop scan", e)
        }
        activeScanner = null
        activeScanCallback = null
        onDeviceFoundCallback = null
        onScanCompleteCallback = null
        onScanFailedCallback = null
    }

    fun cleanup() {
        scanRunnable?.let { handler.removeCallbacks(it) }
        activeScanCallback?.let {
            try {
                activeScanner?.stopScan(it)
            } catch (e: SecurityException) {
                Log.w(tag, "Failed to stop scan on cleanup", e)
            }
        }
        activeScanner = null
        activeScanCallback = null
        scanRunnable = null
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
            .setServiceUuid(android.os.ParcelUuid(serviceUuid))
            .build()
        filters.add(serviceFilter)

        val foundDevices = mutableMapOf<String, BluetoothDevice>()

        val scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                Log.d(
                    tag,
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
                Log.d(tag, "onBatchScanResults: ${results.size} results")
                for (result in results) {
                    val device = result.device
                    if (!foundDevices.containsKey(device.address)) {
                        Log.d(
                            tag,
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
                Log.e(tag, "Scan failed with error code: $errorCode - $errorMessage")
                onScanFailedCallback?.invoke(errorMessage)
            }
        }

        activeScanner = scanner
        activeScanCallback = scanCallback

        Log.d(tag, "Starting Bluetooth streaming scan...")
        scanner.startScan(
            filters,
            settings,
            scanCallback
        )

        scanRunnable = Runnable {
            Log.d(tag, "Stopping Bluetooth scan after 3 seconds...")
            try {
                scanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.w(tag, "Failed to stop scan due to security exception", e)
            } catch (e: Exception) {
                Log.e(tag, "Failed to stop scan", e)
            }

            activeScanner = null
            activeScanCallback = null

            val deviceList = foundDevices.values.map { device ->
                Device(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            }
            Log.d(tag, "Scan completed. Found ${deviceList.size} devices.")
            onScanCompleteCallback?.invoke(deviceList)
            
            // Clear callbacks after completion
            onDeviceFoundCallback = null
            onScanCompleteCallback = null
            onScanFailedCallback = null
        }

        // TODO: provide a way to configure this delay
        handler.postDelayed(scanRunnable!!, 3000)
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
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val bluetoothDevice = deviceCache[device.address]
                ?: throw IllegalArgumentException("Device not found in cache: ${device.address}")

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                throw UnsupportedOperationException("Bluetooth is not enabled or not available.")
            }

            bluetoothDevice.connectGatt(
                context,
                false,
                object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            onConnectionFailed?.invoke("BLUETOOTH_CONNECT permission is not granted")
                            return
                        }
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt.discoverServices()
                            onConnected?.invoke()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                onConnectionFailed?.invoke("Connection failed with status: $status")
                            }
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            onConnectionFailed?.invoke("BLUETOOTH_CONNECT permission is not granted")
                            return
                        }
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            onConnectionFailed?.invoke("Failed to discover services, status: $status")
                        }
                    }
                }
            )
        } else {
            throw BluetoothPermissionException("BLUETOOTH_CONNECT permission is not granted.")
        }
    }

    override fun disconnect() {
        TODO("Implement disconnect logic")
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
