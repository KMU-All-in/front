package com.example.allin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val category: String,
    val date: Long,
    val itemName: String,
    val storeName: String
)
