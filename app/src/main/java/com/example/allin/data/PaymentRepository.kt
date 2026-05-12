package com.example.allin.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow

class PaymentRepository(private val paymentDao: PaymentDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 모든 결제 내역 가져오기 (실시간 Flow)
    val allPayments: Flow<List<Payment>> = paymentDao.getAllPayments()

    // 결제 내역 추가
    suspend fun insert(payment: Payment) {
        // 1. 먼저 로컬 Room DB에 저장
        paymentDao.insert(payment)
        
        // 2. Firestore 동기화 (로그인 된 경우)
        val user = auth.currentUser
        if (user != null) {
            val paymentData = hashMapOf(
                "amount" to payment.amount,
                "storeName" to payment.storeName,
                "date" to payment.date,
                "category" to payment.category,
                "itemName" to payment.itemName,
                "timestamp" to com.google.firebase.Timestamp.now() // 정렬을 위한 타임스탬프 추가
            )
            
            // 파이어베이스의 'transactions' 컬렉션에 저장
            firestore.collection("users")
                .document(user.uid)
                .collection("transactions")
                .add(paymentData)
        }
    }

    suspend fun update(payment: Payment) {
        paymentDao.update(payment)
    }

    suspend fun delete(payment: Payment) {
        paymentDao.delete(payment)
    }
}
