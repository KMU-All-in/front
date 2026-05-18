package com.example.allin.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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

                // [수정] 백그라운드 자동 삭제 로직을 제거했습니다. (사용자가 앱에서 직접 결정함)

                // [2] 하루 전 알림 (D-1) 로직은 유지
                if (remainingTime > 0 && remainingTime < oneDayMillis && !product.notifiedD1) {
                    Log.d("FakeCartWorker", "D-1 알림 발송: ${product.name}")
                    sendNotification(product.name)
                    
                    db.collection("users").document(uid).collection("fakecart")
                        .document(product.id)
                        .update("notifiedD1", true)
                        .await()
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("FakeCartWorker", "체크 중 오류 발생", e)
            return Result.retry()
        }
    }

    private fun sendNotification(productName: String) {
        val channelId = "FakeCartExpiryChannel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "장바구니 만료 알림", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, FakeCartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, productName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("숙고 기간 종료 임박!")
            .setContentText("'${productName}'의 숙고 기간이 하루 남았습니다. 정말 구매하실 건가요?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(productName.hashCode(), notification)
    }
}
