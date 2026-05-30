package com.example.allin

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.allin.worker.FakeCartWorker
import com.example.allin.worker.NotificationWorker
import java.util.concurrent.TimeUnit

object NotificationWorkScheduler {
    private const val WEEKLY_PLAN_WORK_NAME = "WeeklyPlanReminderWork"
    private const val FAKE_CART_WORK_NAME = "FakeCartExpiryWork"

    fun syncAll(context: Context) {
        syncWeeklyPlanReminder(context)
        syncFakeCartExpiry(context)
    }

    fun syncWeeklyPlanReminder(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (NotificationSettings.isPlanAlertEnabled(context)) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WEEKLY_PLAN_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(WEEKLY_PLAN_WORK_NAME)
        }
    }

    fun syncFakeCartExpiry(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (NotificationSettings.isCartAlertEnabled(context)) {
            val request = PeriodicWorkRequestBuilder<FakeCartWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                FAKE_CART_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(FAKE_CART_WORK_NAME)
        }
    }
}