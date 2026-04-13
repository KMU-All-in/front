package com.example.allin.data

data class Payment(
    val id: String,
    val amount: Int,
    val category: String,
    val date: Long,
    val itemName: String
)
