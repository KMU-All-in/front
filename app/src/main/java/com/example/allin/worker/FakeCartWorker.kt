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
import com.example.allin.FakeCartActivity
import com.example.allin.R
import com.example.allin.data.FakeProduct
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class FakeCartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()
        val oneDayMillis = TimeUnit.DAYS.toMillis(1)

        try {
            val snapshot = db.collection("users").document(uid).collection("fakecart").get().await()
            val products = snapshot.toObjects(FakeProduct::class.java)

            for (product in products) {
                val expiryTime = product.addedTime + TimeUnit.DAYS.toMillis(product.expiryDays.toLong())
                val remainingTime = expiryTime - now

                // 남은 시간이 24시간 미만이고 0보다 클 때 (즉, 하루 남았을 때) 알림 전송
                if (remainingTime in 1 until oneDayMillis) {
                    sendNotification(product.name)
                }
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun sendNotification(productName: String) {
        val channelId = "FakeCartExpiryChannel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "장바구니 만료 알림", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, FakeCartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("숙고 기간 종료 임박!")
            .setContentText("'${productName}'의 D-1일입니다. 정말 구매하실 건가요?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(productName.hashCode(), notification)
    }
}
