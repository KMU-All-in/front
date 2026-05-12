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
    private lateinit var ivCharacter: ImageView
    private lateinit var tvWarningMsg: TextView

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
        ivCharacter = findViewById(R.id.ivCharacter)
        tvWarningMsg = findViewById(R.id.tvWarningMsg)
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
                    updateStatusByPercent(percent)
                } else {
                    budgetProgress.progress = 0
                    tvProgressPercent.text = "0.0%"
                    tvWarningMsg.text = "이번 주 예산을 설정하고 계획적인 소비를 시작해보세요!"
                    ivCharacter.setImageResource(android.R.drawable.ic_menu_help)
                }
            }
    }

    private fun updateStatusByPercent(percent: Int) {
        val (resId, message) = when {
            percent >= 100 -> R.drawable.home100 to "예산을 초과했어요! 당분간 지출을 멈춰야 해요."
            percent >= 90 -> R.drawable.home90 to "경고! 예산의 거의 다 써가요. 정말 필요한 것만 사세요!"
            percent >= 80 -> R.drawable.home80 to "주의하세요! 지출이 예산의 80%에 도달했습니다."
            percent >= 50 -> R.drawable.home50 to "벌써 예산의 절반을 사용하셨네요. 조금만 아껴볼까요?"
            else -> android.R.drawable.ic_menu_today to "포포가 당신의 소비를 지켜보고 있어요! 아주 잘하고 있어요."
        }

        tvWarningMsg.text = message
        ivCharacter.setImageResource(resId)
    }

    private fun resetUI() {
        tvWeeklyBudget.text = "0원"
        tvUsedAmount.text = "0원"
        tvBudgetDateRange.text = "설정된 예산이 없습니다"
        budgetProgress.progress = 0
        tvProgressPercent.text = "0.0%"
        tvWarningMsg.text = "포포와 함께 현명한 소비 습관을 만들어봐요!"
        ivCharacter.setImageResource(android.R.drawable.ic_menu_help)
    }
}
