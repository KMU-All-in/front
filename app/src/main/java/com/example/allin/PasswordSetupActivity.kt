package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PasswordSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_setup)

        val etPassword = findViewById<EditText>(R.id.etPassword)
        // [수정] etConfirmPassword -> etPasswordConfirm (레이아웃 ID와 일치)
        val etPasswordConfirm = findViewById<EditText>(R.id.etPasswordConfirm)
        val btnComplete = findViewById<Button>(R.id.btnComplete)

        btnComplete.setOnClickListener {
            val pw = etPassword.text.toString()
            val confirmPw = etPasswordConfirm.text.toString()

            if (pw.isEmpty()) {
                Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pw == confirmPw) {
                val sharedPref = getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("LOCK_PASSWORD", pw)
                    apply()
                }

                Toast.makeText(this, "앱 잠금 비밀번호가 설정되었습니다.", Toast.LENGTH_SHORT).show()
                
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } else {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
