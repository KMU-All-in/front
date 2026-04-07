package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class PasswordSetupActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_setup)

        auth = Firebase.auth

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val btnComplete = findViewById<Button>(R.id.btnComplete)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etPasswordConfirm = findViewById<EditText>(R.id.etPasswordConfirm)
        
        val dots = arrayOf(
            findViewById<View>(R.id.dot1), findViewById<View>(R.id.dot2),
            findViewById<View>(R.id.dot3), findViewById<View>(R.id.dot4)
        )
        
        val dotsConfirm = arrayOf(
            findViewById<View>(R.id.dotConfirm1), findViewById<View>(R.id.dotConfirm2),
            findViewById<View>(R.id.dotConfirm3), findViewById<View>(R.id.dotConfirm4)
        )

        etPassword.addTextChangedListener(createWatcher(dots))
        etPasswordConfirm.addTextChangedListener(createWatcher(dotsConfirm))

        findViewById<View>(R.id.layoutPw).setOnClickListener { showKeyboard(etPassword) }
        findViewById<View>(R.id.layoutPwConfirm).setOnClickListener { showKeyboard(etPasswordConfirm) }

        btnBack.setOnClickListener { finish() }

        btnComplete.setOnClickListener {
            val pw = etPassword.text.toString()
            val pwConfirm = etPasswordConfirm.text.toString()

            if (pw.length < 4 || pwConfirm.length < 4) {
                Toast.makeText(this, "비밀번호 4자리를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pw != pwConfirm) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 이전 단계에서 전달받은 이메일 (없으면 테스트용 생성)
            val userEmail = intent.getStringExtra("USER_EMAIL") ?: "user_${System.currentTimeMillis()}@example.com"
            val finalPassword = pw + "0000" // Firebase 최소 길이를 위해 8자리로 생성

            btnComplete.isEnabled = false
            
            auth.createUserWithEmailAndPassword(userEmail, finalPassword)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // [추가] 앱 잠금용 비밀번호를 기기에 저장 (쇼핑 앱 차단 시 사용)
                        val sharedPref = getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)
                        sharedPref.edit().putString("LOCK_PW", pw).apply()

                        Toast.makeText(this, "회원가입이 완료되었습니다! 로그인해주세요.", Toast.LENGTH_LONG).show()
                        
                        // 로그아웃 후 로그인 화면(AllInActivity)으로 이동
                        auth.signOut()
                        val intent = Intent(this, AllInActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish() 
                    } else {
                        btnComplete.isEnabled = true
                        val errorMsg = task.exception?.message ?: "회원가입 실패"
                        Toast.makeText(this, "오류: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun showKeyboard(editText: EditText) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun createWatcher(dots: Array<View>) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val length = s?.length ?: 0
            for (i in 0 until 4) {
                dots[i].visibility = if (i < length) View.VISIBLE else View.INVISIBLE
            }
        }
        override fun afterTextChanged(s: Editable?) {}
    }
}
