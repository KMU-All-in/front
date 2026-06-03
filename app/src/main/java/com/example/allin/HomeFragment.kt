package com.example.allin

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DecimalFormat

class HomeFragment : Fragment() {

    private lateinit var tvWeeklyBudget: TextView
    private lateinit var tvUsedAmount: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var budgetProgress: ProgressBar
    private lateinit var tvBudgetDateRange: TextView
    private lateinit var ivCharacter: ImageView
    private lateinit var tvWarningMsg: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_home, container, false)

        initViews(view)
        setupListeners(view)
        observeBudgetData()
        checkNotificationPermission()

        return view
    }

    override fun onResume() {
        super.onResume()
        checkSpecialPermissions()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
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
                    if (isAdded) {
                        updateUI(0, 0)
                    }
                    return@addSnapshotListener
                }

                val document = snapshots.documents[0]
                val budgetLimit = document.getLong("budget_usage")?.toLong() ?: 0L
                val totalSpent = document.getLong("total_spent")?.toLong() ?: 0L

                val safeContext = context ?: return@addSnapshotListener

                // 홈 화면에서도 데이터가 변경되면 즉시 예산 초과 및 앱 강제 잠금 체크
                BudgetAlertNotifier.notifyIfThresholdCrossed(
                    safeContext,
                    budgetLimit,
                    totalSpent,
                    totalSpent
                )

                if (!isAdded) {
                    return@addSnapshotListener
                }

                updateUI(budgetLimit.toInt(), totalSpent.toInt())
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
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), requireContext().packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), requireContext().packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationListenerServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(requireContext().packageName)
    }

    private fun showPermissionDialog(title: String, message: String, action: String) {
        AlertDialog.Builder(requireContext())
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

    private fun initViews(view: View) {
        tvWeeklyBudget = view.findViewById(R.id.tvWeeklyBudget)
        tvUsedAmount = view.findViewById(R.id.tvUsedAmount)
        tvProgressPercent = view.findViewById(R.id.tvProgressPercent)
        budgetProgress = view.findViewById(R.id.budgetProgress)
        tvBudgetDateRange = view.findViewById(R.id.tvBudgetDateRange)
        ivCharacter = view.findViewById(R.id.ivCharacter)
        tvWarningMsg = view.findViewById(R.id.tvWarningMsg)
    }

    private fun setupListeners(view: View) {
        view.findViewById<ImageView>(R.id.ivSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        view.findViewById<CardView>(R.id.menuAppLock).setOnClickListener {
            startActivity(Intent(requireContext(), AppLockActivity::class.java))
        }
    }
}