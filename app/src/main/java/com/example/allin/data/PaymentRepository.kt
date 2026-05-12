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
            
            firestore.collection("users").document(user.uid)
                .collection("transactions").add(paymentData)

            updateReportTotalSpent(user.uid, payment.amount.toLong())
        }
    }

    // [수정] 차액만큼 서버 총액 업데이트
    suspend fun update(oldPayment: Payment, newPayment: Payment) {
        paymentDao.update(newPayment)
        
        val user = auth.currentUser
        if (user != null) {
            val diff = (newPayment.amount - oldPayment.amount).toLong()
            if (diff != 0L) {
                updateReportTotalSpent(user.uid, diff)
            }
        }
    }

    // [수정] 삭제 시 서버 총액에서 차감
    suspend fun delete(payment: Payment) {
        paymentDao.delete(payment)
        
        val user = auth.currentUser
        if (user != null) {
            updateReportTotalSpent(user.uid, -(payment.amount.toLong()))
        }
    }

    private fun updateReportTotalSpent(uid: String, amountDelta: Long) {
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
                        .update("total_spent", FieldValue.increment(amountDelta))
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
