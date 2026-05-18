package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import java.util.regex.Pattern

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

            // [추가] 비밀번호 제약: 영문 + 숫자 혼합, 8자리 이상
            val passwordPattern = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")
            if (!passwordPattern.matcher(password).matches()) {
                Toast.makeText(this, "비밀번호는 영문과 숫자를 혼합하여 8자리 이상이어야 합니다.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnNext.isEnabled = false
            btnNext.text = "가입 처리 중..."

            // Firebase 회원가입
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        
                        // [추가] 이메일 인증 메일 발송
                        user?.sendEmailVerification()
                            ?.addOnCompleteListener { verifyTask ->
                                if (verifyTask.isSuccessful) {
                                    Toast.makeText(this, "인증 메일이 발송되었습니다. 이메일을 확인해 주세요!", Toast.LENGTH_LONG).show()
                                }
                            }

                        saveUserInfo(nickname, email, spAge.selectedItem.toString(), etUserJob.text.toString())
                        
                        // 인증 전에는 접근을 제한하기 위해 로그아웃 처리
                        auth.signOut()
                        
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
