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

        // 1. 제외 키워드 체크 (광고 포함)
        val excludeKeywords = listOf("입금", "환불", "취소", "입금완료", "(광고)", "광고")
        if (excludeKeywords.any { fullText.contains(it) }) return

        // 2. 정교화된 금액 추출 호출
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
        val wonPattern = Pattern.compile("([\\d,]+)\\s*원")
        val wonMatcher = wonPattern.matcher(content)
        if (wonMatcher.find()) {
            return wonMatcher.group(1).replace(",", "").toIntOrNull() ?: 0
        }

        val allNumbers = mutableListOf<String>()
        val numPattern = Pattern.compile("[\\d,]{3,}")
        val numMatcher = numPattern.matcher(content)
        while (numMatcher.find()) {
            allNumbers.add(numMatcher.group())
        }

        if (allNumbers.isEmpty()) return 0

        val withComma = allNumbers.find { it.contains(",") }
        if (withComma != null) return withComma.replace(",", "").toIntOrNull() ?: 0

        if (allNumbers.size > 1) {
            val notFourDigits = allNumbers.filter { it.length != 4 }
            if (notFourDigits.isNotEmpty()) return notFourDigits.last().replace(",", "").toIntOrNull() ?: 0
        }

        return allNumbers.last().replace(",", "").toIntOrNull() ?: 0
    }

    private fun savePayment(content: String, title: String, amount: Int) {
        scope.launch {
            try {
                val storeName = if (title.length in 2..12 && !title.contains("메시지")) title else {
                    val parts = content.split(" ")
                    if (parts.size > 1) "${parts[0]} ${parts[1]}" else parts[0]
                }

                // 카테고리 자동 분류
                val category = classifyCategory(storeName, content)

                val payment = Payment(
                    amount = amount,
                    category = category,
                    date = System.currentTimeMillis(),
                    itemName = "자동 입력",
                    storeName = storeName
                )
                // [수정] context를 전달하여 예산 알림이 가능하게 함
                repository.insert(payment, applicationContext)
                sendCompletionNotification(storeName, amount, category)
            } catch (e: Exception) {
                Log.e("PaymentListener", "저장 오류", e)
            }
        }
    }

    private fun classifyCategory(storeName: String, fullText: String): String {
        val lowerStore = storeName.lowercase()
        val lowerText = fullText.lowercase()

        return when {
            listOf("마트", "편의점", "식당", "카페", "커피", "베이커리", "음식점", "배달", "치킨", "피자", "GS25", "CU", "세븐일레븐").any { lowerStore.contains(it) || lowerText.contains(it) } -> "식품/음료"
            listOf("백화점", "쇼핑", "몰", "의류", "패션", "무신사", "지그재그").any { lowerStore.contains(it) || lowerText.contains(it) } -> "패션/의류"
            listOf("올리브영", "화장품", "뷰티", "헤어", "미용실").any { lowerStore.contains(it) || lowerText.contains(it) } -> "뷰티/화장품"
            listOf("하이마트", "전자", "애플", "삼성", "컴퓨터").any { lowerStore.contains(it) || lowerText.contains(it) } -> "전자기기"
            listOf("서점", "교보", "문구", "다이소", "학원", "학교").any { lowerStore.contains(it) || lowerText.contains(it) } -> "도서/문구"
            listOf("마트", "이마트", "홈플러스", "다이소", "생활", "세탁").any { lowerStore.contains(it) || lowerText.contains(it) } -> "생활용품"
            listOf("헬스", "축구", "스포츠", "레저", "골프").any { lowerStore.contains(it) || lowerText.contains(it) } -> "스포츠/레저"
            else -> "기타"
        }
    }

    private fun sendCompletionNotification(store: String, price: Int, category: String) {
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
            .setContentTitle("[$category] 지출 기록됨")
            .setContentText("${store}에서 ${price}원이 기록되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }
}
