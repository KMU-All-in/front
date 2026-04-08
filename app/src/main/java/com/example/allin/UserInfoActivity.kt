package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class UserInfoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_info)

        auth = FirebaseAuth.getInstance()

        val etNickname = findViewById<EditText>(R.id.etNickname)
        val etUserEmail = findViewById<EditText>(R.id.etUserEmail)
        val etUserPw = findViewById<EditText>(R.id.etUserPw)
        val etUserPwConfirm = findViewById<EditText>(R.id.etUserPwConfirm)
        
        val spAge = findViewById<Spinner>(R.id.spAge)
        val etUserJob = findViewById<EditText>(R.id.etUserJob)
        val btnNext = findViewById<Button>(R.id.btnNext)

        val ages = arrayOf("10대", "20대", "30대", "40대", "50대 이상")
        val ageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ages)
        spAge.adapter = ageAdapter

        btnNext.setOnClickListener {
            val nickname = etNickname.text.toString().trim()
            val email = etUserEmail.text.toString().trim()
            val password = etUserPw.text.toString().trim()
            val passwordConfirm = etUserPwConfirm.text.toString().trim()

            if (nickname.isEmpty() || email.isEmpty() || password.isEmpty() || passwordConfirm.isEmpty()) {
                Toast.makeText(this, "모든 필수 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != passwordConfirm) {
                Toast.makeText(this, "비밀번호가 서로 다릅니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 최소 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnNext.isEnabled = false
            btnNext.text = "가입 처리 중..."

            // [핵심] Firebase 회원가입
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        saveUserInfo(nickname, email, spAge.selectedItem.toString(), etUserJob.text.toString())
                        
                        // 회원가입 직후에는 로그아웃 처리 (로그인 화면에서 다시 로그인하도록 유도)
                        auth.signOut()
                        
                        Toast.makeText(this, "회원가입이 완료되었습니다! 로그인해 주세요.", Toast.LENGTH_SHORT).show()
                        
                        // [수정] 로그인 화면(AllInActivity)으로 이동하며 스택 제거
                        val intent = Intent(this, AllInActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        btnNext.isEnabled = true
                        btnNext.text = "다음 단계로"
                        val errorMsg = task.exception?.message ?: "오류가 발생했습니다."
                        Toast.makeText(this, "가입 실패: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun saveUserInfo(nickname: String, email: String, age: String, job: String) {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("NICKNAME", nickname)
            putString("EMAIL", email)
            putString("AGE", age)
            putString("JOB", job)
            apply()
        }
    }
}
