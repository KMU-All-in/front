package com.example.allin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class UserInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_info)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val tvError = findViewById<TextView>(R.id.tvError)
        
        val etUserId = findViewById<EditText>(R.id.etUserId)
        val etUserPw = findViewById<EditText>(R.id.etUserPw)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val spAge = findViewById<Spinner>(R.id.spAge)
        val etUserEmail = findViewById<EditText>(R.id.etUserEmail)
        val etUserJob = findViewById<EditText>(R.id.etUserJob)

        // Spinner 설정 (연령대) - 사진과 동일한 목록으로 변경
        val ages = arrayOf("연령대를 선택하세요", "10대", "20대", "30대", "40대", "50대", "60대 이상")
        
        // 커스텀 레이아웃 적용
        val adapter = ArrayAdapter(this, R.layout.spinner_item, ages)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spAge.adapter = adapter

        btnBack.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            val isInfoComplete = etUserId.text.isNotEmpty() &&
                    etUserPw.text.isNotEmpty() &&
                    rgGender.checkedRadioButtonId != -1 &&
                    spAge.selectedItemPosition != 0 &&
                    etUserEmail.text.isNotEmpty() &&
                    etUserJob.text.isNotEmpty()

            if (isInfoComplete) {
                tvError.visibility = View.GONE
                val intent = Intent(this, PasswordSetupActivity::class.java)
                intent.putExtra("USER_EMAIL", etUserEmail.text.toString())
                startActivity(intent)
            } else {
                tvError.visibility = View.VISIBLE
            }
        }
    }
}
