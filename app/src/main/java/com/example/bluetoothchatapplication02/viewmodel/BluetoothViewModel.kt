package com.example.bluetoothchatapplication02.viewmodel

import androidx.lifecycle.ViewModel
import com.example.bluetoothchatapplication02.model.BluetoothDevice
import com.example.bluetoothchatapplication02.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BluetoothViewModel : ViewModel() {
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

    fun receiveChatMessage(message: String) {
        val newMessage = ChatMessage(
            text = message,
            isFromMe = false,
            senderName = _connectedDeviceName.value
        )
        _chatMessages.update { current -> current + newMessage }
    }

    fun addLocalMessage(text: String, myName: String) {
        val newMessage = ChatMessage(
            text = text,
            isFromMe = true,
            senderName = myName
        )
        _chatMessages.update { current -> current + newMessage }
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