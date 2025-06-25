package net.mercuryksm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.mercuryksm.device.Device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBluetoothProviderTest {

    private lateinit var mockAdapter: BluetoothAdapter
    private lateinit var mockContext: Context
    private lateinit var provider: AndroidBluetoothProvider
    private lateinit var mockScanner: BluetoothLeScanner

    @Before
    fun setUp() {
        mockAdapter = mockk(relaxed = true)
        mockContext = mockk<Context>(relaxed = true)
        mockScanner = mockk(relaxed = true)

        every { mockContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_GRANTED
        every { mockContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_GRANTED
        every { mockContext.applicationContext } returns mockContext
        every { mockAdapter.bluetoothLeScanner } returns mockScanner
        every { mockAdapter.isEnabled } returns true // デフォルトで有効

        provider = AndroidBluetoothProvider(mockContext, mockAdapter)
    }

    @Test
    fun isBluetoothAvailable_returnsFalse_whenAdapterIsNull() {
        provider = AndroidBluetoothProvider(mockContext, null)
        assertFalse(provider.isBluetoothAvailable())
    }

    @Test
    fun isBluetoothAvailable_returnsTrue_whenAdapterIsEnabled() {
        every { mockAdapter.isEnabled } returns true
        assertTrue(provider.isBluetoothAvailable())
    }

    @Test
    fun isBluetoothAvailable_returnsFalse_whenAdapterIsDisabled() {
        every { mockAdapter.isEnabled } returns false
        assertFalse(provider.isBluetoothAvailable())
    }

    @Test
    fun startDeviceScan_callsOnScanFailed_whenBluetoothDisabled() {
        every { mockAdapter.isEnabled } returns false
        
        var failedMessage: String? = null
        provider.startDeviceScan(
            onDeviceFound = { fail("onDeviceFound should not be called") },
            onScanComplete = { fail("onScanComplete should not be called") },
            onScanFailed = { failedMessage = it }
        )
        
        assertEquals("Bluetooth is not enabled or not available", failedMessage)
    }

    @Test
    fun startDeviceScan_callsOnScanFailed_whenScanPermissionNotGranted() {
        every { mockContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_DENIED
        
        var failedMessage: String? = null
        provider.startDeviceScan(
            onDeviceFound = { fail("onDeviceFound should not be called") },
            onScanComplete = { fail("onScanComplete should not be called") },
            onScanFailed = { failedMessage = it }
        )
        
        assertEquals("BLUETOOTH_SCAN permission is not granted", failedMessage)
    }

    @Test
    fun startDeviceScan_callsOnScanFailed_whenConnectPermissionNotGranted() {
        every { mockContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_DENIED
        
        var failedMessage: String? = null
        provider.startDeviceScan(
            onDeviceFound = { fail("onDeviceFound should not be called") },
            onScanComplete = { fail("onScanComplete should not be called") },
            onScanFailed = { failedMessage = it }
        )
        
        assertEquals("BLUETOOTH_CONNECT permission is not granted", failedMessage)
    }

    @Test
    fun startDeviceScan_callsOnScanFailed_whenScannerNotAvailable() {
        every { mockAdapter.bluetoothLeScanner } returns null
        
        var failedMessage: String? = null
        provider.startDeviceScan(
            onDeviceFound = { fail("onDeviceFound should not be called") },
            onScanComplete = { fail("onScanComplete should not be called") },
            onScanFailed = { failedMessage = it }
        )
        
        assertEquals("BluetoothLeScanner is not available", failedMessage)
    }

    @Test
    fun startDeviceScan_callsOnDeviceFound_whenDeviceDiscovered() {
        val mockBluetoothDevice = mockk<BluetoothDevice>()
        every { mockBluetoothDevice.address } returns "00:11:22:33:AA:BB"
        every { mockBluetoothDevice.name } returns "TestDevice"

        val mockScanResult = mockk<ScanResult>()
        every { mockScanResult.device } returns mockBluetoothDevice
        every { mockScanResult.rssi } returns -50

        val scanCallbackSlot = slot<ScanCallback>()
        every { mockScanner.startScan(any(), any(), capture(scanCallbackSlot)) } just Runs

        var foundDevice: Device? = null
        provider.startDeviceScan(
            onDeviceFound = { foundDevice = it },
            onScanComplete = { },
            onScanFailed = { fail("onScanFailed should not be called: $it") }
        )

        // Simulate device discovery
        scanCallbackSlot.captured.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)

        assertEquals("TestDevice", foundDevice?.name)
        assertEquals("00:11:22:33:AA:BB", foundDevice?.address)
    }

    @Test
    fun startDeviceScan_callsOnScanFailed_whenScanFails() {
        val scanCallbackSlot = slot<ScanCallback>()
        every { mockScanner.startScan(any(), any(), capture(scanCallbackSlot)) } answers {
            scanCallbackSlot.captured.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
        }

        var failedMessage: String? = null
        provider.startDeviceScan(
            onDeviceFound = { fail("onDeviceFound should not be called") },
            onScanComplete = { fail("onScanComplete should not be called") },
            onScanFailed = { failedMessage = it }
        )

        assertEquals("Internal error", failedMessage)
    }

    @Test
    fun stopDeviceScan_stopsActiveScan() {
        val scanCallbackSlot = slot<ScanCallback>()
        every { mockScanner.startScan(any(), any(), capture(scanCallbackSlot)) } just Runs
        every { mockScanner.stopScan(any<ScanCallback>()) } just Runs

        provider.startDeviceScan(
            onDeviceFound = { },
            onScanComplete = { },
            onScanFailed = { }
        )

        provider.stopDeviceScan()

        verify { mockScanner.stopScan(scanCallbackSlot.captured) }
    }

    @Test
    fun connect_throwsException_whenBluetoothScanPermissionNotGranted() {
        every { mockContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_DENIED
        
        val device = Device("TestDevice", "00:11:22:33:AA:BB")
        
        try {
            provider.connect(device, onConnected = {}, onConnectionFailed = {})
            fail("Expected BluetoothPermissionException")
        } catch (e: BluetoothPermissionException) {
            assertEquals("BLUETOOTH_SCAN permission is not granted", e.message)
        }
    }

    @Test
    fun connect_throwsException_whenBluetoothConnectPermissionNotGranted() {
        every { mockContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_DENIED
        
        val device = Device("TestDevice", "00:11:22:33:AA:BB")
        
        try {
            provider.connect(device, onConnected = {}, onConnectionFailed = {})
            fail("Expected BluetoothPermissionException")
        } catch (e: BluetoothPermissionException) {
            assertEquals("BLUETOOTH_CONNECT permission is not granted", e.message)
        }
    }

    @Test
    fun connect_throwsException_whenBluetoothAdapterIsNull() {
        provider = AndroidBluetoothProvider(mockContext, null)
        
        val device = Device("TestDevice", "00:11:22:33:AA:BB")
        
        try {
            provider.connect(device, onConnected = {}, onConnectionFailed = {})
            fail("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertEquals("Bluetooth is not enabled or not available", e.message)
        }
    }

    @Test
    fun connect_throwsException_whenBluetoothAdapterIsDisabled() {
        every { mockAdapter.isEnabled } returns false
        
        val device = Device("TestDevice", "00:11:22:33:AA:BB")
        
        try {
            provider.connect(device, onConnected = {}, onConnectionFailed = {})
            fail("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertEquals("Bluetooth is not enabled or not available", e.message)
        }
    }

    @Test
    fun connect_throwsException_whenDeviceNotInCache() {
        val device = Device("TestDevice", "00:11:22:33:AA:BB")
        
        try {
            provider.connect(device, onConnected = {}, onConnectionFailed = {})
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Device not found in cache: 00:11:22:33:AA:BB", e.message)
        }
    }

    @Test
    fun connect_callsConnectGatt_whenValidDevice() {
        // First discover a device to add it to cache
        val mockBluetoothDevice = mockk<BluetoothDevice>(relaxed = true)
        every { mockBluetoothDevice.address } returns "00:11:22:33:AA:BB"
        every { mockBluetoothDevice.name } returns "TestDevice"

        val mockScanResult = mockk<ScanResult>()
        every { mockScanResult.device } returns mockBluetoothDevice
        every { mockScanResult.rssi } returns -50

        val scanCallbackSlot = slot<ScanCallback>()
        every { mockScanner.startScan(any(), any(), capture(scanCallbackSlot)) } just Runs

        provider.startDeviceScan(
            onDeviceFound = { },
            onScanComplete = { },
            onScanFailed = { }
        )

        // Simulate device discovery to add to cache
        scanCallbackSlot.captured.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)

        // Now test connection
        val device = Device("TestDevice", "00:11:22:33:AA:BB")
        
        provider.connect(
            device = device,
            onConnected = {},
            onConnectionFailed = {}
        )

        verify { mockBluetoothDevice.connectGatt(mockContext, false, any()) }
    }
}
