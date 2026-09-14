package com.example.bluetoothchatapplication02.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.UUID

private const val TAG = "BluetoothLeService"

@SuppressLint("MissingPermission")
class BluetoothLeService : Service() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothManager: BluetoothManager? = null

    private var bluetoothGatt: BluetoothGatt? = null

    private var gattServer: BluetoothGattServer? = null
    private var connectedClient: BluetoothDevice? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun initialize(): Boolean {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) return false

        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)

        val service = BluetoothGattService(HopLinkConfig.SERVICE_UUID_JAVA, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            HopLinkConfig.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )
        characteristic.addDescriptor(descriptor)
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        return true
    }

    fun connect(address: String): Boolean {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                return true
            } catch (e: IllegalArgumentException) {
                return false
            }
        }
        return false
    }

    fun sendMessage(message: String) {
        val payload = message.toByteArray(Charsets.UTF_8)

        if (bluetoothGatt != null) {
            val char = bluetoothGatt?.getService(HopLinkConfig.SERVICE_UUID_JAVA)
                ?.getCharacteristic(HopLinkConfig.CHARACTERISTIC_UUID)
            char?.let {
                it.value = payload
                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                bluetoothGatt?.writeCharacteristic(it)
            }
        }
        else if (connectedClient != null && gattServer != null) {
            val char = gattServer?.getService(HopLinkConfig.SERVICE_UUID_JAVA)
                ?.getCharacteristic(HopLinkConfig.CHARACTERISTIC_UUID)
            char?.let {
                it.value = payload
                gattServer?.notifyCharacteristicChanged(connectedClient, it, false)
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedClient = device
                broadcastUpdate(ACTION_GATT_CONNECTED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedClient = null
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            val receivedText = String(value, Charsets.UTF_8)

            if (receivedText.startsWith("[SYS_NAME]:")) {
                val peerName = receivedText.removePrefix("[SYS_NAME]:")
                sendBroadcast(Intent(ACTION_NAME_AVAILABLE).apply {
                    putExtra(EXTRA_ADDRESS, device.address)
                    putExtra(EXTRA_NAME, peerName)
                })

                val prefs = getSharedPreferences("HopLinkPrefs", Context.MODE_PRIVATE)
                val myName = prefs.getString("USER_ALIAS", "Anonymous") ?: "Anonymous"
                sendMessage("[SYS_NAME]:$myName")

            } else {
                sendBroadcast(Intent(ACTION_DATA_AVAILABLE).apply { putExtra(EXTRA_DATA, receivedText) })
            }
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(ACTION_GATT_CONNECTED)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)

                val char = gatt.getService(HopLinkConfig.SERVICE_UUID_JAVA)
                    ?.getCharacteristic(HopLinkConfig.CHARACTERISTIC_UUID)

                if (char != null) {
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val prefs = getSharedPreferences("HopLinkPrefs", Context.MODE_PRIVATE)
                val myName = prefs.getString("USER_ALIAS", "Anonymous") ?: "Anonymous"
                sendMessage("[SYS_NAME]:$myName")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                val receivedText = String(data, Charsets.UTF_8)
                if (receivedText.startsWith("[SYS_NAME]:")) {
                    val peerName = receivedText.removePrefix("[SYS_NAME]:")
                    sendBroadcast(Intent(ACTION_NAME_AVAILABLE).apply {
                        putExtra(EXTRA_ADDRESS, gatt.device.address)
                        putExtra(EXTRA_NAME, peerName)
                    })
                } else {
                    sendBroadcast(Intent(ACTION_DATA_AVAILABLE).apply { putExtra(EXTRA_DATA, receivedText) })
                }
            }
        }
    }

    private fun broadcastUpdate(action: String) {
        sendBroadcast(Intent(action))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bluetoothGatt?.close()
        bluetoothGatt = null
        gattServer?.close()
        gattServer = null
        return super.onUnbind(intent)
    }

    companion object {
        const val ACTION_GATT_CONNECTED = "com.example.bluetoothchatapplication02.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.bluetoothchatapplication02.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetoothchatapplication02.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.bluetoothchatapplication02.ACTION_DATA_AVAILABLE"
        const val ACTION_NAME_AVAILABLE = "com.example.bluetoothchatapplication02.ACTION_NAME_AVAILABLE"
        const val EXTRA_DATA = "com.example.bluetoothchatapplication02.EXTRA_DATA"
        const val EXTRA_ADDRESS = "com.example.bluetoothchatapplication02.EXTRA_ADDRESS"
        const val EXTRA_NAME = "com.example.bluetoothchatapplication02.EXTRA_NAME"
    }
}