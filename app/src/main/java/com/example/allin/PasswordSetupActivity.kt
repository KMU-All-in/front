package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

            val userEmail = intent.getStringExtra("USER_EMAIL") ?: "user_${System.currentTimeMillis()}@example.com"

            // Firebase 회원가입
            auth.createUserWithEmailAndPassword(userEmail, pw + "00")
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                        
                        // [수정 핵심] 홈 화면(HomeActivity)으로 이동
                        val intent = Intent(this, HomeActivity::class.java)
                        startActivity(intent)
                        finishAffinity() // 이전 액티비티들을 모두 종료
                    } else {
                        Toast.makeText(this, "오류: ${task.exception?.message}", Toast.LENGTH_LONG).show()
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
