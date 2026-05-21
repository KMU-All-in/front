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

        // 1. 제외 키워드 체크 (광고/공동 계좌 알림 포함)
        val excludeKeywords = listOf("입금", "환불", "취소", "입금완료", "(광고)", "광고", "모임통장", "모임 통장")
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
        val paymentAmountPatterns = listOf(
            Pattern.compile("([\\d,]+)\\s*원\\s*(?:카드)?(?:결제완료|결제|승인|사용|출금)"),
            Pattern.compile("(?:결제완료|결제|승인|사용|출금)\\s*([\\d,]+)\\s*원")
        )

        for (pattern in paymentAmountPatterns) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                return matcher.group(1)?.replace(",", "")?.toIntOrNull() ?: 0
            }
        }

        val wonPattern = Pattern.compile("([\\d,]+)\\s*원")
        val wonMatcher = wonPattern.matcher(content)
        while (wonMatcher.find()) {
            val amountText = wonMatcher.group(1) ?: continue
            val nearbyText = content.substring(wonMatcher.end(), minOf(content.length, wonMatcher.end() + 12))
            if (!nearbyText.contains("캐시백")) {
                return amountText.replace(",", "").toIntOrNull() ?: 0
            }
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
                val storeName = extractStoreName(content, title)

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
            listOf(
                "마트", "편의점", "식당", "카페", "커피", "베이커리", "음식점", "배달", "치킨", "피자",
                "gs25", "cu", "세븐일레븐", "별차이나", "중식", "중국집", "반점", "마라", "짜장", "짬뽕"
            ).any { lowerStore.contains(it) || lowerText.contains(it) } -> "식품/음료"
            listOf("백화점", "쇼핑", "몰", "의류", "패션", "무신사", "지그재그").any { lowerStore.contains(it) || lowerText.contains(it) } -> "패션/의류"
            listOf("올리브영", "화장품", "뷰티", "헤어", "미용실").any { lowerStore.contains(it) || lowerText.contains(it) } -> "뷰티/화장품"
            listOf("하이마트", "전자", "애플", "삼성", "컴퓨터").any { lowerStore.contains(it) || lowerText.contains(it) } -> "전자기기"
            listOf("서점", "교보", "문구", "다이소", "학원", "학교").any { lowerStore.contains(it) || lowerText.contains(it) } -> "도서/문구"
            listOf("마트", "이마트", "홈플러스", "다이소", "생활", "세탁").any { lowerStore.contains(it) || lowerText.contains(it) } -> "생활용품"
            listOf("헬스", "축구", "스포츠", "레저", "골프").any { lowerStore.contains(it) || lowerText.contains(it) } -> "스포츠/레저"
            else -> "기타"
        }
    }

    private fun extractStoreName(content: String, title: String): String {
        val directStorePatterns = listOf(
            Pattern.compile("(?:가맹점|사용처|결제처)[:\\s]+([^\\n\\r]+)"),
            Pattern.compile("([가-힣a-zA-Z0-9()._\\-\\s]+?)에서\\s*[\\d,]+\\s*원"),
            Pattern.compile("[\\d,]+\\s*원\\s*(?:카드)?(?:결제완료|결제|승인|사용|출금)\\s+([^\\n\\r]+)")
        )

        for (pattern in directStorePatterns) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                val candidate = cleanStoreName(matcher.group(1))
                if (candidate.isNotBlank()) return candidate
            }
        }

        val cleanTitle = cleanStoreName(title)
        if (isLikelyStoreName(cleanTitle)) return cleanTitle

        val parts = content.split(Regex("\\s+"))
            .map { cleanStoreName(it) }
            .filter { isLikelyStoreName(it) }

        return parts.firstOrNull() ?: "알 수 없음"
    }

    private fun cleanStoreName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""
        val stopWords = listOf("잔액", "누적", "승인번호", "일시불", "체크", "카드", "계좌", "알림")
        var name = rawName
            .replace(Regex("[\\[\\]]"), " ")
            .replace(Regex("[\\d,]+\\s*원"), " ")
            .replace(Regex("(결제완료|결제|승인|사용|출금)"), " ")
            .trim()

        for (stopWord in stopWords) {
            val index = name.indexOf(stopWord)
            if (index > 0) name = name.substring(0, index).trim()
        }

        return name.replace(Regex("\\s+"), " ").trim()
    }

    private fun isLikelyStoreName(name: String): Boolean {
        if (name.length !in 2..25) return false
        if (Pattern.compile("[\\d,]+\\s*원").matcher(name).find()) return false

        val paymentWords = listOf("결제", "결제완료", "승인", "출금", "입금", "캐시백", "알림", "메시지")
        if (paymentWords.any { name.contains(it) }) return false

        val bankOrCardNames = listOf(
            "토스뱅크", "토스", "카카오뱅크", "케이뱅크", "국민카드", "신한카드", "우리카드", "하나카드",
            "현대카드", "삼성카드", "롯데카드", "농협", "기업은행", "우리은행", "하나은행", "신한은행", "국민은행"
        )
        if (bankOrCardNames.any { name.contains(it) }) return false

        return true
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
