package com.example.allin

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.allin.data.BudgetAnalyzer
import com.example.allin.data.Payment
import com.example.allin.ui.PieChartView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.DecimalFormat

class ReportActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChartView
    private lateinit var tvTotalConsumption: TextView
    private lateinit var tvBudgetUsage: TextView
    private lateinit var tvRecommendations: TextView
    private val budgetAnalyzer = BudgetAnalyzer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        hideSystemBars()
        initViews()
        loadAndAnalyzeData()
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

    private fun loadAndAnalyzeData() {
        val sharedPref = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        val totalBudget = sharedPref.getInt("TOTAL_BUDGET", 0)
        
        val paymentsJson = sharedPref.getString("PAYMENTS_LIST", null)
        val payments: List<Payment> = if (paymentsJson != null) {
            val type = object : TypeToken<List<Payment>>() {}.type
            Gson().fromJson(paymentsJson, type)
        } else {
            emptyList()
        }

        val result = budgetAnalyzer.analyze(payments, totalBudget)

        val dec = DecimalFormat("#,###")
        tvTotalConsumption.text = "총 소비액: ${dec.format(result.totalConsumption)}원"
        tvBudgetUsage.text = "예산 사용률: ${result.budgetUsagePercent}%"
        
        tvRecommendations.text = result.recommendations.joinToString("\n\n")

        pieChart.setData(result.categorySums)
    }

    private fun setupListeners() {
        // X 버튼 클릭 시 리포트 화면 닫기
        findViewById<ImageView>(R.id.btnCloseReport)?.setOnClickListener {
            finish()
            // 닫힐 때 애니메이션을 아래로 내려가는 식으로 하고 싶다면 추가 가능
            // overridePendingTransition(0, R.anim.slide_out_bottom) 
        }
    }
}
