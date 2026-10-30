package com.example.bluetoothchatapplication02.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bluetoothchatapplication02.data.ChatMessageDao
import com.example.bluetoothchatapplication02.model.BluetoothDevice
import com.example.bluetoothchatapplication02.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeshPeer(val nodeId: Int, val name: String)

class BluetoothViewModel(
    private val chatMessageDao: ChatMessageDao
) : ViewModel() {

    private val _discoverableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoverableDevices.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val _connectedDeviceName = MutableStateFlow("Connected Device")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

    private val _targetNodeId = MutableStateFlow<Int?>(null)
    val targetNodeId: StateFlow<Int?> = _targetNodeId.asStateFlow()

    private val _currentPeerAddress = MutableStateFlow("")

    fun setCurrentPeerAddress(address: String) {
        _currentPeerAddress.value = address
    }

    init {
        viewModelScope.launch {
            _currentPeerAddress.collectLatest { address ->
                if (address.isNotEmpty()) {
                    chatMessageDao.getMessagesForDevice(address).collectLatest { dbMessages ->
                        _chatMessages.value = dbMessages
                    }
                }
            }
        }
    }

    val recentConversations: StateFlow<List<ChatMessage>> = chatMessageDao.getRecentConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _meshPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val meshPeers: StateFlow<List<MeshPeer>> = _meshPeers.asStateFlow()

    fun addMeshPeer(nodeId: Int, name: String) {
        _meshPeers.update { currentPeers ->
            if (currentPeers.any { it.nodeId == nodeId }) {
                currentPeers.map { if (it.nodeId == nodeId) MeshPeer(nodeId, name) else it }
            } else {
                currentPeers + MeshPeer(nodeId, name)
            }
        }
    }

    fun deleteMessage(messageId: Int) {
        viewModelScope.launch {
            chatMessageDao.deleteMessageById(messageId)
        }
    }

    fun deleteConversation(deviceAddress: String) {
        viewModelScope.launch {
            chatMessageDao.deleteConversationByAddress(deviceAddress)
        }
    }

    fun setConnectedDeviceName(name: String) {
        _connectedDeviceName.value = name
    }

    fun addDiscoveredDevice(device: BluetoothDevice) {
        _discoverableDevices.update { currentDevices ->
            val existingDevice = currentDevices.find { it.deviceName == device.deviceName }

            if (existingDevice != null) {
                currentDevices.map {
                    if (it.deviceName == device.deviceName) device else it
                }
            } else if (currentDevices.any { it.deviceAddress.equals(device.deviceAddress, ignoreCase = true) }) {
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

    fun receiveChatMessage(message: String, senderId: Int, senderAddress: String) {
        val targetAddress = if (senderId != -1) senderId.toString() else senderAddress
        val peerName = _meshPeers.value.find { it.nodeId == senderId }?.name ?: _connectedDeviceName.value

        saveMessageToDatabase(
            text = message,
            senderName = peerName,
            isFromMe = false,
            deviceAddress = targetAddress
        )
    }

    fun getChatStream(deviceAddress: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForDevice(deviceAddress)
    }

    fun addLocalMessage(text: String, peerName: String, targetAddress: String) {
        saveMessageToDatabase(
            text = text,
            senderName = peerName,
            isFromMe = true,
            deviceAddress = targetAddress
        )
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