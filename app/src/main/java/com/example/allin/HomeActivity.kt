package com.example.allin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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

        initViews()
        setupListeners()
        // SharedPreferences 대신 Firestore 데이터를 실시간으로 감시합니다.
        observeBudgetData()
    }

    private fun initViews() {
        tvWeeklyBudget = findViewById(R.id.tvWeeklyBudget)
        tvUsedAmount = findViewById(R.id.tvUsedAmount)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        budgetProgress = findViewById(R.id.budgetProgress)
        tvBudgetDateRange = findViewById(R.id.tvBudgetDateRange)
    }

    private fun setupListeners() {
        // 우측 상단 설정 아이콘
        findViewById<ImageView>(R.id.ivSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // 앱 잠금 메뉴 (S1, S2 시나리오 대응)
        findViewById<CardView>(R.id.menuAppLock).setOnClickListener {
            val intent = Intent(this, AppLockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // 하단 탭: 홈
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            // 현재 화면이므로 무시
        }

        // 하단 탭: 예산 설정
        findViewById<LinearLayout>(R.id.navBudget).setOnClickListener {
            val intent = Intent(this, BudgetSetupActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // 하단 탭: 가짜 장바구니
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            val intent = Intent(this, FakeCartActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    private fun observeBudgetData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("HomeActivity", "No user logged in")
            return
        }

        // 현재 날짜를 포함하는 리포트를 찾거나 가장 최근 리포트를 가져옵니다.
        db.collection("users").document(currentUser.uid)
            .collection("reports")
            .orderBy("start_date", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("HomeActivity", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    val document = snapshots.documents[0]
                    
                    // Firestore 필드 연결: budget_usage (예산), total_spent (사용 금액)
                    val budgetUsage = document.getLong("budget_usage")?.toInt() ?: 0
                    val totalSpent = document.getLong("total_spent")?.toInt() ?: 0
                    
                    val dec = DecimalFormat("#,###")
                    tvWeeklyBudget.text = "${dec.format(budgetUsage)}원"
                    tvUsedAmount.text = "${dec.format(totalSpent)}원"

                    // 날짜 범위 표시 (Firestore의 start_date, end_date 활용)
                    val startTimestamp = document.getTimestamp("start_date")
                    val endTimestamp = document.getTimestamp("end_date")
                    
                    if (startTimestamp != null && endTimestamp != null) {
                        val sdf = SimpleDateFormat("M월 d일", Locale.KOREA)
                        val startStr = sdf.format(startTimestamp.toDate())
                        val endStr = sdf.format(endTimestamp.toDate())
                        tvBudgetDateRange.text = "$startStr ~ $endStr"
                    }

                    // 프로그레스 바 업데이트
                    if (budgetUsage > 0) {
                        val percent = (totalSpent.toFloat() / budgetUsage.toFloat() * 100).toInt()
                        budgetProgress.progress = percent
                        tvProgressPercent.text = "$percent.0%"
                    } else {
                        budgetProgress.progress = 0
                        tvProgressPercent.text = "0.0%"
                    }
                } else {
                    tvWeeklyBudget.text = "0원"
                    tvUsedAmount.text = "0원"
                    tvBudgetDateRange.text = "설정된 예산이 없습니다"
                    budgetProgress.progress = 0
                    tvProgressPercent.text = "0.0%"
                }
            }
    }
}
