package com.example.bluetoothchatapplication02.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer

class BluetoothScanner(
    private val onDeviceFound: (deviceAddress: String, deviceName: String, nodeId: Int) -> Unit
) {
    private var isScanning = false
    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScanning(bluetoothAdapter: BluetoothAdapter?) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || isScanning) return


        val filter = ScanFilter.Builder()
            .setServiceUuid(HopLinkConfig.SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val scanRecord = result.scanRecord ?: return
                val serviceData = scanRecord.getServiceData(HopLinkConfig.SERVICE_UUID)

                if (serviceData != null && serviceData.size >= 4) {
                    val buffer = ByteBuffer.wrap(serviceData)

                    val nodeId = buffer.int

                    val nameBytes = ByteArray(serviceData.size - 4)
                    buffer.get(nameBytes)
                    val deviceName = String(nameBytes, Charsets.UTF_8)

                    Log.d("BluetoothScanner", "Discovered: $deviceName (Node: $nodeId) at ${result.device.address}")

                    onDeviceFound(result.device.address, deviceName, nodeId)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BluetoothScanner", "Scan failed with error: $errorCode")
                isScanning = false
            }
        }

        scanner.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        Log.d("BluetoothScanner", "Started BLE scanning for mesh peers")
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(bluetoothAdapter: BluetoothAdapter?) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanCallback?.let {
            scanner.stopScan(it)
            isScanning = false
            scanCallback = null
            Log.d("BluetoothScanner", "Stopped BLE scanning")
        }
    }
}