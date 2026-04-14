package com.example.allin

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.allin.data.BudgetAnalyzer
import com.example.allin.data.Payment
import com.example.allin.ui.PieChartView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DecimalFormat

class ReportActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChartView
    private lateinit var tvTotalConsumption: TextView
    private lateinit var tvBudgetUsage: TextView
    private lateinit var tvRecommendations: TextView
    private val budgetAnalyzer = BudgetAnalyzer()
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        hideSystemBars()
        initViews()
        loadAndAnalyzeDataFromFirestore()
        setupListeners()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun initViews() {
        pieChart = findViewById<PieChartView>(R.id.pieChart)
        tvTotalConsumption = findViewById<TextView>(R.id.tvTotalConsumption)
        tvBudgetUsage = findViewById<TextView>(R.id.tvBudgetUsage)
        tvRecommendations = findViewById<TextView>(R.id.tvRecommendations)
    }

    private fun loadAndAnalyzeDataFromFirestore() {
        val currentUser = auth.currentUser ?: return

        // 1. 리포트 데이터(총 예산) 가져오기
        db.collection("users").document(currentUser.uid)
            .collection("reports")
            .orderBy("start_date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { reportSnapshots ->
                if (!reportSnapshots.isEmpty) {
                    val reportDoc = reportSnapshots.documents[0]
                    val totalBudget = reportDoc.getLong("budget_usage")?.toInt() ?: 0

                    // 2. 개별 지출 내역(transactions) 가져와서 카테고리별 합산
                    db.collection("users").document(currentUser.uid)
                        .collection("transactions")
                        .get()
                        .addOnSuccessListener { transSnapshots ->
                            val categorySums = mutableMapOf<String, Int>()
                            var totalSpent = 0
                            
                            for (doc in transSnapshots.documents) {
                                val amount = doc.getLong("amount")?.toInt() ?: 0
                                val category = doc.getString("category") ?: "기타"
                                categorySums[category] = categorySums.getOrDefault(category, 0) + amount
                                totalSpent += amount
                            }

                            // 3. UI 업데이트 및 차트 그리기
                            updateUI(totalSpent, totalBudget, categorySums)
                        }
                }
            }
    }

    private fun updateUI(totalSpent: Int, totalBudget: Int, categorySums: Map<String, Int>) {
        val dec = DecimalFormat("#,###")
        tvTotalConsumption.text = "총 소비액: ${dec.format(totalSpent)}원"
        
        val usagePercent = if (totalBudget > 0) (totalSpent.toFloat() / totalBudget.toFloat() * 100).toInt() else 0
        tvBudgetUsage.text = "예산 사용률: $usagePercent%"
        
        // 간단한 추천 메시지 생성
        val recommendations = mutableListOf<String>()
        if (usagePercent > 100) recommendations.add("예산을 초과했습니다! 지출을 즉시 줄여야 합니다.")
        else if (usagePercent > 80) recommendations.add("예산의 80% 이상을 사용했습니다. 주의가 필요합니다.")
        else recommendations.add("현재 예산 내에서 아주 잘 소비하고 있습니다!")
        
        tvRecommendations.text = recommendations.joinToString("\n\n")

        // 수정한 PieChartView에 데이터 전달 (남은 예산 포함해서 그려줌)
        pieChart.setData(categorySums, totalBudget)
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btnCloseReport)?.setOnClickListener {
            finish()
        }
    }
}
