package com.example.allin.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.allin.R
import com.example.allin.HomeActivity
import com.example.allin.data.AppDatabase
import com.example.allin.data.Payment
import com.example.allin.data.PaymentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class PaymentNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: PaymentRepository

    override fun onCreate() {
        super.onCreate()
        val dao = AppDatabase.getDatabase(applicationContext).paymentDao()
        repository = PaymentRepository(dao)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        // 1. 우리 앱(AllIn)에서 발생한 알림은 무시 (무한 루프 방지)
        if (sbn?.packageName == packageName) return

        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullText = "$title $text"

        Log.d("PaymentListener", "알림 감지: $fullText")

        // 2. 결제 관련 키워드가 있을 때만 처리
        if (fullText.contains("결제") || fullText.contains("승인") || fullText.contains("원") || fullText.contains("사용")) {
            parseAndSavePayment(fullText)
        }
    }

    private fun parseAndSavePayment(content: String) {
        scope.launch {
            try {
                val amountPattern = Pattern.compile("([\\d,]+)\\s*원")
                val amountMatcher = amountPattern.matcher(content)
                var amount = 0
                if (amountMatcher.find()) {
                    val amountStr = amountMatcher.group(1)?.replace(",", "") ?: "0"
                    amount = amountStr.toIntOrNull() ?: 0
                }

                val parts = content.split(" ")
                val storeName = if (parts.size > 1) parts[0] + " " + parts[1] else parts.getOrNull(0) ?: "알 수 없는 상점"

                if (amount > 0) {
                    val payment = Payment(
                        amount = amount,
                        category = "기타",
                        date = System.currentTimeMillis(),
                        itemName = "자동 입력 지출",
                        storeName = storeName
                    )
                    
                    repository.insert(payment)
                    Log.d("PaymentListener", "지출 기록 완료: $storeName $amount 원")
                    
                    sendCompletionNotification(storeName, amount)
                }
            } catch (e: Exception) {
                Log.e("PaymentListener", "분석 오류", e)
            }
        }
    }

    private fun sendCompletionNotification(store: String, price: Int) {
        val channelId = "PaymentInputChannel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "결제 자동 입력", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("결제 내역 자동 입력 완료")
            .setContentText("${store}에서 ${price}원이 기록되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }
}
