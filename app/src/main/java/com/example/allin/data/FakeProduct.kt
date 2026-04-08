package com.example.allin.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "fake_products")
data class FakeProduct(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val price: Int,
    val imageUrl: String = "",
    val url: String = "",
    val status: String = "구매 예정",
    val addedTime: Long = System.currentTimeMillis(),
    val expiryDays: Int = 7,
    val reasons: List<String> = emptyList() // [변경] 고정된 5개 대신 리스트로 변경
)

// Room에서 리스트를 저장하기 위한 컨버터
class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return Gson().toJson(list)
    }
}
