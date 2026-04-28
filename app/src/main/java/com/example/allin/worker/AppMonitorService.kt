package com.example.allin.worker

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.allin.LockActivity
import com.example.allin.R

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    
    // 임시 잠금 앱 목록 (추후 DB 연동)
    private val lockedApps = mutableSetOf("com.coupang.mobile", "com.musinsa.store")
    private var lastApp: String? = null

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            
            val currentApp = getForegroundApp()
            if (currentApp != null && lockedApps.contains(currentApp)) {
                // 이전에 이미 감지된 앱이 아니라면 잠금 화면 띄움
                if (currentApp != lastApp) {
                    val lockIntent = Intent(this@AppMonitorService, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(lockIntent)
                }
            }
            lastApp = currentApp
            handler.postDelayed(this, 1000) // 1초마다 체크
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
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

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "AppLockChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "쇼핑 앱 잠금 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("쇼핑 앱 감시 중")
            .setContentText("설정한 쇼핑 앱 실행을 감시하고 있습니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        if (stats != null) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            if (sortedStats.isNotEmpty()) {
                return sortedStats[0].packageName
            }
        }
        return null
    }
}
