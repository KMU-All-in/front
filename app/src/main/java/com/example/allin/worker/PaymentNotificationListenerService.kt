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

        // 1. 제외 키워드 체크 (매우 엄격하게 차단)
        val stopKeywords = listOf(
            "실패", "부족", "거절", "매거진", "뉴스레터", "안내", "장려금", "알림톡", "공지", 
            "제출", "미제출", "청구서", "법인카드", "입금", "환불", "취소", "캐시백"
        )
        if (stopKeywords.any { fullText.contains(it) }) return

        // 2. "만원" 단위가 포함된 경우 차단 (예: 330만원)
        if (fullText.contains("만원")) return

        // 3. 지출 핵심 키워드가 반드시 포함되어야 함
        val payKeywords = listOf("승인", "결제", "일시불", "출금", "카드승인", "자동이체", "사용")
        if (payKeywords.none { fullText.contains(it) }) return

        // 4. 금액 추출 (가장 확실한 패턴만 사용)
        val amount = extractStrictAmount(fullText)
        
        // 500만원 이상의 비정상적 금액(학번 오인식 등) 또는 0원 이하는 무시
        if (amount <= 0 || amount > 5000000) return

        val currentTime = System.currentTimeMillis()
        if (amount == lastAmount && (currentTime - lastProcessedTime) < DUPLICATE_INTERVAL) return

        lastAmount = amount
        lastProcessedTime = currentTime
        savePayment(fullText, title, amount)
    }

    private fun extractStrictAmount(content: String): Int {
        // [수정] 숫자 뒤에 바로 '원'이 붙어있고, 그 앞뒤로 결제 관련어가 있는 경우만 매칭
        // 예: "48,000원 결제", "결제 10,500원"
        val patterns = listOf(
            Pattern.compile("([\\d,]+)원\\s*(?:결제|승인|출금|사용|일시불)"),
            Pattern.compile("(?:결제|승인|출금|사용|일시불)\\s*([\\d,]+)원"),
            Pattern.compile("([\\d,]+)원") // 마지막 수단
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                val amountStr = matcher.group(1).replace(",", "")
                // 학번(7~8자리) 오인식 방지: 금액 문자열 길이를 제한
                if (amountStr.length > 7) continue 
                return amountStr.toIntOrNull() ?: 0
            }
        }
        return 0
    }

    private fun savePayment(content: String, title: String, amount: Int) {
        scope.launch {
            try {
                val storeName = extractStoreName(content, title)
                val category = classifyCategory(storeName, content)

                val payment = Payment(
                    amount = amount,
                    category = category,
                    date = System.currentTimeMillis(),
                    itemName = "자동 입력",
                    storeName = storeName
                )
                repository.insert(payment, applicationContext)
                sendCompletionNotification(storeName, amount, category)
            } catch (e: Exception) {
                Log.e("PaymentListener", "저장 오류", e)
            }
        }
    }

    private fun classifyCategory(storeName: String, fullText: String): String {
        val lowerStore = storeName.lowercase()
        return when {
            listOf("마트", "편의점", "gs25", "cu", "세븐일레븐").any { lowerStore.contains(it) } -> "식품/음료"
            listOf("백화점", "쇼핑", "몰", "무신사", "지그재그").any { lowerStore.contains(it) } -> "패션/의류"
            else -> "기타"
        }
    }

    private fun extractStoreName(content: String, title: String): String {
        val directStorePatterns = listOf(
            Pattern.compile("(?:가맹점|사용처|결제처)[:\\s]+([^\\n\\r]+)"),
            Pattern.compile("([가-힣a-zA-Z0-9()._\\-\\s]+?)에서\\s*[\\d,]+원"),
            Pattern.compile("[\\d,]+원\\s*(?:카드)?(?:결제완료|결제|승인|사용|출금)\\s+([^\\n\\r]+)")
        )

        for (pattern in directStorePatterns) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                val candidate = matcher.group(1).trim()
                if (candidate.isNotBlank()) return candidate
            }
        }
        return title.ifBlank { "알 수 없음" }
    }

    private fun sendCompletionNotification(store: String, price: Int, category: String) {
        val channelId = "PaymentInputChannel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "결제 자동 입력", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, HomeActivity::class.java)
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
