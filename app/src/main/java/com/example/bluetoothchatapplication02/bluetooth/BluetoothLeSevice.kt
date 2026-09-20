package com.example.bluetoothchatapplication02.bluetooth

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createServiceNotificationChannel()

        val notification = NotificationCompat.Builder(this, "HOPLINK_SYSTEM_SERVICE")
            .setContentTitle("HopLink is Active")
            .setContentText("Listening for peer-to-peer messages in the background")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Default Android BT icon
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(101, notification)
        }
        return START_STICKY
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "HOPLINK_SYSTEM_SERVICE",
                "HopLink Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Bluetooth radio active in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun initialize(): Boolean {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) return false

        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)

        if (gattServer == null) {
            Log.e(TAG, "CRITICAL: openGattServer returned null. Check permissions!")
            return false
        }

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

        Log.d(TAG, "GATT Server initialized successfully")
        return true
    }

    fun connect(address: String): Boolean {
        Log.d(TAG, "Attempting connection to $address")
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
        Log.d(TAG, "Attempting to send: $message")

        if (bluetoothGatt != null) {
            val char = bluetoothGatt?.getService(HopLinkConfig.SERVICE_UUID_JAVA)
                ?.getCharacteristic(HopLinkConfig.CHARACTERISTIC_UUID)
            if (char != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    char.value = payload
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    bluetoothGatt?.writeCharacteristic(char)
                }
                Log.d(TAG, "Message dispatched via GATT Client")
            } else {
                Log.e(TAG, "Client characteristic not found!")
            }
        }
        else if (connectedClient != null && gattServer != null) {
            val char = gattServer?.getService(HopLinkConfig.SERVICE_UUID_JAVA)
                ?.getCharacteristic(HopLinkConfig.CHARACTERISTIC_UUID)
            if (char != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gattServer?.notifyCharacteristicChanged(connectedClient!!, char, false, payload)
                } else {
                    char.value = payload
                    gattServer?.notifyCharacteristicChanged(connectedClient, char, false)
                }
                Log.d(TAG, "Message dispatched via GATT Server Notify")
            } else {
                Log.e(TAG, "Server characteristic not found!")
            }
        } else {
            Log.e(TAG, "Cannot send: Not connected to any device!")
        }
    }

    private fun processIncomingData(value: ByteArray, address: String) {
        val receivedText = String(value, Charsets.UTF_8)
        Log.d(TAG, "Received raw data: $receivedText")

        if (receivedText.startsWith("[SYS_NAME]:")) {
            val peerName = receivedText.removePrefix("[SYS_NAME]:")
            sendBroadcast(Intent(ACTION_NAME_AVAILABLE).apply {
                setPackage(packageName)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_NAME, peerName)
            })
        } else {
            sendBroadcast(Intent(ACTION_DATA_AVAILABLE).apply {
                setPackage(packageName)
                putExtra(EXTRA_DATA, receivedText)
            })
        }
    }
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Server: Client connected (${device.address})")
                connectedClient = device
                broadcastUpdate(ACTION_GATT_CONNECTED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Server: Client disconnected")
                connectedClient = null
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            Log.d(TAG, "Server received write request")
            processIncomingData(value, device.address)

            val text = String(value, Charsets.UTF_8)
            if (text.startsWith("[SYS_NAME]:")) {
                val prefs = getSharedPreferences("HopLinkPrefs", Context.MODE_PRIVATE)
                val myName = prefs.getString("USER_ALIAS", "Anonymous") ?: "Anonymous"
                sendMessage("[SYS_NAME]:$myName")
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            Log.d(TAG, "Server granted Notification descriptor")
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Client: Connected to server. Discovering services...")
                broadcastUpdate(ACTION_GATT_CONNECTED)
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Client: Services discovered. Enabling notifications...")
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
                val char = gatt.getService(HopLinkConfig.SERVICE_UUID_JAVA)?.getCharacteristic(HopLinkConfig.CHARACTERISTIC_UUID)
                char?.let {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor!!, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor!!)
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "Client: Notifications enabled. Sending handshake.")
            val prefs = getSharedPreferences("HopLinkPrefs", Context.MODE_PRIVATE)
            val myName = prefs.getString("USER_ALIAS", "Anonymous") ?: "Anonymous"
            sendMessage("[SYS_NAME]:$myName")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            Log.d(TAG, "Client received data (API 33+)")
            processIncomingData(value, gatt.device.address)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "Client received data (Legacy)")
            processIncomingData(characteristic.value, gatt.device.address)
        }
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
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