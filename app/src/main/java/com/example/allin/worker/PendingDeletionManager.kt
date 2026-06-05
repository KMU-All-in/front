package com.example.allin.worker

import android.os.Handler
import android.os.Looper
import android.util.Log

object PendingDeletionManager {
    private val handler = Handler(Looper.getMainLooper())
    private val pendingTasks = mutableMapOf<String, Runnable>()

    fun scheduleDeletion(productId: String, delayMillis: Long, onTimeout: () -> Unit) {
        cancelDeletion(productId)
        val task = Runnable { 
            Log.d("PendingDeletion", "Executing auto-deletion for $productId")
            onTimeout()
            pendingTasks.remove(productId)
        }
        pendingTasks[productId] = task
        handler.postDelayed(task, delayMillis)
        Log.d("PendingDeletion", "Scheduled deletion for $productId in $delayMillis ms")
    }

    fun cancelDeletion(productId: String) {
        pendingTasks[productId]?.let {
            handler.removeCallbacks(it)
            pendingTasks.remove(productId)
            Log.d("PendingDeletion", "Cancelled deletion for $productId")
        }
    }
}
