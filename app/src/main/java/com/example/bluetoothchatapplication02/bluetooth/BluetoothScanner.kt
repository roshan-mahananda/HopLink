package com.example.bluetoothchatapplication02.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import com.example.bluetoothchatapplication02.model.BluetoothDevice

@SuppressLint("MissingPermission")
class BluetoothScanner {

    private var scanning = false
    private var leScanCallback: ScanCallback? = null

    fun findPairedDevices(bluetoothAdapter: BluetoothAdapter): List<BluetoothDevice> {
        val pairedDevices = bluetoothAdapter.bondedDevices
        return pairedDevices.map { device ->
            BluetoothDevice(
                deviceName = device.name ?: "Unknown Device",
                deviceAddress = device.address
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun scanLeDevice(
        bluetoothAdapter: BluetoothAdapter?,
        onDeviceFound: (BluetoothDevice) -> Unit
    ) {
        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        if (scanning) {
            leScanCallback?.let { bluetoothLeScanner.stopScan(it) }
            scanning = false
        }

        leScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                val address = result.device.address
                val serviceData = result.scanRecord?.getServiceData(HopLinkConfig.SERVICE_UUID)

                val extractedName = if (serviceData != null) {
                    String(serviceData, Charsets.UTF_8)
                } else {
                    val uniqueId = address.takeLast(5).replace(":", "")
                    "HopNode-$uniqueId"
                }

                val customDevice = BluetoothDevice(
                    deviceName = extractedName,
                    deviceAddress = address,
                    userAlias = extractedName
                )

                onDeviceFound(customDevice)
            }
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(HopLinkConfig.SERVICE_UUID)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanning = true
        bluetoothLeScanner.startScan(listOf(filter), settings, leScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan(bluetoothAdapter: BluetoothAdapter?) {
        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        if (scanning) {
            scanning = false
            leScanCallback?.let { bluetoothLeScanner.stopScan(it) }
            leScanCallback = null
        }
    }
}