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
import java.nio.ByteBuffer
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

    private val seenMessageIds = LinkedHashSet<Int>()
    private val MAX_CACHE_SIZE = 2000

    private val meshAdvertiser = BluetoothAdvertiser()

    private var myNodeId: Int = -1

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("HopLinkPrefs", Context.MODE_PRIVATE)
        myNodeId = prefs.getInt("NODE_ID", -1)
        Log.d(TAG, "Service Created - Device Node ID: $myNodeId")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createServiceNotificationChannel()

        val notification = NotificationCompat.Builder(this, "HOPLINK_SYSTEM_SERVICE")
            .setContentTitle("HopLink is Active")
            .setContentText("Listening for peer-to-peer messages in the background")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
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
        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled == false) {
            Log.e(TAG, "Bluetooth is disabled or unavailable.")
            return false
        }

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

    fun sendMessage(message: String, destinationId: Int = 0) {
        val stringBytes = message.toByteArray(Charsets.UTF_8)
        val ttl: Byte = 10
        val messageId = UUID.randomUUID().hashCode()
        val payloadLength = stringBytes.size.toShort()

        val buffer = ByteBuffer.allocate(15 + stringBytes.size)
        buffer.put(ttl)
        buffer.putInt(messageId)
        buffer.putInt(myNodeId)
        buffer.putInt(destinationId)
        buffer.putShort(payloadLength)
        buffer.put(stringBytes)

        val payload = buffer.array()
        seenMessageIds.add(messageId)

        Log.d(TAG, "Sending Msg ID $messageId | From: $myNodeId -> To: $destinationId | TTL: $ttl")

        dispatchBytes(payload)

        meshAdvertiser.broadcastMeshMessage(bluetoothAdapter, payload) {
            Log.d(TAG, "Finished ambient broadcast for local message $messageId")
        }
    }

    fun sendMeshMessage(targetNodeId: Int, message: String) {
        sendMessage(message, destinationId = targetNodeId)
    }

    fun sendPresenceAnnouncement() {
        val prefs = getSharedPreferences("HopLinkPrefs", Context.MODE_PRIVATE)
        val myName = prefs.getString("USER_ALIAS", "Anonymous") ?: "Anonymous"
        sendMessage("[SYS_ANNOUNCE]:$myName", destinationId = 0)
    }

    fun processAmbientPayload(payload: ByteArray, senderAddress: String = "AIR_BROADCAST") {
        processIncomingData(payload, senderAddress)
    }

    private fun processIncomingData(value: ByteArray, address: String) {
        if (value.size < 15) {
            Log.e(TAG, "Received packet too small to contain 15-byte mesh header.")
            return
        }

        val buffer = ByteBuffer.wrap(value)
        val ttl = buffer.get()
        val messageId = buffer.getInt()
        val senderId = buffer.getInt()
        val destinationId = buffer.getInt()
        val payloadLength = buffer.getShort()

        if (seenMessageIds.contains(messageId)) {
            Log.d(TAG, "Dropped duplicate message ID: $messageId")
            return
        }

        seenMessageIds.add(messageId)
        if (seenMessageIds.size > MAX_CACHE_SIZE) {
            val oldestId = seenMessageIds.first()
            seenMessageIds.remove(oldestId)
        }

        val safePayloadLen = minOf(payloadLength.toInt(), value.size - 15)
        val textBytes = ByteArray(safePayloadLen)
        buffer.get(textBytes)
        val receivedText = String(textBytes, Charsets.UTF_8)

        Log.d(TAG, "Processed ID: $messageId | From: $senderId | To: $destinationId | TTL: $ttl")

        val isForMe = destinationId == 0 || destinationId == myNodeId
        if (isForMe) {
            if (receivedText.startsWith("[SYS_ANNOUNCE]:")) {
                val peerName = receivedText.removePrefix("[SYS_ANNOUNCE]:")
                sendBroadcast(Intent(ACTION_PEER_ANNOUNCED).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_SENDER_ID, senderId)
                    putExtra(EXTRA_NAME, peerName)
                })
            } else if (receivedText.startsWith("[SYS_NAME]:")) {
                val peerName = receivedText.removePrefix("[SYS_NAME]:")
                sendBroadcast(Intent(ACTION_NAME_AVAILABLE).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_ADDRESS, address)
                    putExtra(EXTRA_NAME, peerName)
                })
            } else {
                sendBroadcast(Intent(ACTION_DATA_AVAILABLE).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_SENDER_ID, senderId)
                    putExtra(EXTRA_DATA, receivedText)
                })
            }
        } else {
            Log.d(TAG, "Relaying private message intended for Node $destinationId (My Node ID: $myNodeId)")
        }

        if (ttl > 1) {
            val newTtl = (ttl - 1).toByte()
            Log.d(TAG, "Forwarding packet $messageId to peers. New TTL: $newTtl")

            val forwardBuffer = ByteBuffer.allocate(15 + textBytes.size)
            forwardBuffer.put(newTtl)
            forwardBuffer.putInt(messageId)
            forwardBuffer.putInt(senderId)
            forwardBuffer.putInt(destinationId)
            forwardBuffer.putShort(payloadLength)
            forwardBuffer.put(textBytes)

            val payload = forwardBuffer.array()

            dispatchBytes(payload)

            meshAdvertiser.broadcastMeshMessage(bluetoothAdapter, payload) {
                Log.d(TAG, "Finished ambient broadcast for relayed packet $messageId")
            }
        }
    }

    private fun dispatchBytes(payload: ByteArray) {
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
            }
        } else if (connectedClient != null && gattServer != null) {
            val char = gattServer?.getService(HopLinkConfig.SERVICE_UUID_JAVA)
                ?.getCharacteristic(HopLinkConfig.CHARACTERISTIC_UUID)
            if (char != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gattServer?.notifyCharacteristicChanged(connectedClient!!, char, false, payload)
                } else {
                    char.value = payload
                    gattServer?.notifyCharacteristicChanged(connectedClient, char, false)
                }
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Server: Client connected (${device.address})")
                connectedClient = device
                broadcastUpdate(ACTION_GATT_CONNECTED)
                sendPresenceAnnouncement()
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
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            Log.d(TAG, "Server granted Notification descriptor")
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(ACTION_GATT_CONNECTED)
                gatt.requestMtu(512)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d("BluetoothLeService", "MTU changed to: $mtu")
            gatt.discoverServices()
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
            sendPresenceAnnouncement()
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

    private fun broadcastUpdate(action: String, data: String) {
        val intent = Intent(action).apply {
            setPackage(packageName)
            putExtra(EXTRA_DATA, data)
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
        const val ACTION_PEER_ANNOUNCED = "com.example.bluetoothchatapplication02.ACTION_PEER_ANNOUNCED"

        const val EXTRA_DATA = "com.example.bluetoothchatapplication02.EXTRA_DATA"
        const val EXTRA_ADDRESS = "com.example.bluetoothchatapplication02.EXTRA_ADDRESS"
        const val EXTRA_NAME = "com.example.bluetoothchatapplication02.EXTRA_NAME"
        const val EXTRA_SENDER_ID = "com.example.bluetoothchatapplication02.EXTRA_SENDER_ID"
    }
}