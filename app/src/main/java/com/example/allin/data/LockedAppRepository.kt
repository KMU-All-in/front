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
        lockedAppDao.deleteAll()
        selectedApps.forEach { lockedAppDao.insert(it) }

        val user = auth.currentUser ?: return
        val appStateMap = selectedApps.associate { it.packageName to it.isActive }
        try {
            db.collection("users").document(user.uid)
                .set(mapOf("locked_apps" to appStateMap), SetOptions.merge())
                .await()
            Log.d("LockedAppRepository", "Firestore sync all success")
        } catch (e: Exception) {
            Log.e("LockedAppRepository", "Firestore sync all failed", e)
        }
    }

    suspend fun removeLockedApp(packageName: String) {
        val currentUser = auth.currentUser ?: return

        // 1. Room에서 삭제
        lockedAppDao.delete(LockedApp(packageName, "", false))

        // 2. Firestore에서 삭제
        try {
            val docRef = db.collection("users").document(currentUser.uid)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentAppStates = (snapshot.get("locked_apps") as? Map<String, Boolean>)?.toMutableMap() ?: mutableMapOf()
                currentAppStates.remove(packageName)
                transaction.update(docRef, "locked_apps", currentAppStates)
            }.await()
            Log.d("LockedAppRepository", "App removed successfully: $packageName")
        } catch (e: Exception) {
            Log.e("LockedAppRepository", "Firestore sync delete failed", e)
        }
    }

    // 앱 시작 시 Firestore 데이터를 Room으로 가져오는 함수
    fun syncFromFirestore(packageManager: android.content.pm.PackageManager) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { snapshot ->
                scope.launch {
                    lockedAppDao.deleteAll()

                    val appStatesMap = snapshot.get("locked_apps") as? Map<String, Boolean>
                    if (appStatesMap != null) {
                        appStatesMap.forEach { (pkg, isActive) ->
                            val appName = try {
                                val info = packageManager.getApplicationInfo(pkg, 0)
                                packageManager.getApplicationLabel(info).toString()
                            } catch (e: Exception) { "Locked App" }
                            lockedAppDao.insert(LockedApp(pkg, appName, isActive))
                        }
                    } else {
                        val appStatesList = snapshot.get("locked_apps") as? List<String> ?: emptyList()
                        appStatesList.forEach { pkg ->
                            val appName = try {
                                val info = packageManager.getApplicationInfo(pkg, 0)
                                packageManager.getApplicationLabel(info).toString()
                            } catch (e: Exception) { "Locked App" }
                            lockedAppDao.insert(LockedApp(pkg, appName, true))
                        }
                    }

                    Log.d("LockedAppRepository", "Sync from Firestore completed")
                }
            }
    }

    suspend fun updateLockedAppStatus(packageName: String, isActive: Boolean) {
        val lockedApp = lockedAppDao.getLockedApp(packageName)
        if (lockedApp != null) {
            lockedAppDao.updateLockedApp(lockedApp.copy(isActive = isActive))
        }

        val currentUser = auth.currentUser ?: return
        try {
            val docRef = db.collection("users").document(currentUser.uid)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentAppStates = when (val lockedApps = snapshot.get("locked_apps")) {
                    is Map<*, *> -> lockedApps.mapNotNull { (key, value) ->
                        val appPackage = key as? String ?: return@mapNotNull null
                        appPackage to (value as? Boolean ?: true)
                    }.toMap().toMutableMap()
                    is List<*> -> lockedApps.mapNotNull { it as? String }
                        .associateWith { true }
                        .toMutableMap()
                    else -> mutableMapOf()
                }

                currentAppStates[packageName] = isActive
                transaction.set(docRef, mapOf("locked_apps" to currentAppStates), SetOptions.merge())
            }.await()
            Log.d("LockedAppRepository", "App status synced: $packageName -> $isActive")
        } catch (e: Exception) {
            Log.e("LockedAppRepository", "Firestore sync status failed", e)
        }
    }
}
