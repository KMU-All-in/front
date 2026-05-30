package com.example.allin

import android.content.Context

object NotificationSettings {
    const val PREFS_NAME = "AllInPrefs"
    const val KEY_BUDGET_ALERT = "alert_budget"
    const val KEY_PLAN_ALERT = "alert_plan"
    const val KEY_CART_ALERT = "alert_cart"

    fun isBudgetAlertEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BUDGET_ALERT, true)

    fun isPlanAlertEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PLAN_ALERT, true)

    fun isCartAlertEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CART_ALERT, true)
}