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

data class MeshPeer(val nodeId: Int, val name: String)

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

    private val _targetNodeId = MutableStateFlow<Int?>(null)
    val targetNodeId: StateFlow<Int?> = _targetNodeId.asStateFlow()
        private val _currentPeerAddress = MutableStateFlow("MESH_SESSION")

    init {
        viewModelScope.launch {
            _currentPeerAddress.collectLatest { address ->
                chatMessageDao.getMessagesForDevice(address).collectLatest { dbMessages ->
                    _chatMessages.value = dbMessages
                }
            }
        }
    }

    private val _meshPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val meshPeers: StateFlow<List<MeshPeer>> = _meshPeers.asStateFlow()

    fun addMeshPeer(nodeId: Int, name: String) {
        _meshPeers.update { currentPeers ->
            if (currentPeers.any { it.nodeId == nodeId }) {
                currentPeers
            } else {
                currentPeers + MeshPeer(nodeId, name)
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
        _discoverableDevices.update { currentDevices ->
            if(currentDevices.any { it.deviceAddress.equals(device.deviceAddress, ignoreCase = true) }) {
                currentDevices
            } else {
                currentDevices + device
            }
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
        saveMessageToDatabase(
            text = message,
            senderName = _connectedDeviceName.value,
            isFromMe = false,
            deviceAddress = _currentPeerAddress.value
        )
    }

    fun addLocalMessage(text: String, myName: String) {
        saveMessageToDatabase(
            text = text,
            senderName = myName,
            isFromMe = true,
            deviceAddress = _currentPeerAddress.value
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

    fun setTargetNodeId(nodeId: Int?) {
        _targetNodeId.value = nodeId
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