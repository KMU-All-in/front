package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        hideSystemBars()
        auth = FirebaseAuth.getInstance()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AllInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        val user = auth.currentUser
        findViewById<TextView>(R.id.tvUserName).text = user?.displayName ?: "사용자"
        findViewById<TextView>(R.id.tvUserEmail).text = user?.email ?: "이메일 정보 없음"

        // A1: 알림 설정 스위치 로직
        setupNotificationSwitches()
    }

    private fun setupNotificationSwitches() {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)

        val swBudget = findViewById<SwitchCompat>(R.id.swBudgetAlert)
        val swPlan = findViewById<SwitchCompat>(R.id.swPlanAlert)
        val swCart = findViewById<SwitchCompat>(R.id.swCartAlert)

        swBudget.isChecked = prefs.getBoolean(NotificationSettings.KEY_BUDGET_ALERT, true)
        swPlan.isChecked = prefs.getBoolean(NotificationSettings.KEY_PLAN_ALERT, true)
        swCart.isChecked = prefs.getBoolean(NotificationSettings.KEY_CART_ALERT, true)

        swBudget.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(NotificationSettings.KEY_BUDGET_ALERT, isChecked)
                .apply()
        }

        swPlan.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(NotificationSettings.KEY_PLAN_ALERT, isChecked)
                .apply()

            NotificationWorkScheduler.syncWeeklyPlanReminder(this)
        }

        swCart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(NotificationSettings.KEY_CART_ALERT, isChecked)
                .apply()

            NotificationWorkScheduler.syncFakeCartExpiry(this)
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }


}
