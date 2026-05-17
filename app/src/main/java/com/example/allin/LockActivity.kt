package com.example.allin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LockActivity : AppCompatActivity() {

    private lateinit var tvLockAdvice: TextView
    private lateinit var pinIndicatorContainer: android.widget.LinearLayout
    private var currentPin = ""
    private var correctPin = "1234" // 기본값, Firestore에서 가져옵니다.

    private var targetPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        tvLockAdvice = findViewById(R.id.tvLockAdvice)
        pinIndicatorContainer = findViewById(R.id.pinIndicatorContainer)

        setupKeypad()
        updatePinIndicators()
        
        // Firestore에서 실제 PIN 가져오기
        fetchLockPin()

        targetPackageName = intent.getStringExtra("PACKAGE_NAME")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 새로 들어온 인텐트로 교체
        targetPackageName = intent.getStringExtra("PACKAGE_NAME") // 패키지명 다시 확실하게 백업!
    }

    private fun fetchLockPin() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.contains("lock_pin")) {
                    correctPin = document.getString("lock_pin") ?: "1234"
                }
            }
    }

    private fun setupKeypad() {
        findAndSetNumericButtons(findViewById(android.R.id.content))

        findViewById<ImageButton>(R.id.btnBackspace).setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.substring(0, currentPin.length - 1)
                updatePinIndicators()
            }
        }
    }

    private fun findAndSetNumericButtons(view: View) {
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndSetNumericButtons(view.getChildAt(i))
            }
        } else if (view is Button) {
            val text = view.text.toString()
            if (text.length == 1 && text[0].isDigit()) {
                view.setOnClickListener {
                    if (currentPin.length < 4) {
                        currentPin += text
                        updatePinIndicators()
                        if (currentPin.length == 4) {
                            verifyPin()
                        }
                    }
                }
            }
        }
    }

    private fun updatePinIndicators() {
        for (i in 0 until pinIndicatorContainer.childCount) {
            val dot = pinIndicatorContainer.getChildAt(i)
            if (i < currentPin.length) {
                dot.setBackgroundResource(R.drawable.pin_dot_on)
            } else {
                dot.setBackgroundResource(R.drawable.pin_dot_off)
            }
        }
    }

    private fun verifyPin() {
        if (currentPin == correctPin) {
            Toast.makeText(this, "잠금 해제되었습니다.", Toast.LENGTH_SHORT).show()
            resetFailCount()

            val targetPackage = targetPackageName ?: intent.getStringExtra("PACKAGE_NAME")

            android.util.Log.d("LockActivity", "면죄부 발급 패키지명: $targetPackage")
            com.example.allin.worker.AppMonitorService.unlockedAppPackage = targetPackage

            finish()
        } else {
            handleFail()
            currentPin = ""
            updatePinIndicators()
            Toast.makeText(this, "비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleFail() {
        val sharedPref = getSharedPreferences("LockPrefs", Context.MODE_PRIVATE)
        val failCount = sharedPref.getInt("FAIL_COUNT", 0) + 1
        sharedPref.edit().putInt("FAIL_COUNT", failCount).apply()

        if (failCount == 5 || failCount == 8 || failCount == 10) {
            showFailNotification(failCount)
        }
    }

    private fun resetFailCount() {
        val sharedPref = getSharedPreferences("LockPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putInt("FAIL_COUNT", 0).apply()
    }

    private fun showFailNotification(count: Int) {
        val channelId = "LockFailChannel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "보안 알림", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, AppLockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_CHANGE_PASSWORD", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("비밀번호 입력 ${count}회 실패")
            .setContentText("보안을 위해 비밀번호를 변경하시겠습니까?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    override fun onBackPressed() {
        super.onBackPressed()

        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}
