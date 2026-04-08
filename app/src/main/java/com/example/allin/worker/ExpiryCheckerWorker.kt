package com.example.allin.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.allin.data.FakeProductDatabase
import com.example.allin.data.FakeProduct
import kotlinx.coroutines.flow.first

class ExpiryCheckerWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = FakeProductDatabase.getDatabase(applicationContext)
        val dao = database.fakeProductDao()
        
        // 1. 만료 대상 조회 (Room DB 활용)
        val products = dao.getAllProducts().first()
        val currentTime = System.currentTimeMillis()

        for (product in products) {
            if (product.status == "만료" || product.status == "구매 완료") continue

            // 2. 만료 여부 확인 (ExpiryValidator 역할)
            val expiryTimeMillis = product.addedTime + (product.expiryDays * 24 * 60 * 60 * 1000L)
            val timeLeft = expiryTimeMillis - currentTime

            if (timeLeft <= 0) {
                // 3. 만료 상태 변경 (ExpiryStatusChanger 역할)
                val expiredProduct = product.copy(status = "만료")
                dao.insertProduct(expiredProduct)
                
                // 4. 만료 알림 (ExpiryNotifier 역할)
                sendNotification("상품 만료 알림", "'${product.name}' 상품의 보관 기간이 만료되었습니다.")
            } else if (timeLeft < 24 * 60 * 60 * 1000L) {
                // 만료 1일 전 알림
                sendNotification("만료 임박 알림", "'${product.name}' 상품이 내일 만료됩니다. 구매를 결정하셨나요?")
            }
        }
        
        return Result.success()
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "expiry_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "가짜 장바구니 알림", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
