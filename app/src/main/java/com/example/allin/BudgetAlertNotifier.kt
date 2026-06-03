package com.example.allin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.allin.data.AppDatabase
import com.example.allin.worker.AppMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BudgetAlertNotifier {
    private const val CHANNEL_ID = "BudgetThresholdChannel"
    private const val TAG = "BudgetAlertNotifier"

    fun notifyIfThresholdCrossed(
        context: Context,
        budget: Long,
        oldSpent: Long,
        newSpent: Long
    ) {
        if (budget <= 0L) return

        val oldPercent = (oldSpent.toDouble() / budget * 100).toInt()
        val newPercent = (newSpent.toDouble() / budget * 100).toInt()
        
        val sharedPref = context.getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)

        if (newPercent >= 100) {
            // [강력 조치] 예산 초과 상태 즉시 저장 및 모든 앱 강제 활성화
            val wasExceeded = sharedPref.getBoolean("is_budget_exceeded", false)
            sharedPref.edit().putBoolean("is_budget_exceeded", true).commit()
            
            autoEnableAllAppLocks(context)
            
            // 처음 100%를 넘거나, 상태가 변했을 때 알림
            if (!wasExceeded || oldPercent < 100) {
                send(context, "예산을 모두 사용함. 이제부터 길냥이정식도 못먹음 짬타이거 ㄱㄱ")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "예산 초과! 모든 쇼핑 앱을 강제 잠금합니다.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // 예산 범위 내로 복귀 시 상태 해제
            sharedPref.edit().putBoolean("is_budget_exceeded", false).commit()
            
            val message = when {
                oldPercent < 90 && newPercent >= 90 -> "90%를 사용했어요. 길냥이정식이 얼마 안남았어요."
                oldPercent < 80 && newPercent >= 80 -> "이제 길냥이정식 먹을시간이에요."
                oldPercent < 50 && newPercent >= 50 -> "우와 50퍼나 사용했어요."
                else -> null
            }
            if (message != null) {
                send(context, message)
            }
        }
    }

    private fun autoEnableAllAppLocks(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).lockedAppDao()
                // 1. DB의 모든 앱 상태를 isActive = 1 로 강제 변경
                dao.activateAll()
                
                // 2. 서비스가 이미 실행 중이어도 설정을 갱신하도록 다시 시작 호출
                val intent = Intent(context, AppMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "잠금 자동 활성화 실패", e)
            }
        }
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

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "예산 알림 권한이 없어 알림을 보낼 수 없습니다.", e)
        } catch (e: Exception) {
            Log.e(TAG, "예산 알림 발송 실패", e)
        }
    }
}
