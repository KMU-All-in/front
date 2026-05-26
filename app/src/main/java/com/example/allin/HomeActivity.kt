package com.example.allin

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DecimalFormat
import androidx.activity.OnBackPressedCallback

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
        checkNotificationPermission()
        setupBackPress()
    }

    override fun onResume() {
        super.onResume()
        checkSpecialPermissions()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
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
                    updateUI(0, 0)
                    return@addSnapshotListener
                }

                val document = snapshots.documents[0]
                val budgetLimit = document.getLong("budget_usage")?.toInt() ?: 0
                val totalSpent = document.getLong("total_spent")?.toInt() ?: 0
                
                updateUI(budgetLimit, totalSpent)
            }
    }

    private fun updateUI(budgetLimit: Int, totalSpent: Int) {
        val dec = DecimalFormat("#,###")
        tvWeeklyBudget.text = "${dec.format(budgetLimit)}원"
        tvUsedAmount.text = "${dec.format(totalSpent)}원"
        
        if (budgetLimit > 0) {
            val percent = (totalSpent.toFloat() / budgetLimit.toFloat() * 100).toInt()
            budgetProgress.progress = percent
            tvProgressPercent.text = "$percent.0%"
            updateStatusByPercent(percent)
        } else {
            budgetProgress.progress = 0
            tvProgressPercent.text = "0.0%"
            tvWarningMsg.text = "예산을 먼저 설정해 주세요!"
            ivCharacter.setImageResource(android.R.drawable.ic_menu_help)
        }
    }

    private fun updateStatusByPercent(percent: Int) {
        val (resId, message) = when {
            percent >= 100 -> R.drawable.dog_sad to "예산을 초과했어요! 지출을 멈추세요."
            percent >= 90 -> R.drawable.dog_emergency to "개큰경고! 예산의 90%를 넘었습니다."
            percent >= 80 -> R.drawable.dog_omg to "경고! 예산의 80%를 넘었습니다."
            percent >= 50 -> R.drawable.dog_happy to "벌써 절반이나 썼어요! 아껴봅시다."
            percent >= 20 -> R.drawable.dog_happy to "20% 사용! 이대로만 아껴봐요~!"
            else -> R.drawable.dog_default to "포포가 당신의 소비를 응원해요!"
        }
        tvWarningMsg.text = message
        ivCharacter.setImageResource(resId)
    }

    private fun checkSpecialPermissions() {
        if (!isUsageStatsPermissionGranted()) {
            showPermissionDialog("사용 정보 접근 권한 필요", "쇼핑 앱 감지를 위해 권한이 필요합니다.", Settings.ACTION_USAGE_ACCESS_SETTINGS)
            return
        }
        if (!isNotificationListenerServiceEnabled()) {
            showPermissionDialog("알림 접근 권한 필요", "결제 내역 자동 입력을 위해 권한이 필요합니다.", "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationListenerServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun showPermissionDialog(title: String, message: String, action: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("설정하러 가기") { _, _ ->
                try {
                    startActivity(Intent(action))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .show()
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
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<CardView>(R.id.menuAppLock).setOnClickListener {
            startActivity(Intent(this, AppLockActivity::class.java))
        }
        
        // Navigation - All using REORDER_TO_FRONT to prevent stacking
        findViewById<LinearLayout>(R.id.navBudget).setOnClickListener {
            val intent = Intent(this, BudgetSetupActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            // Slide to Left animation (entering from left)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            val intent = Intent(this, FakeCartActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            // Entering from right
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupBackPress(){
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed(){
                finishAffinity()
            }
        })
    }
}
