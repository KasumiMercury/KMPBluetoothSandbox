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
    fun getDeviceList_throwsException_whenBluetoothDisabled() {
        every { mockAdapter.isEnabled } returns false

        try {
            provider.getDeviceList { }
            fail("UnsupportedOperationException がスローされませんでした")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("Bluetooth is not enabled or not available") == true)
        }
    }

    @Test
    fun getDeviceList_throwsBluetoothPermissionException_whenScanPermissionNotGranted() {
        every { mockContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_DENIED

        try {
            provider.getDeviceList { }
            fail("BluetoothPermissionException がスローされませんでした")
        } catch (e: BluetoothPermissionException) {
            assertTrue(e.message?.contains("BLUETOOTH_SCAN permission is not granted") == true)
        }
    }

    @Test
    fun getDeviceList_returnsEmptyList_whenScannerNotAvailable() {
        every { mockAdapter.bluetoothLeScanner } returns null
        var resultList: List<Device>? = null
        provider.getDeviceList { resultList = it }
        assertTrue(resultList?.isEmpty() == true)
    }

    @Test
    fun getDeviceList_callsScanFailed_whenScanFails() {
        val scanCallbackSlot = slot<ScanCallback>()
        every { mockScanner.startScan(any(), any(), capture(scanCallbackSlot)) } answers {
            scanCallbackSlot.captured.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
        }

        var resultList: List<Device>? = null
        provider.getDeviceList { resultList = it }
        assertTrue(resultList?.isEmpty() == true)
    }

    // TODO: fix this test
    /*
    java.lang.AbstractMethodError: 'public abstract void java.lang.Runnable.run()' cannot be called
    at java.lang.Object_4a951e4_Proxy.run(Unknown Source:22)
    at android.os.Handler.handleCallback(Handler.java:958)
    at android.os.Handler.dispatchMessage(Handler.java:99)
    at android.os.Looper.loopOnce(Looper.java:205)
    at android.os.Looper.loop(Looper.java:294)
    at android.app.ActivityThread.main(ActivityThread.java:8177)
    at java.lang.reflect.Method.invoke(Native Method)
    at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:552)
    at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:971)
     */
    @Test
    fun getDeviceList_returnsDevices_whenScanSucceeds() {
        val mockBluetoothDevice = mockk<BluetoothDevice>()
        every { mockBluetoothDevice.address } returns "00:11:22:33:AA:BB"
        every { mockBluetoothDevice.name } returns "TestDevice"

        val mockScanResult = mockk<ScanResult>()
        every { mockScanResult.device } returns mockBluetoothDevice
        every { mockScanResult.rssi } returns -50

        val scanCallbackSlot = slot<ScanCallback>()
        val handlerSlot = slot<Runnable>()

        every { mockScanner.startScan(any(), any(), capture(scanCallbackSlot)) } just Runs
        every { provider.handler.postDelayed(capture(handlerSlot), any()) } answers {
            // Simulate scan result
            scanCallbackSlot.captured.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            // Simulate scan timeout
            handlerSlot.captured.run()
            true
        }

        var resultList: List<Device>? = null
        provider.getDeviceList { resultList = it }

        assertFalse(resultList?.isEmpty() ?: true)
        assertEquals(1, resultList?.size)
        assertEquals("TestDevice", resultList?.get(0)?.name)
        assertEquals("00:11:22:33:AA:BB", resultList?.get(0)?.address)
        verify { mockScanner.stopScan(scanCallbackSlot.captured) }
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
}
