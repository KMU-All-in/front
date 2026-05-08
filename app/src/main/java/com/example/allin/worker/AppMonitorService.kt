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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val lockedApps = mutableSetOf<String>()
    private var lastApp: String? = null

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            
            val currentApp = getForegroundApp()
            
            // [매우 중요] 로그캣에서 'AppMonitorService'로 검색 시 이 로그가 매초 찍혀야 합니다.
            Log.d("AppMonitorService", "● 감시 작동 중 - 현재 앱: $currentApp | 잠금 앱 수: ${lockedApps.size}")
            
            if (currentApp != null && lockedApps.contains(currentApp)) {
                if (currentApp != lastApp) {
                    Log.e("AppMonitorService", "▶▶▶ 잠금 앱 감지됨: $currentApp ◀◀◀")
                    val intent = Intent(this@AppMonitorService, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("PACKAGE_NAME", currentApp)
                    }
                    startActivity(intent)
                }
            }
            lastApp = currentApp
            handler.postDelayed(this, 1000) // 1초마다 체크
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("AppMonitorService", "서비스 onCreate() 호출됨")
        startForegroundService()
        observeLockedApps()
    }

    private fun startForegroundService() {
        val channelId = "AppLockChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "잠금 감시", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("쇼핑 앱 감시 활성화")
            .setContentText("설정한 쇼핑 앱 실행을 감시하고 있습니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 안드로이드 14(API 34) 이상 대응: 서비스 타입 명시
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun observeLockedApps() {
        val user = auth.currentUser
        if (user == null) {
            Log.e("AppMonitorService", "로그인 정보 없음 - 목록 로드 불가")
            return
        }
        
        db.collection("users").document(user.uid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("AppMonitorService", "Firestore 리스너 오류", e)
                return@addSnapshotListener
            }
            val apps = snapshot?.get("locked_apps") as? List<String>
            if (apps != null) {
                lockedApps.clear()
                lockedApps.addAll(apps)
                Log.d("AppMonitorService", "잠금 목록 동기화됨: $lockedApps")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AppMonitorService", "서비스 onStartCommand() - 감시 시작")
        isRunning = true
        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("AppMonitorService", "서비스 onDestroy() - 감시 중단")
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        
        // UsageEvents를 사용하는 방식이 더 정확하고 빠릅니다.
        val events = usm.queryEvents(now - 3000, now)
        val event = UsageEvents.Event()
        var lastResumedApp: String? = null
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumedApp = event.packageName
            }
        }
        
        return lastResumedApp ?: lastApp // 새로운 이벤트가 없으면 마지막 앱 유지
    }
}
