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

        val pkgName = sbn?.packageName ?: ""
        if (pkgName == packageName) return 

        val msgApps = listOf("com.kakao.talk", "com.samsung.android.messaging", "com.google.android.apps.messaging")
        
        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullText = "$title $text"

        // 1. 제외 키워드 체크 (입금, 환불 등)
        val excludeKeywords = listOf("입금", "환불", "취소", "입금완료")
        if (excludeKeywords.any { fullText.contains(it) }) return

        // 2. 금액 패턴 확인: 숫자 뒤에 '원'이 있거나, 숫자만 3자리 이상 있는 경우
        // ([\\d,]{3,}) : 쉼표 포함 숫자 3자리 이상
        // (?:\\s*원)? : 뒤에 '원'이 올 수도 있고 안 올 수도 있음 (비캡쳐 그룹)
        val amountPattern = Pattern.compile("([\\d,]{3,})\\s*(?:원)?")
        val amountMatcher = amountPattern.matcher(fullText)
        val hasAmount = amountMatcher.find()

        // 3. 결제 핵심 키워드 체크 ("출금" 추가)
        val payKeywords = listOf("승인", "결제", "일시불", "출금", "카드승인", "자동이체")
        val hasPayKeyword = payKeywords.any { fullText.contains(it) }

        // 4. 메시징 앱 필터링 (메신저 앱은 키워드가 더 확실해야 함)
        if (msgApps.contains(pkgName)) {
            if (!hasPayKeyword) return
        }

        // 최종 조건: 금액 패턴이 발견되고 결제 키워드가 포함된 경우
        if (hasAmount && hasPayKeyword) {
            Log.d("PaymentListener", "결제/출금 알림 감지: $fullText")
            parseAndSavePayment(fullText, title)
        }
    }

    private fun parseAndSavePayment(content: String, title: String) {
        scope.launch {
            try {
                // 한 번 더 정확하게 금액 추출
                val amountPattern = Pattern.compile("([\\d,]{3,})\\s*(?:원)?")
                val amountMatcher = amountPattern.matcher(content)
                var amount = 0
                if (amountMatcher.find()) {
                    val amountStr = amountMatcher.group(1)?.replace(",", "") ?: "0"
                    amount = amountStr.toIntOrNull() ?: 0
                }

                // 상점명 추출 (제목이 있으면 제목 사용)
                val storeName = if (title.length in 2..12 && !title.contains("메시지")) title else {
                    val parts = content.split(" ")
                    if (parts.size > 1) "${parts[0]} ${parts[1]}" else parts[0]
                }

                if (amount > 0) {
                    val payment = Payment(
                        amount = amount,
                        category = "기타",
                        date = System.currentTimeMillis(),
                        itemName = "자동 입력",
                        storeName = storeName
                    )
                    repository.insert(payment)
                    sendCompletionNotification(storeName, amount)
                }
            } catch (e: Exception) {
                Log.e("PaymentListener", "추출/저장 오류", e)
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
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("결제/출금 내역 기록됨")
            .setContentText("${store}에서 ${price}원이 기록되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }
}
