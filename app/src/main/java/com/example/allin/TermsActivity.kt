package com.example.allin

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class TermsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val tvError = findViewById<TextView>(R.id.tvError)

        // 각 약관 제목 클릭 시 다이얼로그 띄우기
        setupTerm(R.id.tvTermTitle1, R.id.cbTerm1, "서비스 이용약관", "제1조 (목적)\n" +
                "이 약관은 충동구매 방지 서비스(이하 \"서비스\")의 이용과 관련하여 회사와 이용자의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.\n" +
                "\n" +
                "제2조 (정의)\n" +
                "1. \"서비스\"란 회원이 충동구매를 방지하고 건전한 소비 습관을 형성할 수 있도록 돕는 모든 서비스를 의미합니다.\n" +
                "2. \"회원\"이란 본 약관에 동의하고 서비스를 이용하는 자를 말합니다.\n" +
                "\n" +
                "제3조 (서비스의 제공)\n" +
                "회사는 다음과 같은 서비스를 제공합니다:\n" +
                "1. 구매 전 대기 시간 설정 기능\n" +
                "2. 소비 패턴 분석 및 리포트\n" +
                "3. 예산 관리 도구\n" +
                "4. 구매 결정 지원 도구\n" +
                "\n" +
                "제4조 (회원의 의무)\n" +
                "1. 회원은 정확한 정보를 제공해야 합니다.\n" +
                "2. 회원은 타인의 정보를 도용해서는 안 됩니다.\n" +
                "3. 회원은 서비스를 부정한 목적으로 이용해서는 안 됩니다.\n")
        setupTerm(R.id.tvTermTitle2, R.id.cbTerm2, "알림 분석 동의", "제1조 (알림 분석의 목적)\n" +
                "앱은 사용자의 쇼핑 관련 알림을 분석하여 충동구매 패턴을 파악합니다.\n" +
                "\n" +
                "제2조 (수집 정보)\n" +
                "1. 쇼핑앱 알림 내용\n" +
                "2. 알림 수신 시간\n" +
                "3. 알림에 대한 사용자 반응\n" +
                "\n" +
                "제3조 (정보의 활용)\n" +
                "수집된 정보는 다음의 목적으로 활용됩니다:\n" +
                "1. 충동구매 패턴 분석\n" +
                "2. 맞춤형 소비 습관 개선 제안\n" +
                "3. 통계 및 서비스 개선\n" +
                "\n" +
                "제4조 (정보 보안)\n" +
                "모든 알림 분석 데이터는 암호화되어 저장되며, 제3자에게 제공되지 않습니다.\n")
        setupTerm(R.id.tvTermTitle3, R.id.cbTerm3, "문자 분석 동의", "제1조 (문자 분석의 목적)\n" +
                "앱은 쇼핑 관련 문자 메시지를 분석하여 사용자의 소비 패턴을 파악합니다.\n" +
                "\n" +
                "제2조 (수집 정보)\n" +
                "1. 쇼핑 관련 문자 메시지 내용\n" +
                "2. 배송 알림 정보\n" +
                "3. 결제 승인 메시지\n" +
                "\n" +
                "제3조 (정보의 활용)\n" +
                "수집된 정보는 다음의 목적으로 활용됩니다:\n" +
                "1. 실제 구매 내역 추적\n" +
                "2. 예산 대비 지출 분석\n" +
                "3. 소비 패턴 리포트 생성\n" +
                "\n" +
                "제4조 (사용자 권리)\n" +
                "사용자는 언제든지 문자 분석 기능을 비활성화할 수 있습니다.\n")
        setupTerm(R.id.tvTermTitle4, R.id.cbTerm4, "알람 설정 동의", "제1조 (알람의 목적)\n" +
                "앱은 사용자가 설정한 시간에 알람을 통해 충동구매 방지를 돕습니다.\n" +
                "\n" +
                "제2조 (알람 종류)\n" +
                "1. 구매 대기 시간 만료 알람\n" +
                "2. 일일/주간 예산 초과 경고\n" +
                "3. 소비 습관 개선 리마인더\n" +
                "\n" +
                "제3조 (알람 설정)\n" +
                "사용자는 알람의 시간, 빈도, 종류를 자유롭게 설정할 수 있습니다.\n" +
                "\n" +
                "제4조 (알람 비활성화)\n" +
                "사용자는 언제든지 설정에서 알람을 비활성화할 수 있습니다")

        btnBack.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            val cb1 = findViewById<CheckBox>(R.id.cbTerm1)
            val cb2 = findViewById<CheckBox>(R.id.cbTerm2)
            val cb3 = findViewById<CheckBox>(R.id.cbTerm3)
            val cb4 = findViewById<CheckBox>(R.id.cbTerm4)

            if (cb1.isChecked && cb2.isChecked && cb3.isChecked && cb4.isChecked) {
                tvError.visibility = View.GONE
                val intent = Intent(this, UserInfoActivity::class.java)
                startActivity(intent)
            } else {
                tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun setupTerm(titleViewId: Int, checkBoxId: Int, title: String, content: String) {
        val titleView = findViewById<TextView>(titleViewId)
        val checkBox = findViewById<CheckBox>(checkBoxId)

        titleView.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton("확인") { _, _ ->
                    checkBox.isChecked = true
                    checkBox.isEnabled = true
                    // 약관 확인 시 문구를 "동의하십니까?"로 변경
                    checkBox.text = "동의하십니까?"
                    // 체크 시 글씨색과 체크박스 색상을 진하게 변경
                    checkBox.setTextColor(Color.parseColor("#000000"))
                    checkBox.buttonTintList = ColorStateList.valueOf(Color.BLACK)
                }
                .show()
        }
    }
}
