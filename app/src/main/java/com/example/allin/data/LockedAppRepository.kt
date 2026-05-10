package com.example.allin.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LockedAppRepository(private val lockedAppDao: LockedAppDao) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    val allLockedApps: Flow<List<LockedApp>> = lockedAppDao.getAllLockedApps()

    // 여러 앱을 한꺼번에 업데이트 (AppSelectActivity용)
    suspend fun updateAllLockedApps(selectedApps: List<LockedApp>) {
        // 1. Room 업데이트 (기존꺼 다 지우고 새로 넣기)
        lockedAppDao.deleteAll()
        selectedApps.forEach { lockedAppDao.insert(it) }

        // 2. Firestore 동기화
        val user = auth.currentUser ?: return
        val packageNames = selectedApps.map { it.packageName }
        try {
            db.collection("users").document(user.uid)
                .set(mapOf("locked_apps" to packageNames), SetOptions.merge())
                .await()
            Log.d("LockedAppRepository", "Firestore sync all success")
        } catch (e: Exception) {
            Log.e("LockedAppRepository", "Firestore sync all failed", e)
        }
    }

    suspend fun removeLockedApp(packageName: String) {
        // 1. Room에서 삭제
        lockedAppDao.delete(LockedApp(packageName, ""))
        
        // 2. Firestore에서 삭제
        val user = auth.currentUser ?: return
        try {
            val docRef = db.collection("users").document(user.uid)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentApps = snapshot.get("locked_apps") as? MutableList<String> ?: mutableListOf()
                currentApps.remove(packageName)
                transaction.update(docRef, "locked_apps", currentApps)
            }.await()
        } catch (e: Exception) {
            Log.e("LockedAppRepository", "Firestore sync delete failed", e)
        }
    }

    // 앱 시작 시 Firestore 데이터를 Room으로 가져오는 함수
    fun syncFromFirestore(packageManager: android.content.pm.PackageManager) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { snapshot ->
                val apps = snapshot.get("locked_apps") as? List<String> ?: emptyList()
                scope.launch {
                    apps.forEach { pkg ->
                        val appName = try {
                            val info = packageManager.getApplicationInfo(pkg, 0)
                            packageManager.getApplicationLabel(info).toString()
                        } catch (e: Exception) { "Locked App" }
                        lockedAppDao.insert(LockedApp(pkg, appName)) 
                    }
                }
            }
    }
}
