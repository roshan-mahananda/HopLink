package com.example.bluetoothchatapplication02.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.bluetoothchatapplication02.model.ChatMessage

@Database(entities = [ChatMessage::class], version = 1, exportSchema = false)
abstract class HopLinkDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao

    companion object{
        @Volatile
        private var INSTANCE: HopLinkDatabase? = null

        fun getDatabase(context: Context): HopLinkDatabase{
            return INSTANCE ?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HopLinkDatabase::class.java,
                    "hoplink_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}