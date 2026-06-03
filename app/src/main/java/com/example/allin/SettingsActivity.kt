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
import android.graphics.Color
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.EmailAuthProvider
import android.text.method.PasswordTransformationMethod
import com.google.firebase.auth.FirebaseAuthException
import android.graphics.Typeface
import com.google.firebase.functions.FirebaseFunctions

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var tvUserEmail: TextView

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

        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvUserEmail.text = user?.email ?: "이메일 정보 없음"

        findViewById<TextView>(R.id.btnEditProfile).setOnClickListener {
            showEmailEditDialog()
        }

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

    private fun showEmailEditDialog() {
        val currentEmail = auth.currentUser?.email

        if (currentEmail.isNullOrEmpty()) {
            Toast.makeText(this, "현재 로그인 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 20, 48, 0)
        }

        val etNewEmail = EditText(this).apply {
            hint = "새 이메일"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }

        val etCurrentPassword = EditText(this).apply {
            hint = "현재 비밀번호"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = Typeface.DEFAULT
            transformationMethod = PasswordTransformationMethod.getInstance()
            setSingleLine(true)
        }

        container.addView(etNewEmail)
        container.addView(etCurrentPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("이메일 수정")
            .setMessage("새 이메일로 인증 메일을 보냅니다. 인증을 완료해야 이메일이 변경됩니다.")
            .setView(container)
            .setPositiveButton("인증 메일 보내기", null)
            .setNegativeButton("취소", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#4F6FFF"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#8B94A8"))

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newEmail = etNewEmail.text.toString().trim()
                val password = etCurrentPassword.text.toString()

                requestEmailChange(currentEmail, newEmail, password, dialog)
            }
        }

        dialog.show()
    }

    private fun requestEmailChange(
        currentEmail: String,
        newEmail: String,
        password: String,
        dialog: AlertDialog
    ) {
        if (newEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            Toast.makeText(this, "올바른 새 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (newEmail == currentEmail) {
            Toast.makeText(this, "현재 이메일과 다른 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "현재 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "로그인 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFunctions.getInstance()
            .getHttpsCallable("checkEmailExists")
            .call(hashMapOf("email" to newEmail))
            .addOnSuccessListener { result ->
                val data = result.getData() as? Map<*, *>
                val emailExists = data?.get("exists") as? Boolean ?: false

                if (emailExists) {
                    Toast.makeText(this, "이미 가입된 이메일입니다. 다른 이메일을 입력해주세요.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                reauthenticateAndSendEmailChange(currentEmail, newEmail, password, dialog)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "이메일 확인 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun reauthenticateAndSendEmailChange(
        currentEmail: String,
        newEmail: String,
        password: String,
        dialog: AlertDialog
    ) {
        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(this, "로그인 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = EmailAuthProvider.getCredential(currentEmail, password)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.verifyBeforeUpdateEmail(newEmail)
                    .addOnSuccessListener {
                        dialog.dismiss()
                        Toast.makeText(
                            this,
                            "새 이메일로 인증 메일을 보냈습니다. 인증 완료 후 다시 로그인해주세요.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, getEmailChangeErrorMessage(e), Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "현재 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getEmailChangeErrorMessage(e: Exception): String {
        val errorCode = (e as? FirebaseAuthException)?.errorCode

        return when (errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "이미 가입된 이메일입니다. 다른 이메일을 입력해주세요."

            "ERROR_INVALID_EMAIL" ->
                "이메일 형식이 올바르지 않습니다."

            "ERROR_REQUIRES_RECENT_LOGIN" ->
                "보안을 위해 다시 로그인한 뒤 이메일 변경을 시도해주세요."

            else ->
                "인증 메일 발송 실패: ${e.message}"
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
