package com.example.allin.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.allin.data.FakeProduct
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import com.example.allin.NotificationSettings

class ExpiryCheckerWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!NotificationSettings.isCartAlertEnabled(applicationContext)) {
            return Result.success()
        }


        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser ?: return Result.failure()

        try {
            // Firestore에서 현재 사용자의 장바구니 아이템 가져오기
            val snapshot = firestore.collection("users/${currentUser.uid}/fakecart")
                .get()
                .await()
            
            val products = snapshot.toObjects(FakeProduct::class.java)
            val currentTime = System.currentTimeMillis()
            val oneDayInMillis = TimeUnit.DAYS.toMillis(1)

            for (product in products) {
                val expiryTimeMillis = product.addedTime + TimeUnit.DAYS.toMillis(product.expiryDays.toLong())
                val timeLeft = expiryTimeMillis - currentTime

                // 만료 1일 전(24시간 이내)이고 아직 알림을 보내지 않은 경우 (간단한 예시로 timeLeft 범위 체크)
                if (timeLeft in 0..oneDayInMillis) {
                    val daysLeft = TimeUnit.MILLISECONDS.toDays(timeLeft)
                    if (daysLeft == 0L) { // 정확히 하루 미만 남았을 때
                        sendNotification(
                            "삭제 예정 알림", 
                            "'${product.name}'이(가) 내일 장바구니에서 삭제됩니다. 다시 생각해보세요!"
                        )
                    }
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("ExpiryWorker", "Error checking expiry", e)
            return Result.retry()
        }
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "expiry_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "가짜 장바구니 알림", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(title.hashCode() + message.hashCode(), notification)
    }
}
