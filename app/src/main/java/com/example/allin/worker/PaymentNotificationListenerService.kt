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

    private var lastAmount: Int = 0
    private var lastProcessedTime: Long = 0
    private val DUPLICATE_INTERVAL = 5000 

    override fun onCreate() {
        super.onCreate()
        val dao = AppDatabase.getDatabase(applicationContext).paymentDao()
        repository = PaymentRepository(dao)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val pkgName = sbn?.packageName ?: ""
        if (pkgName == packageName) return 

        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullText = "$title $text"

        val excludeKeywords = listOf("입금", "환불", "취소", "입금완료")
        if (excludeKeywords.any { fullText.contains(it) }) return

        // 1. 정교화된 금액 추출 호출
        val amount = extractRealAmount(fullText)
        if (amount <= 0) return

        val currentTime = System.currentTimeMillis()
        if (amount == lastAmount && (currentTime - lastProcessedTime) < DUPLICATE_INTERVAL) {
            return
        }

        val payKeywords = listOf("승인", "결제", "일시불", "출금", "카드승인", "자동이체")
        val hasPayKeyword = payKeywords.any { fullText.contains(it) }

        if (hasPayKeyword) {
            lastAmount = amount
            lastProcessedTime = currentTime
            savePayment(fullText, title, amount)
        }
    }

    private fun extractRealAmount(content: String): Int {
        // [1단계] "원"이 붙은 숫자 찾기 (가장 확실함)
        val wonPattern = Pattern.compile("([\\d,]+)\\s*원")
        val wonMatcher = wonPattern.matcher(content)
        if (wonMatcher.find()) {
            return wonMatcher.group(1).replace(",", "").toIntOrNull() ?: 0
        }

        // [2단계] 모든 숫자 후보군 추출 (3자리 이상)
        val allNumbers = mutableListOf<String>()
        val numPattern = Pattern.compile("[\\d,]{3,}")
        val numMatcher = numPattern.matcher(content)
        while (numMatcher.find()) {
            allNumbers.add(numMatcher.group())
        }

        if (allNumbers.isEmpty()) return 0

        // [3단계] 쉼표가 포함된 숫자가 있다면 그것이 금액일 확률이 매우 높음
        val withComma = allNumbers.find { it.contains(",") }
        if (withComma != null) return withComma.replace(",", "").toIntOrNull() ?: 0

        // [4단계] 숫자가 여러 개일 때 카드번호(4자리) 필터링
        if (allNumbers.size > 1) {
            // 4자리가 아닌 숫자가 있다면 그것을 우선 선택 (보통 지출액은 4자리가 아니거나 카드번호보다 뒤에 옴)
            val notFourDigits = allNumbers.filter { it.length != 4 }
            if (notFourDigits.isNotEmpty()) return notFourDigits.last().replace(",", "").toIntOrNull() ?: 0
        }

        // [5단계] 마지막 보루: 가장 마지막에 등장한 숫자 선택
        return allNumbers.last().replace(",", "").toIntOrNull() ?: 0
    }

    private fun savePayment(content: String, title: String, amount: Int) {
        scope.launch {
            try {
                val storeName = if (title.length in 2..12 && !title.contains("메시지")) title else {
                    val parts = content.split(" ")
                    if (parts.size > 1) "${parts[0]} ${parts[1]}" else parts[0]
                }

                val payment = Payment(
                    amount = amount,
                    category = "기타",
                    date = System.currentTimeMillis(),
                    itemName = "자동 입력",
                    storeName = storeName
                )
                repository.insert(payment)
                sendCompletionNotification(storeName, amount)
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
            .setContentTitle("지출 내역 기록됨")
            .setContentText("${store}에서 ${price}원이 기록되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }
}
