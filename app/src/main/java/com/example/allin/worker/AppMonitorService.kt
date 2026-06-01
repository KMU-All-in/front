package com.example.allin.worker

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import kotlinx.coroutines.flow.collect
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
    private lateinit var sharedPref: SharedPreferences

    companion object {
        var unlockedAppPackage: String? = null
        var lastLockTime: Long = 0L
        const val TAG = "AppMonitorService"
    }

    // SharedPreference 변경 리스너 (예산 초과 플래그 감시)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "is_budget_exceeded") {
            Log.d(TAG, "예산 초과 플래그 변경 감지 -> 차단 목록 갱신")
            refreshLockedApps()
        }
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val currentApp = getForegroundApp()
            val currentTime = System.currentTimeMillis()

            if (currentApp != null && lockedApps.contains(currentApp)) {
                if (currentApp != unlockedAppPackage && (currentTime - lastLockTime > 5000)) {
                    Log.d(TAG, "잠금 앱 차단 실행: $currentApp")
                    lastLockTime = currentTime
                    unlockedAppPackage = null

                    val lockIntent = Intent(applicationContext, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("PACKAGE_NAME", currentApp)
                    }
                    startActivity(lockIntent)
                }
            }
            lastApp = currentApp
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
        
        startForegroundService()
        observeRoomDatabase()
    }

    private fun observeRoomDatabase() {
        val dao = AppDatabase.getDatabase(this).lockedAppDao()
        serviceScope.launch {
            dao.getAllLockedApps().collect {
                refreshLockedApps()
            }
        }
    }

    private fun refreshLockedApps() {
        serviceScope.launch {
            val dao = AppDatabase.getDatabase(this@AppMonitorService).lockedAppDao()
            val apps = dao.getLockedAppsList()
            val isBudgetExceeded = sharedPref.getBoolean("is_budget_exceeded", false)

            lockedApps.clear()
            if (isBudgetExceeded) {
                // 예산 초과 시: 리스트에 있는 모든 앱 무조건 차단
                lockedApps.addAll(apps.map { it.packageName })
                Log.d(TAG, "예산 초과 모드: 모든 앱(${lockedApps.size}개) 차단 활성화")
            } else {
                // 일반 상태: 사용자가 켠 앱만 차단
                lockedApps.addAll(apps.filter { it.isActive }.map { it.packageName })
                Log.d(TAG, "일반 모드: 선택된 앱(${lockedApps.size}개) 차단 활성화")
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
        refreshLockedApps()
        handler.post(monitorRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
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
