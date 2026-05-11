package com.example.allin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var tvWeeklyBudget: TextView
    private lateinit var tvUsedAmount: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var budgetProgress: ProgressBar
    private lateinit var tvBudgetDateRange: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        hideSystemBars()
        initViews()
        setupListeners()
        observeBudgetData()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun initViews() {
        tvWeeklyBudget = findViewById(R.id.tvWeeklyBudget)
        tvUsedAmount = findViewById(R.id.tvUsedAmount)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        budgetProgress = findViewById(R.id.budgetProgress)
        tvBudgetDateRange = findViewById(R.id.tvBudgetDateRange)
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.ivSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        findViewById<CardView>(R.id.menuAppLock).setOnClickListener {
            val intent = Intent(this, AppLockActivity::class.java)
            startActivity(intent)
        }

        // 하단 탭 네비게이션 (옆으로 넘기는 애니메이션 적용)
        findViewById<LinearLayout>(R.id.navBudget).setOnClickListener {
            val intent = Intent(this, BudgetSetupActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            val intent = Intent(this, FakeCartActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun observeBudgetData() {
        val currentUser = auth.currentUser ?: return

        db.collection("users").document(currentUser.uid)
            .collection("reports")
            .orderBy("start_date", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) {
                    resetUI()
                    return@addSnapshotListener
                }

                val document = snapshots.documents[0]
                val budgetUsage = document.getLong("budget_usage")?.toInt() ?: 0
                val totalSpent = document.getLong("total_spent")?.toInt() ?: 0
                
                val dec = DecimalFormat("#,###")
                tvWeeklyBudget.text = "${dec.format(budgetUsage)}원"
                tvUsedAmount.text = "${dec.format(totalSpent)}원"

                val startTimestamp = document.getTimestamp("start_date")
                val endTimestamp = document.getTimestamp("end_date")
                if (startTimestamp != null && endTimestamp != null) {
                    val sdf = SimpleDateFormat("M월 d일", Locale.KOREA)
                    tvBudgetDateRange.text = "${sdf.format(startTimestamp.toDate())} ~ ${sdf.format(endTimestamp.toDate())}"
                }

                if (budgetUsage > 0) {
                    val percent = (totalSpent.toFloat() / budgetUsage.toFloat() * 100).toInt()
                    budgetProgress.progress = percent
                    tvProgressPercent.text = "$percent.0%"
                } else {
                    budgetProgress.progress = 0
                    tvProgressPercent.text = "0.0%"
                }
            }
    }

    private fun resetUI() {
        tvWeeklyBudget.text = "0원"
        tvUsedAmount.text = "0원"
        tvBudgetDateRange.text = "설정된 예산이 없습니다"
        budgetProgress.progress = 0
        tvProgressPercent.text = "0.0%"
    }
}
