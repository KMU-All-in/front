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
        paymentDao.insert(payment)
        
        // Firestore 동기화 (로그인 된 경우)
        val user = auth.currentUser
        if (user != null) {
            val paymentData = hashMapOf(
                "amount" to payment.amount,
                "storeName" to payment.storeName,
                "date" to payment.date,
                "category" to payment.category,
                "itemName" to payment.itemName
            )
            firestore.collection("users")
                .document(user.uid)
                .collection("payments")
                .add(paymentData)
        }
    }

    // 결제 내역 수정
    suspend fun update(payment: Payment) {
        paymentDao.update(payment)
        // 필요 시 Firestore 수정 로직도 추가 가능
    }

    // 결제 내역 삭제
    suspend fun delete(payment: Payment) {
        paymentDao.delete(payment)
    }
}
