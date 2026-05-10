package com.example.allin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fake_products")
data class FakeProduct(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val category: String = "",
    val price: Int = 0,
    val imageUrl: String = "",
    val url: String = "",
    val status: String = "구매 예정",
    val addedTime: Long = System.currentTimeMillis(),
    val expiryDays: Int = 7,
    val reasons: List<String> = emptyList()
)
