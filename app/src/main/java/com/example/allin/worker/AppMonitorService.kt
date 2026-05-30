package com.example.allin.worker

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.allin.BudgetSetupActivity
import com.example.allin.LockActivity
import com.example.allin.R
import com.example.allin.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import com.example.allin.MainActivity
import com.example.allin.NotificationSettings

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val lockedApps = mutableSetOf<String>()
    private var lastApp: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        var unlockedAppPackage: String? = null
        var lastLockTime: Long = 0L
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val currentApp = getForegroundApp()
            val currentTime = System.currentTimeMillis()

            if (currentApp != null && lockedApps.contains(currentApp)) {
                if (currentApp != unlockedAppPackage && (currentTime - lastLockTime > 5000)) {
                    Log.d("AppMonitorService", "쇼핑 앱 확실하게 차단 실행 (중복 원천 차단): $currentApp")

                    lastLockTime = currentTime
                    unlockedAppPackage = null

                    checkBudgetAndPlan()

                    val lockIntent = Intent(applicationContext, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("PACKAGE_NAME", currentApp)
                    }
                    startActivity(lockIntent)
                }
            } else {
                if (currentApp != null && !lockedApps.contains(currentApp)) {
                    unlockedAppPackage = null
                }
            }
            lastApp = currentApp
            handler.postDelayed(this, 1000)
        }
    }

    // 예산 및 계획 체크 로직
    private fun checkBudgetAndPlan() {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        db.collection("users").document(currentUser.uid).collection("reports")
            .orderBy("start_date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->

                if (NotificationSettings.isPlanAlertEnabled(this@AppMonitorService)) {

                    sendNotification("계획 미작성", "이번 주 주간 계획을 먼저 작성해 주세요!")

                    // 강제로 예산 설정 페이지로 보내는 어쩌구저쩌구 거시기 그거입니다. 강제로 보내지는게 기분 드릅다 싶으면 주석처리해도 됩니다.
                    val intent = Intent(this@AppMonitorService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        // 만약 MainActivity가 켜질 때 바로 예산 탭을 보여주고 싶다면 아래처럼 플래그를 넘길 수도 있습니다.
                        putExtra("GO_TO_BUDGET", true)
                    }
                    startActivity(intent)

                    Log.d("AppMonitorService", "계획 미작성 확인되어 메인 화면으로 강제 이동 완료!")
                    // 여기까지 주석처리하면 됩니다 밑에 리턴코드 빼고

                    return@addOnSuccessListener
                }

                val doc = snapshots.documents[0]
                val budget = doc.getLong("budget_usage") ?: 0L
                val thisWeekTotal = doc.getLong("total_spent") ?: 0L

                // 예산 사용량 경고 체크
                if (budget > 0L) {
                    val percent = ((thisWeekTotal.toDouble() / budget.toDouble()) * 100).toInt()
                    if (NotificationSettings.isBudgetAlertEnabled(this@AppMonitorService) && percent >= 50) {
                        sendNotification("예산 경고", "이번 주 예산의 ${percent}%를 사용했습니다! 신중하게 쇼핑하세요.")
                    }
                }

                Log.d("AppMonitorService", "주간 계획이 확인되어 정상 통과합니다. 예산: $budget")
            }
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "BudgetWarningChannel"
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "예산 경고", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this@AppMonitorService, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("TARGET_FRAGMENT", "BUDGET")
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        observeRoomDatabase()
    }

    private fun observeRoomDatabase() {
        val dao = AppDatabase.getDatabase(this).lockedAppDao()
        serviceScope.launch {
            dao.getAllLockedApps().collect { apps ->
                lockedApps.clear()
                lockedApps.addAll(apps.filter { it.isActive }.map { it.packageName })
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "AppLockChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "잠금 감시", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("절약 모드 작동 중")
            .setContentText("쇼핑 앱 실행을 감시하고 있습니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        handler.post(monitorRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun getForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3000, now)
        val event = UsageEvents.Event()
        var lastResumedApp: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumedApp = event.packageName
            }
        }
        return lastResumedApp
    }
}