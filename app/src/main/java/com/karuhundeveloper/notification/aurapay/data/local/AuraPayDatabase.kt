package com.karuhundeveloper.notification.aurapay.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AuraPayRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AuraPayDatabase : RoomDatabase() {

    abstract fun auraPayDao(): AuraPayDao

    companion object {
        private const val DB_NAME = "aurapay_relay.db"

        @Volatile
        private var INSTANCE: AuraPayDatabase? = null

        fun getInstance(context: Context): AuraPayDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraPayDatabase::class.java,
                    DB_NAME
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
