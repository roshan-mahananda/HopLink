package com.example.bluetoothchatapplication02.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.bluetoothchatapplication02.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE deviceAddress = :address ORDER BY timestamp ASC")
    fun getMessagesForDevice(address: String): Flow<List<ChatMessage>>
}