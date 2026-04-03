package com.example.allin

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

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnDeleteAccount = findViewById<TextView>(R.id.btnDeleteAccount)
        val tvUserEmail = findViewById<TextView>(R.id.tvUserEmail)

        // 현재 로그인된 유저 이메일 표시
        tvUserEmail.text = auth.currentUser?.email ?: "user@example.com"

        btnBack.setOnClickListener { finish() }

        // 로그아웃 기능
        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            
            // [수정] LoginActivity 대신 실제 존재하는 AllInActivity로 이동
            val intent = Intent(this, AllInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // 계정 삭제 (예시)
        btnDeleteAccount.setOnClickListener {
            Toast.makeText(this, "계정 삭제 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }
    }
}
