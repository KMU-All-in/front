package com.example.allin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FakeProduct::class], version = 2, exportSchema = false) // 버전을 2로 올립니다
@TypeConverters(Converters::class) // 컨버터 등록
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
                .fallbackToDestructiveMigration() // 스키마 변경 시 기존 데이터 삭제 후 재생성 (테스트 단계)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
