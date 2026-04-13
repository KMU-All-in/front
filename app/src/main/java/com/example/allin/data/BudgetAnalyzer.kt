package com.example.allin.data

class BudgetAnalyzer {

    fun calculateTotal(payments: List<Payment>): Int {
        return payments.sumOf { it.amount }
    }

    fun calculateCategorySum(payments: List<Payment>): Map<String, Int> {
        return payments.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    fun calculateBudgetUsage(total: Int, budget: Int): Int {
        if (budget <= 0) return 0
        return ((total.toFloat() / budget.toFloat()) * 100).toInt()
    }

    fun analyze(payments: List<Payment>, budget: Int): AnalysisResult {
        val total = calculateTotal(payments)
        val categorySums = calculateCategorySum(payments)
        val usagePercent = calculateBudgetUsage(total, budget)
        
        val recommendations = mutableListOf<String>()
        
        if (usagePercent > 100) {
            recommendations.add("예산을 초과했습니다! 지출을 즉시 줄여야 합니다.")
        } else if (usagePercent > 80) {
            recommendations.add("예산의 80% 이상을 사용했습니다. 주의가 필요합니다.")
        } else {
            recommendations.add("현재 예산 내에서 잘 소비하고 있습니다.")
        }

        // 가장 많이 지출한 카테고리 찾기
        val topCategory = categorySums.maxByOrNull { it.value }
        topCategory?.let {
            recommendations.add("${it.key} 카테고리에서 가장 많은 지출(${it.value}원)이 발생했습니다. 이 부분의 소비를 줄여보세요.")
        }

        return AnalysisResult(
            totalConsumption = total,
            categorySums = categorySums,
            budgetUsagePercent = usagePercent,
            recommendations = recommendations
        )
    }
}
