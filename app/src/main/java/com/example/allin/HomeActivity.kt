package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.text.DecimalFormat

class HomeActivity : AppCompatActivity() {

    private lateinit var tvWeeklyBudget: TextView
    private lateinit var tvUsedAmount: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var budgetProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initViews()
        setupListeners()
        updateBudgetData()
    }

    private fun initViews() {
        tvWeeklyBudget = findViewById(R.id.tvWeeklyBudget)
        tvUsedAmount = findViewById(R.id.tvUsedAmount)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        budgetProgress = findViewById(R.id.budgetProgress)
    }

    private fun setupListeners() {
        // 우측 상단 설정 아이콘
        findViewById<ImageView>(R.id.ivSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 앱 잠금 메뉴
        findViewById<CardView>(R.id.menuAppLock).setOnClickListener {
            // startActivity(Intent(this, AppLockActivity::class.java)) // 잠금 액티비티 연결 시 주석 해제
        }

        // 하단 탭: 예산 설정
        findViewById<LinearLayout>(R.id.navBudget).setOnClickListener {
            startActivity(Intent(this, BudgetSetupActivity::class.java))
        }

        // 하단 탭: 가짜 장바구니
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            startActivity(Intent(this, FakeCartActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateBudgetData()
    }

    private fun updateBudgetData() {
        val sharedPref = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        val totalBudget = sharedPref.getInt("TOTAL_BUDGET", 0)
        val usedAmount = sharedPref.getInt("USED_AMOUNT", 0)

        val dec = DecimalFormat("#,###")
        tvWeeklyBudget.text = "${dec.format(totalBudget)}원"
        tvUsedAmount.text = "${dec.format(usedAmount)}원"

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
