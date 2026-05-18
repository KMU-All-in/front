package com.example.allin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fake_products")
data class FakeProduct(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    val category: String = "기타",
    val price: Int = 0,
    val url: String = "",
    val imageUrl: String = "",
    val expiryDays: Int = 7,
    val addedTime: Long = System.currentTimeMillis(),
    val reasons: List<String> = emptyList(),
    val notifiedD1: Boolean = false,
    val notifiedD0: Boolean = false
)
