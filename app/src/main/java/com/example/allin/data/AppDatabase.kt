package com.example.allin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FakeProduct::class, LockedApp::class, Payment::class], version = 5, exportSchema = false)
@TypeConverters(AppConverters::class) // 이름을 AppConverters로 변경
abstract class AppDatabase : RoomDatabase() {
    abstract fun fakeProductDao(): FakeProductDao
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "allin_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
