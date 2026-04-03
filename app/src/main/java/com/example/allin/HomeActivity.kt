package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.text.DecimalFormat

class HomeActivity : AppCompatActivity() {

    private lateinit var tvWeeklyBudget: TextView
    private lateinit var tvUsedAmount: TextView
    private lateinit var tvRemainingAmount: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var budgetProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 뷰 연결
        tvWeeklyBudget = findViewById(R.id.tvWeeklyBudget)
        tvUsedAmount = findViewById(R.id.tvUsedAmount)
        tvRemainingAmount = findViewById(R.id.tvRemainingAmount)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        budgetProgress = findViewById(R.id.budgetProgress)

        val menuBudget = findViewById<CardView>(R.id.menuBudget)
        val navBudget = findViewById<LinearLayout>(R.id.navBudget)

        // 예산 설정 화면으로 이동 (메뉴 버튼 또는 하단 탭)
        val goToBudgetSetup = {
            val intent = Intent(this, BudgetSetupActivity::class.java)
            startActivity(intent)
        }

        menuBudget.setOnClickListener { goToBudgetSetup() }
        navBudget.setOnClickListener { goToBudgetSetup() }

        // 데이터 표시
        updateBudgetData()
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에서 돌아왔을 때 최신 데이터로 업데이트
        updateBudgetData()
    }

    private fun updateBudgetData() {
        val sharedPref = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        val totalBudget = sharedPref.getInt("TOTAL_BUDGET", 0)
        val usedAmount = sharedPref.getInt("USED_AMOUNT", 0) // 현재는 0으로 시작

        val dec = DecimalFormat("#,###")
        
        tvWeeklyBudget.text = "${dec.format(totalBudget)}원"
        tvUsedAmount.text = "${dec.format(usedAmount)}원"

        val remaining = totalBudget - usedAmount
        tvRemainingAmount.text = "남은 금액: ${dec.format(if (remaining < 0) 0 else remaining)}원"

        // 프로그레스 바 계산
        if (totalBudget > 0) {
            val percent = (usedAmount.toFloat() / totalBudget.toFloat() * 100).toInt()
            budgetProgress.progress = percent
            tvProgressPercent.text = "$percent.0%"
        } else {
            budgetProgress.progress = 0
            tvProgressPercent.text = "0.0%"
        }
    }
}
