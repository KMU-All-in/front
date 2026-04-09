package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class BudgetSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_setup)

        initViews()
        setupNavigation()
    }

    private fun initViews() {
        val btnOpenAddPlan = findViewById<Button>(R.id.btnOpenAddPlan)
        val cardAddPlan = findViewById<CardView>(R.id.cardAddPlan)
        val dimView = findViewById<View>(R.id.dimView)
        val btnCloseCard = findViewById<ImageView>(R.id.btnCloseCard)
        val btnSaveBudget = findViewById<Button>(R.id.btnSaveBudget)
        val etTotalBudget = findViewById<EditText>(R.id.etTotalBudget)
        val spCategory = findViewById<Spinner>(R.id.spCategory)

        val categories = arrayOf("패션/의류", "뷰티/화장품", "전자기기", "도서/문구", "식품/음료", "생활용품", "스포츠/레저", "기타")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spCategory.adapter = adapter

        btnOpenAddPlan.setOnClickListener {
            dimView.visibility = View.VISIBLE
            cardAddPlan.visibility = View.VISIBLE
        }

        btnCloseCard.setOnClickListener {
            dimView.visibility = View.GONE
            cardAddPlan.visibility = View.GONE
        }

        btnSaveBudget.setOnClickListener {
            val budgetStr = etTotalBudget.text.toString()
            if (budgetStr.isNotEmpty()) {
                val sharedPref = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putInt("TOTAL_BUDGET", budgetStr.toInt())
                    apply()
                }
                Toast.makeText(this, "이번 주 계획이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "총 예산을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            startActivity(Intent(this, FakeCartActivity::class.java))
            finish()
        }
        // 예산(현재 화면) 클릭 시 동작 없음
    }
}
