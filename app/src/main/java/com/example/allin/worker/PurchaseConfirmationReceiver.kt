package com.example.allin.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.allin.data.FakeCartRepository
import com.example.allin.data.FakeProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PurchaseConfirmationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val productId = intent.getStringExtra("product_id") ?: return
        val productName = intent.getStringExtra("product_name") ?: ""

        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(3001) // Cancel the confirmation notification

        if (action == "KEEP_PRODUCT") {
            Log.d("PurchaseReceiver", "User chose to KEEP product $productId")
            PendingDeletionManager.cancelDeletion(productId)
        } else if (action == "AUTO_DELETE_PRODUCT") {
            Log.d("PurchaseReceiver", "Triggering deletion for $productId")
            context?.let { ctx ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = FakeCartRepository()
                        repository.delete(FakeProduct(id = productId))
                        Log.d("PurchaseReceiver", "Successfully deleted $productId")
                    } catch (e: Exception) {
                        Log.e("PurchaseReceiver", "Failed to delete $productId", e)
                    }
                }
            }
        }
    }
}
