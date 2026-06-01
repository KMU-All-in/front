package com.example.allin.data

import android.content.Context
import com.example.allin.BudgetAlertNotifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
            // Log error
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

                    // 2. 통합 알림 및 자동 잠금 로직 호출
                    if (budget > 0) {
                        BudgetAlertNotifier.notifyIfThresholdCrossed(
                            context,
                            budget,
                            currentSpent,
                            newTotalSpent
                        )
                    }
                }
            }
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
