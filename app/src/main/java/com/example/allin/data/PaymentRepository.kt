package com.example.allin.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow

class PaymentRepository(private val paymentDao: PaymentDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val allPayments: Flow<List<Payment>> = paymentDao.getAllPayments()

    suspend fun insert(payment: Payment) {
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
            
            firestore.collection("users")
                .document(user.uid)
                .collection("transactions")
                .add(paymentData)

            updateReportTotalSpent(user.uid, payment.amount)
        }
    }

    private fun updateReportTotalSpent(uid: String, amount: Int) {
        firestore.collection("users").document(uid)
            .collection("reports")
            .orderBy("start_date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                if (!snapshots.isEmpty) {
                    val docId = snapshots.documents[0].id
                    firestore.collection("users").document(uid)
                        .collection("reports").document(docId)
                        .update("total_spent", FieldValue.increment(amount.toLong()))
                }
            }
    }

    // 완전한 초기화 기능 (로컬 DB + 서버 내역 + 서버 총액)
    suspend fun deleteAll() {
        // 1. 로컬 DB 삭제
        paymentDao.deleteAll()
        
        val user = auth.currentUser
        if (user != null) {
            val userRef = firestore.collection("users").document(user.uid)

            // 2. 서버의 모든 결제 내역(transactions) 삭제
            userRef.collection("transactions").get().addOnSuccessListener { snapshots ->
                for (doc in snapshots) doc.reference.delete()
            }

            // 3. 서버 리포트의 총 지출액을 0으로 초기화
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

    suspend fun update(payment: Payment) {
        paymentDao.update(payment)
    }

    suspend fun delete(payment: Payment) {
        paymentDao.delete(payment)
    }
}
