package com.example.allin

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.allin.worker.FakeCartWorker
import com.example.allin.worker.NotificationWorker
import java.util.Calendar
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
            scheduleNextWeeklyPlanReminder(context)
        } else {
            workManager.cancelUniqueWork(WEEKLY_PLAN_WORK_NAME)
        }
    }

    fun scheduleNextWeeklyPlanReminder(context: Context) {
        if (!NotificationSettings.isPlanAlertEnabled(context)) return

        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(getDelayUntilNextPlanReminderMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WEEKLY_PLAN_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    // 테스트용
    /*
    private fun getDelayUntilNextPlanReminderMillis(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }
    */

    private fun getDelayUntilNextPlanReminderMillis(): Long {
        val now = Calendar.getInstance()

        val next = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            when {
                now.get(Calendar.HOUR_OF_DAY) < 12 -> {
                    set(Calendar.HOUR_OF_DAY, 12)
                }

                now.get(Calendar.HOUR_OF_DAY) < 18 -> {
                    set(Calendar.HOUR_OF_DAY, 18)
                }

                else -> {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 12)
                }
            }
        }

        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
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