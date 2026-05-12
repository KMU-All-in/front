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

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val lockedApps = mutableSetOf<String>()
    private var lastApp: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            
            val currentApp = getForegroundApp()
            
            if (currentApp != null && lockedApps.contains(currentApp)) {
                if (currentApp != lastApp) {
                    Log.d("AppMonitorService", "쇼핑 앱 감지: $currentApp")
                    checkBudgetAndPlan()
                    
                    // (옵션) 바로 잠금화면을 띄우고 싶다면 아래 주석 해제
                    /*
                    val intent = Intent(this@AppMonitorService, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("PACKAGE_NAME", currentApp)
                    }
                    startActivity(intent)
                    */
                }
            }
            lastApp = currentApp
            handler.postDelayed(this, 1000)
        }
    }

    // 예산 및 계획 체크 로직 (시나리오 3-1, 3-2-3)
    private fun checkBudgetAndPlan() {
        serviceScope.launch {
            val prefs = getSharedPreferences("AllInPrefs", Context.MODE_PRIVATE)
            val weeklyBudget = prefs.getInt("weekly_budget", 0)
            val hasPlan = prefs.getBoolean("has_weekly_plan", false)

            // 1. 계획이 없으면 작성 페이지로 강제 이동 (3-2-3)
            if (!hasPlan) {
                val intent = Intent(this@AppMonitorService, BudgetSetupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                sendNotification("계획 미작성", "이번 주 주간 계획을 먼저 작성해 주세요!")
                return@launch
            }

            // 2. 이번 주 지출 합계 계산 (3-1)
            val dao = AppDatabase.getDatabase(applicationContext).paymentDao()
            val payments = dao.getAllPayments().first()
            
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            val startOfWeek = calendar.timeInMillis
            
            val thisWeekTotal = payments.filter { it.date >= startOfWeek }.sumOf { it.amount }

            if (weeklyBudget > 0) {
                val percent = (thisWeekTotal.toDouble() / weeklyBudget * 100).toInt()
                if (percent >= 50) {
                    sendNotification("예산 경고", "이번 주 예산의 $percent%를 사용했습니다! 신중하게 쇼핑하세요.")
                }
            }
        }
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "BudgetWarningChannel"
        val manager = getSystemService(NotificationManager::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "예산 경고", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, BudgetSetupActivity::class.java)
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
                lockedApps.addAll(apps.map { it.packageName })
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
