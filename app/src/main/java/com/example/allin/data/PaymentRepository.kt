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
        // 1. 로컬 DB 저장
        paymentDao.insert(payment)
        
        // 2. Firestore 동기화
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
            
            // transactions 컬렉션에 내역 추가
            firestore.collection("users")
                .document(user.uid)
                .collection("transactions")
                .add(paymentData)

            // 3. HomeActivity에서 사용하는 리포트(총액) 업데이트
            updateReportTotalSpent(user.uid, payment.amount)
        }
    }

    private fun updateReportTotalSpent(uid: String, amount: Int) {
        // 가장 최근 리포트 문서를 찾아 total_spent 필드에 금액을 더함
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

    suspend fun update(payment: Payment) {
        paymentDao.update(payment)
    }

    suspend fun delete(payment: Payment) {
        paymentDao.delete(payment)
    }
}
