package com.example.bluetoothchatapplication02.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val senderName: String,
    val isFromMe: Boolean,
    val deviceAddress: String,
    val timestamp: Long
)