package com.example.bluetoothchatapplication02.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bluetoothchatapplication02.data.ChatMessageDao
import com.example.bluetoothchatapplication02.model.BluetoothDevice
import com.example.bluetoothchatapplication02.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BluetoothViewModel(private val chatMessageDao: ChatMessageDao) : ViewModel() {
    private val _discoverableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val _connectionStatus = MutableStateFlow("Disconnected")
    private val _activeRelays = MutableStateFlow(0)
    private val _queuedMessages = MutableStateFlow(0)

    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoverableDevices.asStateFlow()
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    val activeRelays: StateFlow<Int> = _activeRelays.asStateFlow()
    val queuedMessages: StateFlow<Int> = _queuedMessages.asStateFlow()
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow("Connected Device")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

    private val _currentPeerAddress = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            _currentPeerAddress.collectLatest { address ->
                if (address != null) {
                    chatMessageDao.getMessagesForDevice(address).collectLatest { dbMessages ->
                        _chatMessages.value = dbMessages
                    }
                } else {
                    _chatMessages.value = emptyList()
                }
            }
        }
    }

    fun setConnectedPeer(address: String) {
        _currentPeerAddress.value = address
    }

    fun setConnectedDeviceName(name: String) {
        _connectedDeviceName.value = name
    }

    fun addDiscoveredDevice(device: BluetoothDevice) {
        val currentDiscovered = _discoverableDevices.value
        if (!currentDiscovered.any { it.deviceAddress == device.deviceAddress }) {
            _discoverableDevices.value = currentDiscovered + device
        }
    }

    fun clearDiscoveredDevices() {
        _discoverableDevices.value = emptyList()
    }

    fun updateDeviceAlias(address: String, alias: String) {
        _discoverableDevices.update { devices ->
            devices.map { device ->
                if (device.deviceAddress == address) {
                    device.copy(userAlias = alias)
                } else {
                    device
                }
            }
        }
    }

    private fun saveMessageToDatabase(text: String, senderName: String, isFromMe: Boolean, deviceAddress: String) {
        viewModelScope.launch {
            val newMessage = ChatMessage(
                text = text,
                senderName = senderName,
                isFromMe = isFromMe,
                deviceAddress = deviceAddress,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(newMessage)
        }
    }

    fun receiveChatMessage(message: String) {
        val currentAddress = _currentPeerAddress.value ?: return
        saveMessageToDatabase(
            text = message,
            senderName = _connectedDeviceName.value,
            isFromMe = false,
            deviceAddress = currentAddress
        )
    }

    fun addLocalMessage(text: String, myName: String) {
        val currentAddress = _currentPeerAddress.value ?: return
        saveMessageToDatabase(
            text = text,
            senderName = myName,
            isFromMe = true,
            deviceAddress = currentAddress
        )
    }

    fun updateConnectionStatus(status: String) {
        _connectionStatus.value = status
    }

    fun updateActiveRelays(count: Int) {
        _activeRelays.value = count
    }

    fun updateQueuedMessages(count: Int) {
        _queuedMessages.value = count
    }
}

class BluetoothViewModelFactory(private val chatMessageDao: ChatMessageDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BluetoothViewModel(chatMessageDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}