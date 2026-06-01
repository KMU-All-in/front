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
import com.example.allin.NotificationSettings
import com.example.allin.NotificationWorkScheduler
import com.example.allin.R

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            if (!NotificationSettings.isPlanAlertEnabled(applicationContext)) {
                return Result.success()
            }

            val prefs = applicationContext.getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)
            val hasPlan = prefs.getBoolean("has_weekly_plan", false)

            if (!hasPlan) {
                val ignoreCount = prefs.getInt("plan_ignore_count", 0)

                val message = if (ignoreCount > 0) {
                    "아직 주간 계획을 작성하지 않았습니다. 이번 주 예산을 먼저 정해보세요."
                } else {
                    "이번 주 주간 계획을 아직 작성하지 않았습니다."
                }

                sendNotification("주간 계획 알림", message)

                prefs.edit()
                    .putInt("plan_ignore_count", ignoreCount + 1)
                    .apply()
            }

            return Result.success()
        } finally {
            NotificationWorkScheduler.scheduleNextWeeklyPlanReminder(applicationContext)
        }
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "WeeklyPlanReminderChannel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "주간 계획 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, BudgetSetupActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            3001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(3001, notification)
    }
}