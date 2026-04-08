package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()

        // [추가] 뒤로가기 버튼 기능
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // 로그아웃 버튼
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AllInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        
        // 사용자 정보 표시 (예시)
        val user = auth.currentUser
        findViewById<TextView>(R.id.tvUserName).text = user?.displayName ?: "사용자"
        findViewById<TextView>(R.id.tvUserEmail).text = user?.email ?: "이메일 정보 없음"
    }
}
