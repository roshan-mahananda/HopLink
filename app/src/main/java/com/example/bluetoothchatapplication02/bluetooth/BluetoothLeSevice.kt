package com.example.bluetoothchatapplication02.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun initialize(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    fun connect(address: String): Boolean {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                return true
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Device not found with provided address.")
                return false
            }
        } ?: run {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return false
        }
    }

    fun getSupportedGattServices(): List<BluetoothGattService?>? {
        return bluetoothGatt?.services
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.readCharacteristic(characteristic)
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        characteristic.value = payload
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    fun sendMessage(message: String) {
        val gatt = bluetoothGatt ?: return
        val services = gatt.services ?: return

        for (service in services) {
            for (characteristic in service.characteristics) {
                val isWritable = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                if (isWritable) {
                    writeCharacteristic(characteristic, message.toByteArray(Charsets.UTF_8))
                    return // Stop after writing to the first available writable characteristic
                }
            }
        }
    }

    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enabled: Boolean) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, enabled)
        val uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = characteristic.getDescriptor(uuid)
        descriptor?.let { desc ->
            desc.value = if (enabled) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(desc)
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
                Log.i(TAG, "Successfully connected to GATT Server")
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                Log.i(TAG, "Disconnected from the GATT Server")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)

                val prefs = getSharedPreferences("HopLinkPrefs", Context.MODE_PRIVATE)
                val myProfileName = prefs.getString("USER_ALIAS", "Anonymous") ?: "Anonymous"

                val handshakeMessage = "[SYS_NAME]:$myProfileName"

                // Use the new sendMessage function
                sendMessage(handshakeMessage)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                val receivedText = String(data, Charsets.UTF_8)

                if (receivedText.startsWith("[SYS_NAME]:")) {
                    val peerName = receivedText.removePrefix("[SYS_NAME]:")
                    val address = gatt.device.address

                    val intent = Intent(ACTION_NAME_AVAILABLE).apply {
                        putExtra(EXTRA_ADDRESS, address)
                        putExtra(EXTRA_NAME, peerName)
                    }
                    sendBroadcast(intent)
                } else {
                    val intent = Intent(ACTION_DATA_AVAILABLE).apply {
                        putExtra(EXTRA_DATA, receivedText)
                    }
                    sendBroadcast(intent)
                }
            }
        }
    }

    private fun broadcastUpdate(action: String) {
        sendBroadcast(Intent(action))
    }

    private fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
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

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2
    }
}