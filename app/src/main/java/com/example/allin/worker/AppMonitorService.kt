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
import com.example.allin.LockActivity
import com.example.allin.R
import com.example.allin.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            
            // 로컬 리스트(Room에서 가져온 것)에 있는지 확인
            if (currentApp != null && lockedApps.contains(currentApp)) {
                if (currentApp != lastApp) {
                    Log.d("AppMonitorService", "잠금 앱 감지 (Room 기반): $currentApp")
                    val intent = Intent(this@AppMonitorService, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("PACKAGE_NAME", currentApp)
                    }
                    startActivity(intent)
                }
            }
            lastApp = currentApp
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        // Room DB에서 잠금 목록 실시간 감시
        observeRoomDatabase()
    }

    private fun observeRoomDatabase() {
        val dao = AppDatabase.getDatabase(this).lockedAppDao()
        serviceScope.launch {
            dao.getAllLockedApps().collect { apps ->
                lockedApps.clear()
                lockedApps.addAll(apps.map { it.packageName })
                Log.d("AppMonitorService", "Room 데이터 갱신됨: $lockedApps")
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
            .setContentTitle("쇼핑 앱 감시 활성화")
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
