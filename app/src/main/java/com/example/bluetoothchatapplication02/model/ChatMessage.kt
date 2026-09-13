package com.example.bluetoothchatapplication02.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromMe: Boolean,
    val senderName: String,
    val timestamp: Long = System.currentTimeMillis()
)