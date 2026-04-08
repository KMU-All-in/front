package com.example.allin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FakeProduct::class], version = 3, exportSchema = false) // 버전을 3으로 올림
@TypeConverters(Converters::class)
abstract class FakeProductDatabase : RoomDatabase() {
    abstract fun fakeProductDao(): FakeProductDao

    companion object {
        @Volatile
        private var INSTANCE: FakeProductDatabase? = null

        fun getDatabase(context: Context): FakeProductDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FakeProductDatabase::class.java,
                    "fake_product_database"
                )
                .fallbackToDestructiveMigration() // 버전 충돌 시 초기화
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
