package com.example.allin.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.allin.R
import com.example.allin.data.AppDatabase
import com.example.allin.data.Payment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class PaymentNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullText = "$title $text"

        if (fullText.contains("결제") || fullText.contains("승인") || fullText.contains("원")) {
            parseAndSavePayment(fullText)
        }
    }

    private fun parseAndSavePayment(content: String) {
        scope.launch {
            try {
                val amountPattern = Pattern.compile("([\\d,]+)원")
                val amountMatcher = amountPattern.matcher(content)
                var amount = 0
                if (amountMatcher.find()) {
                    amount = amountMatcher.group(1)?.replace(",", "")?.toIntOrNull() ?: 0
                }

                val storeName = content.split(" ").getOrNull(0) ?: "알 수 없는 상점"

                if (amount > 0) {
                    val payment = Payment(
                        amount = amount,
                        category = "기타",
                        date = System.currentTimeMillis(),
                        itemName = "자동 입력 상품",
                        storeName = storeName
                    )
                    
                    val dao = AppDatabase.getDatabase(applicationContext).paymentDao()
                    dao.insert(payment)
                    
                    // 사용자에게 입력 완료 알림 보내기 (시나리오 4번)
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

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("결제 내역 자동 입력 완료")
            .setContentText("${store}에서 ${price}원이 결제되어 기록되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }
}
