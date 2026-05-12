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
        // Repository 초기화 (Dao 주입)
        val dao = AppDatabase.getDatabase(applicationContext).paymentDao()
        repository = PaymentRepository(dao)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullText = "$title $text"

        // "결제", "승인" 키워드 혹은 "원" 단위가 포함된 알림만 가공
        if (fullText.contains("결제") || fullText.contains("승인") || fullText.contains("원")) {
            parseAndSavePayment(fullText)
        }
    }

    private fun parseAndSavePayment(content: String) {
        scope.launch {
            try {
                // 1. 금액 추출 (예: 5,000원 -> 5000)
                val amountPattern = Pattern.compile("([\\d,]+)원")
                val amountMatcher = amountPattern.matcher(content)
                var amount = 0
                if (amountMatcher.find()) {
                    amount = amountMatcher.group(1)?.replace(",", "")?.toIntOrNull() ?: 0
                }

                // 2. 상점명 추출 (알림의 첫 단어를 상점명으로 가정)
                val storeName = content.split(" ").getOrNull(0) ?: "알 수 없는 상점"

                if (amount > 0) {
                    val payment = Payment(
                        amount = amount,
                        category = "기타",
                        date = System.currentTimeMillis(),
                        itemName = "자동 입력 상품",
                        storeName = storeName
                    )
                    
                    // 3. Repository를 통해 로컬 DB 저장 + Firestore(transactions) 동기화
                    repository.insert(payment)
                    
                    // 4. 입력 완료 시스템 알림 발송
                    sendCompletionNotification(storeName, amount)
                }
            } catch (e: Exception) {
                Log.e("PaymentListener", "결제 분석/저장 중 오류 발생", e)
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
