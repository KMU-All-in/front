package com.example.allin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.allin.BudgetAlertNotifier

object BudgetAlertNotifier {
    private const val CHANNEL_ID = "BudgetThresholdChannel"

    fun notifyIfThresholdCrossed(
        context: Context,
        budget: Long,
        oldSpent: Long,
        newSpent: Long
    ) {
        if (!NotificationSettings.isBudgetAlertEnabled(context)) return
        if (budget <= 0L) return

        val oldPercent = (oldSpent.toDouble() / budget * 100).toInt()
        val newPercent = (newSpent.toDouble() / budget * 100).toInt()

        val message = when {
            oldPercent < 100 && newPercent >= 100 ->
                "예산을 모두 사용함. 이제부터 길냥이정식도 못먹음 짬타이거 ㄱㄱ"
            oldPercent < 90 && newPercent >= 90 ->
                "90%를 사용했어요. 길냥이정식이 얼마 안남았어요."
            oldPercent < 80 && newPercent >= 80 ->
                "이제 길냥이정식 먹을시간이에요."
            oldPercent < 50 && newPercent >= 50 ->
                "우와 50퍼나 사용했어요."
            else -> null
        } ?: return

        send(context, message)
    }

    private fun send(context: Context, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "예산 사용 알림", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("예산 관리 알림")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}