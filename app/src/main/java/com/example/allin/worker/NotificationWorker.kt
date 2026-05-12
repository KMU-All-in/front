package com.example.allin.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.allin.BudgetSetupActivity
import com.example.allin.FakeCartActivity
import com.example.allin.R
import com.example.allin.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.*

class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("AllInPrefs", Context.MODE_PRIVATE)
        val hasPlan = prefs.getBoolean("has_weekly_plan", false)
        
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // 3-2. 주간 계획 작성 독촉 (12시, 18시)
        if (!hasPlan && (hour == 12 || hour == 18)) {
            // 사용자가 무시했을 경우를 대비해 캐릭터 상태(짜증) 플래그 설정 가능
            val ignoreCount = prefs.getInt("plan_ignore_count", 0)
            val message = if (ignoreCount > 0) "아직도 안 했어? 빨리 주간 계획 짜라고! (캐릭터가 짜증을 냅니다)" 
                         else "이번 주 주간 계획을 아직 작성하지 않았습니다."
            
            sendNotification("주간 계획 알림", message, BudgetSetupActivity::class.java, 3001)
            prefs.edit().putInt("plan_ignore_count", ignoreCount + 1).apply()
        }

        // 3-4. 장바구니 소멸 알림 (소멸 1일 전 상품 체크)
        checkFakeCartExpiry()

        return Result.success()
    }

    private suspend fun checkFakeCartExpiry() {
        val dao = AppDatabase.getDatabase(applicationContext).fakeProductDao()
        val products = dao.getAllProducts().first()
        val now = System.currentTimeMillis()

        val expiringSoon = products.filter { 
            val expiryTime = it.addedTime + (it.expiryDays * 24 * 60 * 60 * 1000L)
            val timeLeft = expiryTime - now
            timeLeft in 0..(24 * 60 * 60 * 1000L) // 24시간 이내 남은 상품
        }

        if (expiringSoon.isNotEmpty()) {
            sendNotification(
                "장바구니 소멸 임박", 
                "${expiringSoon.size}개의 상품이 곧 사라집니다! 정말 살 건지 결정해 주세요.", 
                FakeCartActivity::class.java, 
                3002
            )
        }
    }

    private fun sendNotification(title: String, message: String, targetActivity: Class<*>, id: Int) {
        val channelId = "ScheduledAlarmChannel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "정기 알림", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, targetActivity)
        val pendingIntent = PendingIntent.getActivity(applicationContext, id, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(id, notification)
    }
}
