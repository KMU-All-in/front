package com.example.allin.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.allin.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class PaymentRepository(private val paymentDao: PaymentDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val allPayments: Flow<List<Payment>> = paymentDao.getAllPayments()

    suspend fun fetchPaymentsFromFirestore() {
        val user = auth.currentUser ?: return
        try {
            val snapshots = firestore.collection("users").document(user.uid)
                .collection("transactions")
                .get()
                .await()
            
            val payments = snapshots.documents.mapNotNull { doc ->
                val amount = doc.getLong("amount")?.toInt() ?: return@mapNotNull null
                val storeName = doc.getString("storeName") ?: "알 수 없음"
                val date = doc.getLong("date") ?: 0L
                val category = doc.getString("category") ?: "기타"
                val itemName = doc.getString("itemName") ?: ""
                
                Payment(
                    amount = amount,
                    storeName = storeName,
                    date = date,
                    category = category,
                    itemName = itemName
                )
            }
            
            if (payments.isNotEmpty()) {
                paymentDao.deleteAll()
                paymentDao.insertAll(payments)
            }
        } catch (e: Exception) {
            // Log error or handle
        }
    }

    suspend fun insert(payment: Payment, context: Context) {
        paymentDao.insert(payment)
        
        val user = auth.currentUser
        if (user != null) {
            val paymentData = hashMapOf(
                "amount" to payment.amount,
                "storeName" to payment.storeName,
                "date" to payment.date,
                "category" to payment.category,
                "itemName" to payment.itemName,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            
            firestore.collection("users").document(user.uid)
                .collection("transactions").add(paymentData)

            updateReportTotalSpent(user.uid, payment.amount.toLong(), context)
        }
    }

    suspend fun update(oldPayment: Payment, newPayment: Payment, context: Context) {
        paymentDao.update(newPayment)
        
        val user = auth.currentUser
        if (user != null) {
            syncFirestorePaymentUpdate(user.uid, oldPayment, newPayment)

            val diff = (newPayment.amount - oldPayment.amount).toLong()
            if (diff != 0L) {
                updateReportTotalSpent(user.uid, diff, context)
            }
        }
    }

    suspend fun delete(payment: Payment, context: Context) {
        paymentDao.delete(payment)
        
        val user = auth.currentUser
        if (user != null) {
            syncFirestorePaymentDelete(user.uid, payment)
            updateReportTotalSpent(user.uid, -(payment.amount.toLong()), context)
        }
    }

    private fun syncFirestorePaymentUpdate(uid: String, oldPayment: Payment, newPayment: Payment) {
        firestore.collection("users").document(uid)
            .collection("transactions")
            .whereEqualTo("date", oldPayment.date)
            .whereEqualTo("amount", oldPayment.amount)
            .whereEqualTo("storeName", oldPayment.storeName)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                val doc = snapshots.documents.firstOrNull() ?: return@addOnSuccessListener
                doc.reference.update(
                    mapOf(
                        "amount" to newPayment.amount,
                        "storeName" to newPayment.storeName,
                        "category" to newPayment.category,
                        "itemName" to newPayment.itemName,
                        "date" to newPayment.date
                    )
                )
            }
    }

    private fun syncFirestorePaymentDelete(uid: String, payment: Payment) {
        firestore.collection("users").document(uid)
            .collection("transactions")
            .whereEqualTo("date", payment.date)
            .whereEqualTo("amount", payment.amount)
            .whereEqualTo("storeName", payment.storeName)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                snapshots.documents.firstOrNull()?.reference?.delete()
            }
    }

    private fun updateReportTotalSpent(uid: String, amountDelta: Long, context: Context) {
        firestore.collection("users").document(uid)
            .collection("reports")
            .orderBy("start_date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                if (!snapshots.isEmpty) {
                    val doc = snapshots.documents[0]
                    val docId = doc.id
                    val budget = doc.getLong("budget_usage") ?: 0L
                    val currentSpent = doc.getLong("total_spent") ?: 0L
                    val newTotalSpent = currentSpent + amountDelta

                    // 1. 지출 합계 업데이트
                    firestore.collection("users").document(uid)
                        .collection("reports").document(docId)
                        .update("total_spent", newTotalSpent)

                    // 2. 예산 대비 비율 체크 및 알림
                    if (budget > 0) {
                        checkBudgetThreshold(context, budget, currentSpent, newTotalSpent)
                    }
                }
            }
    }

    private fun checkBudgetThreshold(context: Context, budget: Long, oldSpent: Long, newSpent: Long) {
        val oldPercent = (oldSpent.toDouble() / budget * 100).toInt()
        val newPercent = (newSpent.toDouble() / budget * 100).toInt()

        val message = when {
            oldPercent < 100 && newPercent >= 100 -> "예산을 모두 사용함. 이제부터 길냥이정식도 못먹음 짬타이거 ㄱㄱ"
            oldPercent < 80 && newPercent >= 80 -> "이제 길냥이정식 먹을시간이에요."
            oldPercent < 50 && newPercent >= 50 -> "우와 50퍼나 사용했어요."
            else -> null
        }

        if (message != null) {
            sendBudgetNotification(context, message)
        }
    }

    private fun sendBudgetNotification(context: Context, message: String) {
        val channelId = "BudgetThresholdChannel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "예산 사용 알림", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("예산 관리 알림")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(3001, notification)
    }

    suspend fun deleteAll() {
        paymentDao.deleteAll()
        val user = auth.currentUser
        if (user != null) {
            val userRef = firestore.collection("users").document(user.uid)
            userRef.collection("transactions").get().addOnSuccessListener { snapshots ->
                for (doc in snapshots) doc.reference.delete()
            }
            userRef.collection("reports")
                .orderBy("start_date", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshots ->
                    if (!snapshots.isEmpty) {
                        snapshots.documents[0].reference.update("total_spent", 0)
                    }
                }
        }
    }
}
