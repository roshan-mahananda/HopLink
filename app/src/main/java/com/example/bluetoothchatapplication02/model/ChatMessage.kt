package com.example.bluetoothchatapplication02.model

import android.location.Address
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val senderName: String,
    val isFromMe: Boolean,
    val deviceAddress: String,
    val timestamp: Long = System.currentTimeMillis()
)