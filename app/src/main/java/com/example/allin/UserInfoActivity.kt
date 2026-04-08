package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
        val cbFashion = findViewById<CheckBox>(R.id.cbFashion)
        val cbBeauty = findViewById<CheckBox>(R.id.cbBeauty)
        val cbDigital = findViewById<CheckBox>(R.id.cbDigital)
        val cbFood = findViewById<CheckBox>(R.id.cbFood)

        val btnNext = findViewById<Button>(R.id.btnNext)

        val ages = arrayOf("10대", "20대", "30대", "40대", "50대 이상")
        val ageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ages)
        spAge.adapter = ageAdapter

        btnNext.setOnClickListener {
            val nickname = etNickname.text.toString().trim()
            val email = etUserEmail.text.toString().trim()
            val password = etUserPw.text.toString().trim()
            val passwordConfirm = etUserPwConfirm.text.toString().trim()

            if (nickname.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "필수 정보를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != passwordConfirm) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // [핵심] Firebase 회원가입 실행
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // 가입 성공 시 부가 정보 저장
                        saveUserInfo(nickname, email, spAge.selectedItem.toString(), etUserJob.text.toString())
                        
                        Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                        
                        // 다음 단계(예산 설정)로 이동
                        val intent = Intent(this, BudgetSetupActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "가입 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
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
