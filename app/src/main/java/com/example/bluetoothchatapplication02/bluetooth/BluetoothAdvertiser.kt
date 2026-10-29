package com.example.bluetoothchatapplication02.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer

class BluetoothAdvertiser {
    private var advertiseCallback: AdvertiseCallback? = null
    private var messageCallback: AdvertiseCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    var isAdvertising = false
        private set

    @SuppressLint("MissingPermission")
    fun startAdvertising(bluetoothAdapter: BluetoothAdapter?, username: String, nodeId: Int) {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null || isAdvertising) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val safeName = username.take(10)
        val nameBytes = safeName.toByteArray(Charsets.UTF_8)

        val serviceData = ByteBuffer.allocate(4 + nameBytes.size)
            .putInt(nodeId)
            .put(nameBytes)
            .array()

        val data = AdvertiseData.Builder()
            .addServiceUuid(HopLinkConfig.SERVICE_UUID)
            .addServiceData(HopLinkConfig.SERVICE_UUID, serviceData)
            .setIncludeDeviceName(false)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isAdvertising = true
                Log.d("BluetoothAdvertiser", "Advertising started successfully with Node ID: $nodeId")
            }

            override fun onStartFailure(errorCode: Int) {
                isAdvertising = false
                Log.e("BluetoothAdvertiser", "Advertising failed: $errorCode")
            }
        }

        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising(bluetoothAdapter: BluetoothAdapter?) {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        advertiseCallback?.let {
            advertiser.stopAdvertising(it)
            isAdvertising = false
            advertiseCallback = null
            Log.d("BluetoothAdvertiser", "Advertising stopped")
        }
    }

    @SuppressLint("MissingPermission")
    fun broadcastMeshMessage(bluetoothAdapter: BluetoothAdapter?, payload: ByteArray, onComplete: () -> Unit) {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        val safePayload = if (payload.size > 31) payload.copyOf(31) else payload

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(HopLinkConfig.SERVICE_UUID)
            .addServiceData(HopLinkConfig.SERVICE_UUID, safePayload)
            .setIncludeDeviceName(false)
            .build()

        messageCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BluetoothAdvertiser", "Broadcasting mesh payload successfully")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BluetoothAdvertiser", "Payload broadcast failed: $errorCode")
            }
        }

        stopAdvertising(bluetoothAdapter)
        advertiser.startAdvertising(settings, data, null, messageCallback)

        handler.postDelayed({
            @SuppressLint("MissingPermission")
            messageCallback?.let {
                advertiser.stopAdvertising(it)
                messageCallback = null
                Log.d("BluetoothAdvertiser", "Stopped mesh payload broadcast")
            }
            onComplete()
        }, 3000)
    }
}