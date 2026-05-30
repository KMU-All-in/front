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
import com.example.allin.MainActivity
import com.example.allin.R
import com.example.allin.data.FakeProduct
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.concurrent.TimeUnit
import com.example.allin.NotificationSettings

class FakeCartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!NotificationSettings.isCartAlertEnabled(applicationContext)) {
            return Result.success()
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()

        try {
            val snapshot = db.collection("users").document(uid).collection("fakecart").get().await()
            val products = snapshot.toObjects(FakeProduct::class.java)

            for (product in products) {
                // 날짜 기반 만료 시점 계산 (만료일 00:00:00)
                val cal = Calendar.getInstance()
                cal.timeInMillis = product.addedTime
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.DAY_OF_YEAR, product.expiryDays)
                val expiryTimestamp = cal.timeInMillis

                // D-1 00:00 이후 알림 로직
                val d1Timestamp = expiryTimestamp - TimeUnit.DAYS.toMillis(1)
                if (now >= d1Timestamp && now < expiryTimestamp && !product.notifiedD1) {
                    sendNotification(product.name, "내일 장바구니에서 삭제됩니다. 다시 생각해보세요!")

                    // Firestore 업데이트 (D-1 알림 완료 표시)
                    db.collection("users").document(uid).collection("fakecart")
                        .document(product.id)
                        .update("notifiedD1", true)
                        .await()
                }

                // D-Day 12시 1분 알림 로직 (만료 당일 00:01 이후)
                if (now >= expiryTimestamp + TimeUnit.MINUTES.toMillis(1) && !product.notifiedD0) {
                    sendNotification(product.name, "숙고 기간이 만료되었습니다! 삭제하시겠습니까, 유지하시겠습니까?")

                    // Firestore 업데이트 (당일 알림 완료 표시)
                    db.collection("users").document(uid).collection("fakecart")
                        .document(product.id)
                        .update("notifiedD0", true)
                        .await()
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("FakeCartWorker", "체크 중 오류 발생", e)
            return Result.retry()
        }
    }

    private fun sendNotification(productName: String, message: String) {
        val channelId = "FakeCartExpiryChannel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "장바구니 만료 알림", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 클릭 시 메인 화면(만료 팝업이 뜰 곳)으로 이동
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, productName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = "'${productName}' $message"

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("가짜 장바구니 알림")
            .setContentText(notificationText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(notificationText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(productName.hashCode(), notification)
    }
}