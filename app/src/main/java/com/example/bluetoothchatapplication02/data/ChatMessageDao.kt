package com.example.bluetoothchatapplication02.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bluetoothchatapplication02.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE deviceAddress = :address ORDER BY timestamp ASC")
    fun getMessagesForDevice(address: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages GROUP BY deviceAddress ORDER BY timestamp DESC")
    fun getRecentConversations(): Flow<List<ChatMessage>>

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Int)

    @Query("DELETE FROM chat_messages WHERE deviceAddress = :deviceAddress")
    suspend fun deleteConversationByAddress(deviceAddress: String)
}