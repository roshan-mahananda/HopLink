package com.example.bluetoothchatapplication02.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer

class BluetoothScanner(
    private val onDeviceFound: (deviceAddress: String, deviceName: String, nodeId: Int) -> Unit,
    private val onMeshPayloadReceived: (ByteArray) -> Unit
) {
    private var isScanning = false
    private var scanCallback: ScanCallback? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val scanCycler = Handler(Looper.getMainLooper())
    private val cycleRunnable = object : Runnable {
        override fun run() {
            restartScan()
            scanCycler.postDelayed(this, 12000)
        }
    }

    private val filter = ScanFilter.Builder()
        .setServiceUuid(HopLinkConfig.SERVICE_UUID)
        .build()

    private val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    @SuppressLint("MissingPermission")
    fun startScanning(adapter: BluetoothAdapter?) {
        this.bluetoothAdapter = adapter
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || isScanning) return

        scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val scanRecord = result.scanRecord ?: return
                val serviceData = scanRecord.getServiceData(HopLinkConfig.SERVICE_UUID)

                if (serviceData != null) {
                    if (serviceData.size >= 15) {
                        Log.d("BluetoothScanner", "Caught ambient mesh payload from ${result.device.address}")
                        onMeshPayloadReceived(serviceData)
                    } else if (serviceData.size >= 4) {
                        val buffer = ByteBuffer.wrap(serviceData)
                        val nodeId = buffer.int
                        val nameBytes = ByteArray(serviceData.size - 4)
                        buffer.get(nameBytes)
                        val deviceName = String(nameBytes, Charsets.UTF_8)

                        Log.d("BluetoothScanner", "Discovered: $deviceName (Node: $nodeId)")
                        onDeviceFound(result.device.address, deviceName, nodeId)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BluetoothScanner", "Scan failed with error: $errorCode")
            }
        }

        scanner.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        Log.d("BluetoothScanner", "Started BLE scanning for mesh peers")

        scanCycler.postDelayed(cycleRunnable, 12000)
    }

    @SuppressLint("MissingPermission")
    private fun restartScan() {
        if (!isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanCallback?.let {
            scanner.stopScan(it)
            scanner.startScan(listOf(filter), settings, it)
            Log.d("BluetoothScanner", "Cycled BLE scan to bypass OS throttling")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(adapter: BluetoothAdapter?) {
        scanCycler.removeCallbacks(cycleRunnable)
        val scanner = adapter?.bluetoothLeScanner ?: return
        scanCallback?.let {
            scanner.stopScan(it)
            isScanning = false
            scanCallback = null
            Log.d("BluetoothScanner", "Stopped BLE scanning")
        }
    }
}