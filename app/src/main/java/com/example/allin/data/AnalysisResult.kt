package com.example.allin.data

data class AnalysisResult(
    val totalConsumption: Int,
    val categorySums: Map<String, Int>,
    val budgetUsagePercent: Int,
    val recommendations: List<String>
)
