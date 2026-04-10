package com.example.allin.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FakeCartRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // [수정] 스크린샷의 구조에 맞춰 경로를 users/{uid}/fakecart 로 변경
    private val collectionPath: String?
        get() = auth.currentUser?.uid?.let { "users/$it/fakecart" }

    // 모든 상품 실시간으로 가져오기
    val allProducts: Flow<List<FakeProduct>> = callbackFlow {
        val path = collectionPath
        if (path == null) {
            trySend(emptyList())
            return@callbackFlow
        }

        val registration = firestore.collection(path)
            .orderBy("addedTime")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.toObjects(FakeProduct::class.java) ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    // 상품 추가 또는 수정
    suspend fun insert(product: FakeProduct) {
        val path = collectionPath ?: return
        firestore.collection(path).document(product.id).set(product).await()
    }

    // 상품 삭제
    suspend fun delete(product: FakeProduct) {
        val path = collectionPath ?: return
        firestore.collection(path).document(product.id).delete().await()
    }
}
