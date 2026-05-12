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
        if (pkgName == packageName) return // 우리 앱 알림 제외

        // 메시징 앱(카톡, 텔레그램, 기본 문자 앱 등) 필터링 - 일상 대화 중 "500원" 방지
        val msgApps = listOf("com.kakao.talk", "com.samsung.android.messaging", "com.google.android.apps.messaging")
        
        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullText = "$title $text"

        // 1. 일상 대화 앱에서 온 알림인데 "승인/결제" 단어가 없으면 무조건 무시
        if (msgApps.contains(pkgName)) {
            if (!fullText.contains("승인") && !fullText.contains("결제") && !fullText.contains("출금")) {
                Log.d("PaymentListener", "메신저 앱의 일반 대화 무시: $fullText")
                return
            }
        }

        // 2. 금액 패턴 확인 (숫자 + 원)
        val amountPattern = Pattern.compile("([\\d,]+)\\s*원")
        val amountMatcher = amountPattern.matcher(fullText)
        val hasAmount = amountMatcher.find()

        // 3. 결제 핵심 키워드 체크
        val payKeywords = listOf("승인", "결제완료", "일시불", "출금완료", "카드승인", "자동이체")
        val hasPayKeyword = payKeywords.any { fullText.contains(it) }

        // 4. 입금/환불 등 제외어
        val excludeKeywords = listOf("입금", "환불", "취소", "입금완료")
        val isExcluded = excludeKeywords.any { fullText.contains(it) }

        // 최종 조건: 금액이 있고 + 결제 키워드가 있고 + 제외어가 없어야 함
        if (hasAmount && hasPayKeyword && !isExcluded) {
            parseAndSavePayment(fullText, title)
        }
    }

    private fun parseAndSavePayment(content: String, title: String) {
        scope.launch {
            try {
                val amountPattern = Pattern.compile("([\\d,]+)\\s*원")
                val amountMatcher = amountPattern.matcher(content)
                var amount = 0
                if (amountMatcher.find()) {
                    val amountStr = amountMatcher.group(1)?.replace(",", "") ?: "0"
                    amount = amountStr.toIntOrNull() ?: 0
                }

                // 상점명 추출 로직: 타이틀(카드사/은행)을 상점명으로 사용하거나 내용의 첫 단어 사용
                val storeName = if (title.length in 2..10 && !title.contains("메시지")) title else content.split(" ").getOrNull(0) ?: "알 수 없는 상점"

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
                Log.e("PaymentListener", "저장 오류", e)
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
            .setContentTitle("결제 내역 자동 기록됨")
            .setContentText("${store}에서 ${price}원을 사용하셨습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }
}
